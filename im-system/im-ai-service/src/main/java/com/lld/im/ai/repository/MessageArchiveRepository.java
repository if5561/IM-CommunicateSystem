package com.lld.im.ai.repository;

import com.lld.im.ai.model.ConversationKind;
import com.lld.im.ai.model.MessageKind;
import com.lld.im.ai.model.NormalizedMessage;
import com.lld.im.ai.model.StoredMessageIndex;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class MessageArchiveRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO im_ai_message_index
            (message_key, app_id, conversation_id, conversation_kind, message_kind, from_id, to_id, group_id,
             message_time, image_url, file_name, raw_message_body, text_content, ocr_text, caption, searchable_text,
             embedding, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), CURRENT_TIMESTAMP)
            ON CONFLICT (message_key) DO UPDATE SET
                app_id = EXCLUDED.app_id,
                conversation_id = EXCLUDED.conversation_id,
                conversation_kind = EXCLUDED.conversation_kind,
                message_kind = EXCLUDED.message_kind,
                from_id = EXCLUDED.from_id,
                to_id = EXCLUDED.to_id,
                group_id = EXCLUDED.group_id,
                message_time = EXCLUDED.message_time,
                image_url = EXCLUDED.image_url,
                file_name = EXCLUDED.file_name,
                raw_message_body = EXCLUDED.raw_message_body,
                text_content = EXCLUDED.text_content,
                ocr_text = EXCLUDED.ocr_text,
                caption = EXCLUDED.caption,
                searchable_text = EXCLUDED.searchable_text,
                embedding = EXCLUDED.embedding,
                updated_at = CURRENT_TIMESTAMP
            """;

    private final JdbcTemplate jdbcTemplate;

    public MessageArchiveRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(NormalizedMessage message, float[] embedding) {
        jdbcTemplate.update(UPSERT_SQL,
                message.getMessageKey(),
                message.getAppId(),
                message.getConversationId(),
                message.getConversationKind().name(),
                message.getMessageKind().name(),
                message.getFromId(),
                message.getToId(),
                message.getGroupId(),
                message.getMessageTime(),
                message.getImageUrl(),
                message.getFileName(),
                message.getRawMessageBody(),
                message.getTextContent(),
                message.getOcrText(),
                message.getCaption(),
                message.getSearchableText(),
                toPgVectorLiteral(embedding));
    }

    public Optional<StoredMessageIndex> findByMessageKey(Long messageKey) {
        List<StoredMessageIndex> results = jdbcTemplate.query(
                "SELECT *, 0.0 AS score FROM im_ai_message_index WHERE message_key = ?",
                storedMessageIndexRowMapper(),
                messageKey
        );
        return results.stream().findFirst();
    }

    public List<NormalizedMessage> findConversationMessages(Integer appId, String conversationId, Long fromTime,
                                                            Long toTime, boolean includeImages) {
        StringBuilder sql = new StringBuilder("SELECT * FROM im_ai_message_index WHERE app_id = ? AND conversation_id = ?");
        List<Object> args = new java.util.ArrayList<>();
        args.add(appId);
        args.add(conversationId);
        if (fromTime != null) {
            sql.append(" AND message_time >= ?");
            args.add(fromTime);
        }
        if (toTime != null) {
            sql.append(" AND message_time <= ?");
            args.add(toTime);
        }
        if (!includeImages) {
            sql.append(" AND message_kind = ?");
            args.add(MessageKind.TEXT.name());
        }
        sql.append(" ORDER BY message_time ASC");

        return jdbcTemplate.query(sql.toString(), normalizedMessageRowMapper(), args.toArray());
    }

    public List<StoredMessageIndex> searchByVector(Integer appId, String conversationId, float[] queryVector, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT *,
                       1 - (embedding <=> CAST(? AS vector)) AS score
                FROM im_ai_message_index
                WHERE app_id = ?
                  AND embedding IS NOT NULL
                """);
        List<Object> args = new java.util.ArrayList<>();
        args.add(toPgVectorLiteral(queryVector));
        args.add(appId);
        if (StringUtils.hasText(conversationId)) {
            sql.append(" AND conversation_id = ?");
            args.add(conversationId);
        }
        sql.append(" ORDER BY embedding <=> CAST(? AS vector) ASC LIMIT ?");
        args.add(toPgVectorLiteral(queryVector));
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), storedMessageIndexRowMapper(), args.toArray());
    }

    private RowMapper<NormalizedMessage> normalizedMessageRowMapper() {
        return (rs, rowNum) -> mapMessage(rs);
    }

    private RowMapper<StoredMessageIndex> storedMessageIndexRowMapper() {
        return (rs, rowNum) -> {
            StoredMessageIndex stored = new StoredMessageIndex();
            stored.setMessage(mapMessage(rs));
            stored.setScore(rs.getDouble("score"));
            return stored;
        };
    }

    private NormalizedMessage mapMessage(ResultSet rs) throws SQLException {
        NormalizedMessage message = new NormalizedMessage();
        message.setMessageKey(rs.getLong("message_key"));
        message.setAppId(rs.getInt("app_id"));
        message.setConversationId(rs.getString("conversation_id"));
        message.setConversationKind(ConversationKind.valueOf(rs.getString("conversation_kind")));
        message.setMessageKind(MessageKind.valueOf(rs.getString("message_kind")));
        message.setFromId(rs.getString("from_id"));
        message.setToId(rs.getString("to_id"));
        message.setGroupId(rs.getString("group_id"));
        message.setMessageTime(rs.getLong("message_time"));
        message.setImageUrl(rs.getString("image_url"));
        message.setFileName(rs.getString("file_name"));
        message.setRawMessageBody(rs.getString("raw_message_body"));
        message.setTextContent(rs.getString("text_content"));
        message.setOcrText(rs.getString("ocr_text"));
        message.setCaption(rs.getString("caption"));
        message.setSearchableText(rs.getString("searchable_text"));
        return message;
    }

    private String toPgVectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        builder.append(']');
        return builder.toString();
    }
}
