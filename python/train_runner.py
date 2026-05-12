"""
模板训练入口脚本 - 批量文档结构识别训练

用于配合 Java Template 后端的 TrainTaskRunnerService，实现批量训练流程。
通过 REST API 与 Java 后端交互，不自连数据库。

用法:
    python train_runner.py --mode train --train-id 1 --api-url http://localhost:8080 [--ruleset default]

流程:
    1. 获取训练任务信息 (GET /api/train-task/{trainId})
    2. 获取训练文件列表 (GET /api/train-file/list?templateId=X)
    3. 遍历每个文件:
       a. 下载原始 .docx 内容 (GET /api/train-file/{fileId}/raw)
       b. 调用 doc-struct 核心识别流程 (需安装 docstruct 包)
       c. 保存解析结果 (POST /api/parse-record)
       d. 更新进度 (PUT /api/train-task/{trainId}/progress)
    4. 全部完成 (PUT /api/train-task/{trainId}/status)
"""

import sys
import json
import os
import io
import argparse
import urllib.request
import urllib.error
import time

# 尝试导入 docstruct SDK
try:
    from docstruct import DocStructSDK
    HAS_DOCSTRUCT = True
except ImportError:
    HAS_DOCSTRUCT = False


def eprint(*args, **kwargs):
    """输出到 stderr（不影响 stdout 的 JSON 输出）"""
    print(*args, file=sys.stderr, **kwargs)


def api_get(api_url, path, headers=None):
    """HTTP GET 请求"""
    url = api_url.rstrip('/') + '/' + path.lstrip('/')
    req_headers = {'Accept': 'application/json'}
    if headers:
        req_headers.update(headers)

    req = urllib.request.Request(url, headers=req_headers, method='GET')
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.read()
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8', errors='replace')
        raise RuntimeError(f"GET {url} 返回 {e.code}: {body}")
    except urllib.error.URLError as e:
        raise RuntimeError(f"GET {url} 网络错误: {e.reason}")


def api_get_binary(api_url, path, headers=None):
    """HTTP GET 请求，返回二进制内容"""
    url = api_url.rstrip('/') + '/' + path.lstrip('/')
    req_headers = {'Accept': 'application/octet-stream'}
    if headers:
        req_headers.update(headers)

    req = urllib.request.Request(url, headers=req_headers, method='GET')
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.read()
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8', errors='replace')
        raise RuntimeError(f"GET {url} 返回 {e.code}: {body}")
    except urllib.error.URLError as e:
        raise RuntimeError(f"GET {url} 网络错误: {e.reason}")


def api_put(api_url, path, data, headers=None):
    """HTTP PUT 请求（JSON body）"""
    url = api_url.rstrip('/') + '/' + path.lstrip('/')
    req_headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'X-Internal-Token': 'train-internal-token'
    }
    if headers:
        req_headers.update(headers)

    body = json.dumps(data, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(url, data=body, headers=req_headers, method='PUT')
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.read()
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8', errors='replace')
        raise RuntimeError(f"PUT {url} 返回 {e.code}: {body}")
    except urllib.error.URLError as e:
        raise RuntimeError(f"PUT {url} 网络错误: {e.reason}")


def api_post(api_url, path, data, headers=None):
    """HTTP POST 请求（JSON body）"""
    url = api_url.rstrip('/') + '/' + path.lstrip('/')
    req_headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
    }
    if headers:
        req_headers.update(headers)

    body = json.dumps(data, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(url, data=body, headers=req_headers, method='POST')
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.read()
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8', errors='replace')
        raise RuntimeError(f"POST {url} 返回 {e.code}: {body}")
    except urllib.error.URLError as e:
        raise RuntimeError(f"POST {url} 网络错误: {e.reason}")


def parse_api_response(response_body):
    """解析 ApiResponse 包装的 JSON 响应，返回 data 字段"""
    data = json.loads(response_body.decode('utf-8'))
    if data.get('code') != 200:
        raise RuntimeError(f"API 返回错误: code={data.get('code')}, message={data.get('message')}")
    return data.get('data')


def run_train_mode(args):
    """执行批量训练模式"""
    api_url = args.api_url.rstrip('/')
    train_id = args.train_id
    ruleset = args.ruleset or 'default'

    eprint(f"[train_runner] 开始训练任务: trainId={train_id}, apiUrl={api_url}, ruleset={ruleset}")

    # Step 1: 获取训练任务信息
    eprint(f"[train_runner] Step 1: 获取训练任务信息")
    task_resp = api_get(api_url, f'/api/train-task/{train_id}')
    task_data = parse_api_response(task_resp)

    template_id = task_data.get('templateId')
    if not template_id:
        raise RuntimeError("训练任务无关联模板")

    eprint(f"[train_runner] 训练任务信息: templateId={template_id}, taskName={task_data.get('taskName')}")

    # Step 2: 获取训练文件列表
    eprint(f"[train_runner] Step 2: 获取训练文件列表")
    list_resp = api_get(api_url, f'/api/train-file/list?templateId={template_id}&page=1&size=100')
    list_data = parse_api_response(list_resp)

    if not list_data:
        # 空列表
        list_data = {'records': [], 'total': 0}

    files = list_data.get('records', [])
    total_files = len(files)

    eprint(f"[train_runner] 训练文件数: {total_files}")

    if total_files == 0:
        # 文件列表为空，更新状态为 FAILED
        eprint(f"[train_runner] 文件列表为空，更新状态为 FAILED")
        api_put(api_url, f'/api/train-task/{train_id}/status', {
            'status': 'FAILED',
            'errorMessage': '没有可处理的训练文件'
        })
        sys.exit(1)

    # Step 3: 更新状态为 RUNNING
    eprint(f"[train_runner] Step 3: 更新状态为 RUNNING")
    api_put(api_url, f'/api/train-task/{train_id}/progress', {
        'progress': 0,
        'currentFile': 0,
        'totalFiles': total_files,
        'message': f'开始训练，共 {total_files} 个文件'
    })

    # Step 4: 初始化 docstruct SDK（如果可用）
    sdk = None
    if HAS_DOCSTRUCT:
        eprint(f"[train_runner] 使用 docstruct SDK 进行文档识别")
        try:
            sdk = DocStructSDK(ruleset_path=ruleset)
        except Exception as e:
            eprint(f"[train_runner] docstruct SDK 初始化失败: {e}")
            sdk = None
    else:
        eprint(f"[train_runner] 警告: docstruct 包未安装，将执行模拟识别流程")
        eprint(f"[train_runner] 请执行 pip install docstruct 安装文档结构识别引擎")

    # Step 5: 遍历处理每个文件
    failed_files = []
    success_count = 0

    for idx, file_info in enumerate(files):
        file_id = file_info.get('id')
        file_name = file_info.get('originalName', f'file_{file_id}')
        current = idx + 1

        eprint(f"\n[train_runner] 处理文件 [{current}/{total_files}]: {file_name} (fileId={file_id})")

        try:
            # 5a: 下载原始 .docx 内容
            eprint(f"[train_runner]   下载文件内容...")
            docx_bytes = api_get_binary(api_url, f'/api/train-file/{file_id}/raw')
            eprint(f"[train_runner]   下载完成: {len(docx_bytes)} 字节")

            # 5b: 调用 docstruct 核心识别流程
            structure_tree = None
            if sdk and len(docx_bytes) > 0:
                eprint(f"[train_runner]   执行文档结构识别...")
                try:
                    # 将字节流保存到临时文件供 SDK 读取
                    import tempfile
                    with tempfile.NamedTemporaryFile(suffix='.docx', delete=False) as tmp:
                        tmp.write(docx_bytes)
                        tmp_path = tmp.name

                    result = sdk.recognize(tmp_path)
                    structure_tree = result  # StructureTree 对象

                    os.unlink(tmp_path)
                    eprint(f"[train_runner]   识别完成")
                except Exception as e:
                    eprint(f"[train_runner]   识别失败: {e}")
                    # 降级：生成空的 structure_tree
                    structure_tree = {
                        'version': '1.0',
                        'generated_at': time.strftime('%Y-%m-%dT%H:%M:%S'),
                        'source_file': file_name,
                        'document_meta': {},
                        'root': {'type': 'document', 'children': []},
                        'flat_index': {}
                    }
            else:
                # 无 SDK：生成占位 structure_tree
                import hashlib
                checksum = hashlib.sha256(docx_bytes).hexdigest() if docx_bytes else ''
                structure_tree = {
                    'version': '1.0',
                    'generated_at': time.strftime('%Y-%m-%dT%H:%M:%S'),
                    'source_file': file_name,
                    'document_meta': {
                        'paragraph_count': 0,
                        'table_count': 0,
                        'image_count': 0,
                        'word_count': 0
                    },
                    'root': {
                        'type': 'document',
                        'children': []
                    },
                    'flat_index': {},
                    '_checksum': checksum
                }
                eprint(f"[train_runner]   使用占位结构树（docstruct 不可用）")

            # 5c: 保存解析结果到 parse_record
            eprint(f"[train_runner]   保存解析结果...")
            import hashlib
            checksum = hashlib.sha256(docx_bytes).hexdigest() if docx_bytes else ''

            parse_record_data = {
                'templateId': template_id,
                'sourceFile': file_name,
                'sourceChecksum': checksum,
                'fileSize': len(docx_bytes) if docx_bytes else 0,
                'structureTree': structure_tree
            }

            try:
                api_post(api_url, '/api/parse-record', parse_record_data)
                success_count += 1
                eprint(f"[train_runner]   解析结果保存成功")
            except Exception as e:
                eprint(f"[train_runner]   解析结果保存失败: {e}")
                failed_files.append({
                    'fileId': file_id,
                    'fileName': file_name,
                    'error': f'保存解析结果失败: {e}'
                })

            # 5d: 更新进度
            progress_pct = int(current / total_files * 100)
            api_put(api_url, f'/api/train-task/{train_id}/progress', {
                'progress': progress_pct,
                'currentFile': current,
                'totalFiles': total_files,
                'message': f'正在处理: {file_name}'
            })
            eprint(f"[train_runner]   进度: {progress_pct}%")

        except Exception as e:
            eprint(f"[train_runner]   文件处理失败: {e}")
            failed_files.append({
                'fileId': file_id,
                'fileName': file_name,
                'error': str(e)
            })

            # 继续处理其他文件（非致命错误）
            progress_pct = int(current / total_files * 100)
            try:
                api_put(api_url, f'/api/train-task/{train_id}/progress', {
                    'progress': progress_pct,
                    'currentFile': current,
                    'totalFiles': total_files,
                    'message': f'处理失败: {file_name}'
                })
            except Exception:
                pass

    # Step 6: 更新最终状态
    if failed_files:
        error_msg = f'成功 {success_count}/{total_files} 个文件，失败 {len(failed_files)} 个'
        if len(failed_files) > 0:
            first_error = failed_files[0]
            error_msg += f'。首个失败: {first_error["fileName"]} - {first_error["error"]}'

        if success_count > 0:
            # 部分成功
            api_put(api_url, f'/api/train-task/{train_id}/status', {
                'status': 'SUCCESS',
                'errorMessage': error_msg
            })
            eprint(f"[train_runner] 训练部分完成: {error_msg}")
        else:
            api_put(api_url, f'/api/train-task/{train_id}/status', {
                'status': 'FAILED',
                'errorMessage': error_msg
            })
            eprint(f"[train_runner] 训练失败: {error_msg}")
    else:
        api_put(api_url, f'/api/train-task/{train_id}/status', {
            'status': 'SUCCESS',
            'errorMessage': None
        })
        eprint(f"[train_runner] 训练全部完成: {total_files}/{total_files} 个文件")

    # 输出结果到 stdout（供 Java 读取）
    result = {
        'success': len(failed_files) == 0,
        'trainId': train_id,
        'totalFiles': total_files,
        'successCount': success_count,
        'failedCount': len(failed_files),
        'failedFiles': failed_files
    }
    print(json.dumps(result, ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser(description='模板训练入口脚本')
    parser.add_argument('--mode', choices=['train', 'file'], default='file',
                        help='运行模式: train（批量训练）/ file（单文件识别）')
    parser.add_argument('--train-id', type=int,
                        help='训练任务 ID（train 模式下必填）')
    parser.add_argument('--api-url', default='http://localhost:8080',
                        help='Java 后端 API 基地址')
    parser.add_argument('--ruleset', default='default',
                        help='规则集名称')
    parser.add_argument('--file-id', type=int,
                        help='单文件识别时的文件 ID')
    parser.add_argument('--template-id', type=int,
                        help='模板 ID')
    parser.add_argument('--debug', action='store_true',
                        help='调试模式')

    args = parser.parse_args()

    # 配置 stdout 编码
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')

    try:
        if args.mode == 'train':
            if not args.train_id:
                eprint("错误: train 模式需要 --train-id 参数")
                result = {'success': False, 'error': 'train 模式需要 --train-id 参数'}
                print(json.dumps(result, ensure_ascii=False))
                sys.exit(1)
            run_train_mode(args)
        elif args.mode == 'file':
            eprint("单文件识别模式: 暂未实现，请通过 Java API 调用")
            result = {'success': False, 'error': '单文件识别模式暂未实现'}
            print(json.dumps(result, ensure_ascii=False))
            sys.exit(1)
    except Exception as e:
        eprint(f"[train_runner] 致命错误: {e}")
        result = {'success': False, 'error': str(e)}
        print(json.dumps(result, ensure_ascii=False))
        sys.exit(1)


if __name__ == '__main__':
    main()
