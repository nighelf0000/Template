# Template 系统部署手册

## 1. 系统概述

| 项目 | 说明 |
|------|------|
| **系统名称** | Template System — 文档解析与样式预览系统 |
| **后端技术** | Spring Boot 3.2.5, MyBatis-Plus 3.5.6, Apache POI 5.3.0, PDFBox 3.0.1 |
| **前端技术** | Vue 3 + Vite 8 + Element Plus 2 + TypeScript 6 + pdfjs-dist 4.9 |
| **数据库** | MySQL 8.0+ |
| **JDK 版本** | 17+ |
| **Node 版本** | 16+ |
| **默认端口** | 后端 8080，前端开发服务器 3000 |
| **打包产物** | `target/template-system-1.0.0.jar` |

---

## 2. 环境准备

### 2.1 安装 JDK 17+

```bash
# 验证安装
java -version
# 预期: openjdk version "17.x.x" ...
```

下载地址：[Adoptium JDK 17](https://adoptium.net/download/) 或 [Oracle JDK 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)

### 2.2 安装 Node.js 16+

```bash
# 验证安装
node --version
npm --version
```

下载地址：[Node.js 官网](https://nodejs.org/)

### 2.3 安装 MySQL 8.0+

```bash
# 验证安装
mysql --version
```

确保 MySQL 服务已启动，root 用户具有创建数据库权限。

### 2.4 中文字体（PDF 生成必需）

PDF 生成使用 **黑体 (SimHei)** 字体，默认路径 `C:\Windows\Fonts\simhei.ttf`。

Linux 部署时需安装并配置字体路径：

```bash
# CentOS / RHEL
yum install -y fontconfig
mkdir -p /usr/share/fonts/chinese
# 将 simhei.ttf 复制到 /usr/share/fonts/chinese/
fc-cache -fv

# Ubuntu / Debian
apt-get install -y fontconfig
mkdir -p /usr/share/fonts/truetype/chinese
# 将 simhei.ttf 复制到上述目录
fc-cache -fv
```

字体路径配置见 `application.yml` 中的 `template.pdf.font.path`。

---

## 3. 配置文件说明

配置文件位于 `src/main/resources/application.yml`，关键配置如下：

### 3.1 数据库连接

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/template_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: root
```

**生产环境建议：**
- 修改 `username` / `password` 为非 root 账号
- 设置 `useSSL=true` 并配置 SSL 证书
- 调优连接池参数（见 3.3）

### 3.2 文件上传限制

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
```

### 3.3 连接池配置

```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 30000
      idle-timeout: 600000
      maximum-pool-size: 10     # 生产环境可上调至 20-30
      minimum-idle: 5           # 生产环境可上调至 10
      max-lifetime: 1800000
```

### 3.4 PDF 转换器配置

```yaml
template:
  pdf:
    converter: poi              # poi | libreoffice
    font:
      path: 'C:\Windows\Fonts\simhei.ttf'
    libreoffice:
      path: soffice             # LibreOffice 可执行文件路径
      timeout-seconds: 30
    cache:
      enabled: true             # 启用缓存，避免重复生成
      ttl-minutes: 5            # 缓存有效期
```

- **poi 模式**：纯 Java 实现，无需额外依赖，适合大部分场景
- **libreoffice 模式**：需要安装 LibreOffice，对复杂文档（公式、图片）保真度更高

---

## 4. 数据库初始化

### 4.1 创建数据库并建表

数据库初始化脚本位于 `src/main/resources/init.sql`。执行方式：

```bash
mysql -u root -p < src/main/resources/init.sql
```

或在 MySQL 客户端中：

```sql
source src/main/resources/init.sql;
```

该脚本会自动创建 `template_db` 数据库及以下 4 张表：

| 表名 | 说明 |
|------|------|
| `template_config` | 模板信息配置表 |
| `template_rule` | 模板规则样式配置表 |
| `template_engine_config` | 内容识别引擎配置表 |
| `user_upload_file` | 用户上传文件表 |

### 4.2 验证数据库

```sql
USE template_db;
SHOW TABLES;
-- 预期输出: 4 张表
```

---

## 5. 构建与启动

### 5.1 一键启动（开发环境 - Windows）

#### 启动后端

运行项目根目录下的 `start.bat`：

```
start.bat
```

该脚本会自动：
1. 检查 Java 17+ 版本
2. 检查端口 8080 是否被占用
3. 执行 `mvnw clean package -DskipTests`
4. 启动 `target/template-system-1.0.0.jar`

#### 启动前端

运行项目根目录下的 `frontend-start.bat`：

```
frontend-start.bat
```

该脚本会自动：
1. 检查 Node.js 是否安装
2. 首次运行时执行 `npm install`
3. 启动 Vite 开发服务器（`http://localhost:3000`）

#### 停止服务

运行 `stop.bat`，根据提示选择终止 8080 和 3000 端口的进程。

### 5.2 手动构建与启动

#### 后端

```bash
# 构建（跳过测试）
mvnw.cmd clean package -DskipTests        # Windows
./mvnw clean package -DskipTests          # Linux/Mac

# 启动
java -jar target/template-system-1.0.0.jar

# 指定配置文件启动
java -jar target/template-system-1.0.0.jar --spring.config.location=file:./application-prod.yml

# 后台运行（Linux）
nohup java -jar target/template-system-1.0.0.jar > logs/app.log 2>&1 &
```

#### 前端

```bash
cd frontend

# 首次运行：安装依赖
npm install

# 开发模式启动
npm run dev

# 生产构建
npm run build
# 构建产物在 frontend/dist/，部署到 Nginx/Apache 即可
```

### 5.3 自定义端口

```bash
# 后端自定义端口
java -jar target/template-system-1.0.0.jar --server.port=9090

# 前端自定义端口
# 修改 frontend/vite.config.ts 或创建 .env 文件：
# echo "VITE_PORT=3001" > frontend/.env
```

---

## 6. 生产环境部署

### 6.1 推荐架构

```
┌─────────────┐     ┌──────────────┐     ┌─────────┐
│   Nginx     │────▶│  Spring Boot │────▶│  MySQL  │
│  (前端静态)  │     │  (API:8080)  │     │  :3306  │
│   :80/443   │     └──────────────┘     └─────────┘
└─────────────┘
```

### 6.2 前端部署到 Nginx

```bash
# 1. 构建前端
cd frontend
npm install
npm run build

# 2. 将 dist 目录复制到 Nginx 静态目录
cp -r dist /var/www/template-frontend/

# 3. Nginx 配置示例
```

```nginx
server {
    listen       80;
    server_name  your-domain.com;

    # 前端静态文件
    location / {
        root   /var/www/template-frontend;
        index  index.html;
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

### 6.3 后端生产配置清单

1. **数据库密码**：修改为强密码，建议使用环境变量
   ```bash
   export DB_PASSWORD=your_strong_password
   java -jar template-system-1.0.0.jar --spring.datasource.password=${DB_PASSWORD}
   ```

2. **日志配置**：日志配置在 `src/main/resources/log4j2-spring.xml`，生产环境调整日志级别为 WARN

3. **JVM 参数**（建议）：
   ```bash
   java -Xms512m -Xmx2g -XX:+UseG1GC -jar target/template-system-1.0.0.jar
   ```

4. **文件存储**：确保日志目录 `logs/` 和临时目录 `temp/` 有写入权限

### 6.4 系统服务化（Linux - systemd）

创建服务文件 `/etc/systemd/system/template.service`：

```ini
[Unit]
Description=Template System Backend
After=network.target mysql.service

[Service]
User=template
WorkingDirectory=/opt/template
ExecStart=/usr/bin/java -Xms512m -Xmx2g -jar /opt/template/template-system-1.0.0.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable template
systemctl start template
systemctl status template
```

---

## 7. 验证部署

### 7.1 后端健康检查

```bash
# 检查应用是否启动
curl http://localhost:8080/actuator/health

# 查看文件列表接口（需先配置模板）
curl http://localhost:8080/api/file/list?page=1&size=10
```

### 7.2 前端验证

浏览器访问：
- 开发模式：`http://localhost:3000`
- 生产模式：`http://your-domain.com`

### 7.3 功能验证步骤

1. 打开前端页面，确认页面正常加载
2. 创建模板 → 配置文件识别引擎规则
3. 上传 Word (.docx) 文件
4. 查看解析预览，确认 PDF 预览正常
5. 确认高亮底色显示正确
6. 执行导出功能，确认输出文件正确

---

## 8. 常见问题

### 8.1 PDF 预览中文乱码或空白

**原因**：中文字体（SimHei）未安装或路径不正确。

**解决**：
1. 确认字体文件存在：`ls C:\Windows\Fonts\simhei.ttf` (Windows) 或 `fc-list | grep SimHei` (Linux)
2. 修改 `application.yml` 中 `template.pdf.font.path` 为正确的字体路径
3. Linux 下可使用系统自带中文字体，如 `/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc`

### 8.2 端口被占用

**错误**：`Port 8080 is already in use`

**解决**：
```bash
# Windows — 查找并终止占用进程
netstat -ano | findstr :8080
taskkill /F /PID <PID>

# Linux
lsof -i :8080
kill -9 <PID>
```

或直接运行 `stop.bat` 自动终止。

### 8.3 数据库连接失败

**错误**：`Communications link failure` 或 `Access denied`

**解决**：
1. 确认 MySQL 服务已启动
2. 验证 `application.yml` 中的数据库连接信息
3. 确认 `template_db` 数据库已创建
4. 检查防火墙是否放通 3306 端口

### 8.4 Maven 构建失败

**常见原因**：
- 网络问题导致依赖下载失败 → 配置 Maven 国内镜像（如阿里云）
- JDK 版本不正确 → 确保使用 JDK 17+

### 8.5 npm install 失败

**解决**：
```bash
# 清理缓存重试
npm cache clean --force
npm install

# 或使用国内镜像
npm config set registry https://registry.npmmirror.com
npm install
```

---

## 9. 目录结构参考

```
Template/
├── start.bat                 # 后端一键启动脚本
├── frontend-start.bat        # 前端一键启动脚本
├── stop.bat                  # 服务停止脚本
├── mvnw / mvnw.cmd           # Maven Wrapper（无需安装 Maven）
├── pom.xml                   # Maven 项目配置
├── src/
│   ├── main/
│   │   ├── java/com/template/
│   │   └── resources/
│   │       ├── application.yml    # 主配置文件
│   │       ├── init.sql           # 数据库初始化脚本
│   │       └── log4j2-spring.xml  # 日志配置
│   └── test/
├── frontend/
│   ├── package.json
│   ├── src/
│   └── node_modules/
├── Design/                   # 设计文档
├── Doc/                      # 其他文档
├── logs/                     # 运行日志
└── temp/                     # 临时文件
```
