# 智能匹配训练 - TF-IDF + 文本相似度
# 输入：--input 文件 JSON - 包含 template_id 和 training_data
# 输出：stdout JSON - 包含 success 和 rules 数组

import sys
import json
import os

# 尝试导入依赖，若缺失则输出友好错误
try:
    import numpy as np
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.metrics.pairwise import cosine_similarity
    import jieba
except ImportError as e:
    error_msg = {
        "success": False,
        "error": f"Python依赖缺失: {e}. 请执行: pip install -r python/requirements.txt"
    }
    print(json.dumps(error_msg, ensure_ascii=False))
    sys.exit(1)


def chinese_tokenizer(text):
    """中文分词器，使用 jieba 进行分词"""
    words = jieba.lcut(text)
    # 过滤掉单字和空格
    return [w for w in words if len(w.strip()) > 1]


class TfidfTrainer:
    """基于 TF-IDF 和余弦相似度的智能匹配训练器"""

    def __init__(self):
        self.vectorizer = TfidfVectorizer(tokenizer=chinese_tokenizer, max_features=1000)

    def train(self, training_data):
        """
        对训练数据执行 TF-IDF 训练并生成规则

        TF-IDF 在所有组的全部文本上拟合，再按组提取各组的平均特征向量和 Top 关键词。
        这样 IDF 跨组计算，关键词反映的是该类型区别于其他类型的区分性词汇。

        Args:
            training_data: list of dict, each with:
                - match_type: str
                - match_level: int or None
                - texts: list of str
                - style_rule_id: int or None

        Returns:
            list of rules
        """
        rules = []

        # Step 1: 收集所有组的全部文本及其组索引
        all_texts = []
        group_ranges = []  # [(start, end), ...] 每组文本在 all_texts 中的起止位置
        for group in training_data:
            texts = group.get("texts", [])
            if not texts or len(texts) < 1:
                group_ranges.append(None)
                continue
            start = len(all_texts)
            all_texts.extend(texts)
            end = len(all_texts)
            group_ranges.append((start, end))

        if not all_texts:
            return rules

        # Step 2: 在所有组的全部文本上拟合 TF-IDF（IDF 跨组计算）
        try:
            tfidf_matrix = self.vectorizer.fit_transform(all_texts)
        except Exception as e:
            return rules

        feature_names = self.vectorizer.get_feature_names_out()

        # Step 3: 按组提取平均 TF-IDF 向量和 Top 关键词
        for group_idx, group in enumerate(training_data):
            match_type = group.get("match_type", "UNKNOWN")
            match_level = group.get("match_level")
            texts = group.get("texts", [])
            style_rule_id = group.get("style_rule_id")

            range_info = group_ranges[group_idx]
            if range_info is None:
                continue
            start, end = range_info
            group_vectors = tfidf_matrix[start:end]

            # 组内平均 TF-IDF 向量（代表该类型的特征向量）
            avg_vector = group_vectors.mean(axis=0)
            avg_vector_flat = np.array(avg_vector).flatten()

            # 余弦相似度矩阵（组内文本间）
            similarity_matrix = cosine_similarity(group_vectors)

            # 按平均 TF-IDF 权重的降序提取关键词
            feature_indices = np.argsort(avg_vector_flat)[::-1]
            top_keywords = []
            for idx in feature_indices:
                if avg_vector_flat[idx] > 0:
                    top_keywords.append(str(feature_names[idx]))
                if len(top_keywords) >= 10:
                    break

            # 降级：词频统计
            if not top_keywords:
                word_freq = {}
                for text in texts:
                    words = chinese_tokenizer(text)
                    for w in words:
                        word_freq[w] = word_freq.get(w, 0) + 1
                sorted_words = sorted(word_freq.items(), key=lambda x: -x[1])
                top_keywords = [w for w, _ in sorted_words[:10]]

            # 计算相似度阈值
            if similarity_matrix.shape[0] > 1:
                non_diag = similarity_matrix[~np.eye(similarity_matrix.shape[0], dtype=bool)]
                if len(non_diag) > 0:
                    threshold = max(0.2, float(non_diag.min()) * 0.7)
                else:
                    threshold = 0.3
            else:
                threshold = 0.3

            rule = {
                "match_type": match_type,
                "match_level": match_level,
                "keywords": top_keywords,
                "feature_vector": avg_vector_flat.tolist(),
                "threshold": round(threshold, 4),
                "style_rule_id": style_rule_id
            }

            rules.append(rule)

        return rules


def main():
    """主入口：从 --input 文件读取 JSON，执行训练，输出 JSON 到 stdout"""
    sys.stdout.reconfigure(encoding='utf-8')
    try:
        # 解析 --input 参数获取训练数据文件路径
        input_path = None
        for i, arg in enumerate(sys.argv):
            if arg == "--input" and i + 1 < len(sys.argv):
                input_path = sys.argv[i + 1]
                break

        if input_path is None:
            output = {
                "success": False,
                "error": "缺少 --input 参数，请提供训练数据文件路径"
            }
            print(json.dumps(output, ensure_ascii=False))
            sys.exit(1)

        with open(input_path, 'r', encoding='utf-8') as f:
            raw_input = f.read()

        if not raw_input.strip():
            output = {
                "success": False,
                "error": "输入为空，请提供训练数据 JSON"
            }
            print(json.dumps(output, ensure_ascii=False))
            sys.exit(1)

        input_data = json.loads(raw_input)
        template_id = input_data.get("template_id")
        training_data = input_data.get("training_data", [])

        if not training_data:
            output = {
                "success": False,
                "error": "training_data 为空，请提供有效的训练数据"
            }
            print(json.dumps(output, ensure_ascii=False))
            sys.exit(1)

        trainer = TfidfTrainer()
        rules = trainer.train(training_data)

        output = {
            "success": True,
            "template_id": template_id,
            "rules": rules
        }

        print(json.dumps(output, ensure_ascii=False))

    except json.JSONDecodeError as e:
        output = {
            "success": False,
            "error": f"JSON 解析失败: {e}"
        }
        print(json.dumps(output, ensure_ascii=False))
        sys.exit(1)
    except Exception as e:
        output = {
            "success": False,
            "error": f"训练过程异常: {e}"
        }
        print(json.dumps(output, ensure_ascii=False))
        sys.exit(1)


if __name__ == "__main__":
    main()
