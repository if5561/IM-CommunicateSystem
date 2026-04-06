# im-ai-service

独立的 AI 扩展模块，负责三类能力：

1. 聊天记录语义搜索 + 图片消息通过“图片描述 + 可见文字提取”-> 文本embedding 搜索
2. 文本会话总结
3. 规则引擎 + AI 分类的内容审核

## 运行前提

- JDK 17
- RabbitMQ
- PostgreSQL
- `pgvector` 扩展
- OpenAI 兼容接口 Key

## 本地安装 pgvector

### 方案一：Docker 快速启动

如果你本机有 Docker，这是最省事的方式：

```bash
docker run --name im-ai-pgvector ^
  -e POSTGRES_USER=postgres ^
  -e POSTGRES_PASSWORD=postgres ^
  -e POSTGRES_DB=im_ai ^
  -p 5432:5432 ^
  -d pgvector/pgvector:pg17
```

启动后可以直接连接：

- host: `localhost`
- port: `5432`
- database: `im_ai`
- username: `postgres`
- password: `postgres`

### 方案二：Windows 原生安装

1. 从 PostgreSQL Windows 官方下载页安装 PostgreSQL。
官方页面：[PostgreSQL Windows Installer](https://www.postgresql.org/download/windows/)

2. 安装 Visual Studio 2022 Build Tools，并确保包含 C++ 工具链。
官方说明里提到 Windows 上构建扩展需要 Visual Studio / MSVC：
[PostgreSQL Windows Build Docs](https://www.postgresql.org/docs/15/install-windows.html)

3. 按 pgvector 官方 README 在管理员权限的 `x64 Native Tools Command Prompt for VS 2022` 中编译安装：

```bat
set "PGROOT=C:\Program Files\PostgreSQL\17"
cd %TEMP%
git clone --branch v0.8.1 https://github.com/pgvector/pgvector.git
cd pgvector
nmake /F Makefile.win
nmake /F Makefile.win install
```

官方安装说明：
[pgvector README](https://github.com/pgvector/pgvector)

### 建库与启用扩展

进入 `psql` 后执行：

```sql
CREATE DATABASE im_ai;
\c im_ai;
CREATE EXTENSION IF NOT EXISTS vector;
```

如果你想单独建一个应用用户，也可以执行：

```sql
CREATE USER im_ai_user WITH PASSWORD 'im_ai_password';
GRANT ALL PRIVILEGES ON DATABASE im_ai TO im_ai_user;
\c im_ai;
GRANT ALL ON SCHEMA public TO im_ai_user;
CREATE EXTENSION IF NOT EXISTS vector;
```

### 配置项目连接

当前 `im-ai-service` 默认读取这些参数：

- `IM_AI_DB_URL`
- `IM_AI_DB_USERNAME`
- `IM_AI_DB_PASSWORD`

例如：

```bat
set IM_AI_DB_URL=jdbc:postgresql://localhost:5432/im_ai
set IM_AI_DB_USERNAME=postgres
set IM_AI_DB_PASSWORD=postgres
```

首次启动 `im-ai-service` 时会自动执行 `schema.sql`，创建 `im_ai_message_index` 表和 `pgvector` 索引。

## 默认模型

- `chat`: `gpt-4.1-mini`
- `embedding`: `text-embedding-3-small`

## 图片消息约定

`service` 模块上传图片后，会返回一个 JSON 字符串，可直接作为原有发消息接口中的 `messageBody`：

```json
{
  "type": "image",
  "imageUrl": "/media/images/20260405/example.png",
  "storagePath": "E:/.../uploads/images/20260405/example.png",
  "fileName": "example.png",
  "width": 1080,
  "height": 720,
  "size": 245678,
  "contentType": "image/png",
  "ocrText": "",
  "caption": ""
}
```

AI 模块会在消费消息时补齐图片 `ocrText` 和 `caption`，并将 `ocrText + caption + fileName` 用于文本向量化检索。

## 持久化索引

- 语义搜索索引已落到 PostgreSQL 表 `im_ai_message_index`
- 向量列使用 `pgvector` 的 `vector(1536)`
- 启动时会自动执行 [schema.sql](E:\JAVAnew\work\IMCommunicateSystem\im-system\im-ai-service\src\main\resources\schema.sql)
- 搜索时直接使用 `pgvector` 的 `<=>` 余弦距离算子做近邻检索
