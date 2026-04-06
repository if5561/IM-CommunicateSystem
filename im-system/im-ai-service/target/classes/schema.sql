CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS im_ai_message_index (
message_key BIGINT PRIMARY KEY,
app_id INT NOT NULL,
conversation_id VARCHAR(128) NOT NULL,
conversation_kind VARCHAR(16) NOT NULL,
message_kind VARCHAR(16) NOT NULL,
from_id VARCHAR(64),
to_id VARCHAR(64),
group_id VARCHAR(64),
message_time BIGINT NOT NULL,
image_url VARCHAR(512),
file_name VARCHAR(255),
raw_message_body TEXT,
text_content TEXT,
ocr_text TEXT,
caption TEXT,
searchable_text TEXT,
embedding vector(1536),
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- 索引
CREATE INDEX IF NOT EXISTS idx_im_ai_message_index_conv
ON im_ai_message_index (app_id, conversation_id, message_time);
-- 给向量字段建高速检索索引:USING HNSW：使用 HNSW 算法（pgvector 里最快的向量检索算法）
CREATE INDEX IF NOT EXISTS idx_im_ai_message_index_embedding
    ON im_ai_message_index USING hnsw (embedding vector_cosine_ops);

-- (embedding vector_cosine_ops)：embedding = 你的向量字段 vector_cosine_ops = 用余弦相似度计算向量距离
