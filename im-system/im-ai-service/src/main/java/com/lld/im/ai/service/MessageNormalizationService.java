package com.lld.im.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lld.im.ai.model.ConversationKind;
import com.lld.im.ai.model.ImageMessageBody;
import com.lld.im.ai.model.MessageKind;
import com.lld.im.ai.model.NormalizedMessage;
import com.lld.im.ai.mq.payload.GroupChatMessageContentPayload;
import com.lld.im.ai.mq.payload.MessageContentPayload;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MessageNormalizationService {

    private final ObjectMapper objectMapper;

    public MessageNormalizationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NormalizedMessage normalizeP2P(MessageContentPayload payload) {
        NormalizedMessage message = new NormalizedMessage();
        fillCommonFields(message, payload.getAppId(), payload.getMessageKey(), payload.getFromId(), payload.getToId(),
                null, payload.getMessageTime(), payload.getMessageBody());
        message.setConversationKind(ConversationKind.P2P);
        message.setConversationId(buildP2PConversationId(payload.getFromId(), payload.getToId()));
        parseMessageBody(message, payload.getMessageBody());
        return message;
    }

    public NormalizedMessage normalizeGroup(GroupChatMessageContentPayload payload) {
        NormalizedMessage message = new NormalizedMessage();
        fillCommonFields(message, payload.getAppId(), payload.getMessageKey(), payload.getFromId(), payload.getToId(),
                payload.getGroupId(), payload.getMessageTime(), payload.getMessageBody());
        message.setConversationKind(ConversationKind.GROUP);
        message.setConversationId("GROUP:" + payload.getGroupId());
        parseMessageBody(message, payload.getMessageBody());
        return message;
    }

    public void applyImageInsight(NormalizedMessage message, String ocrText, String caption) {
        message.setOcrText(safeText(ocrText));
        message.setCaption(safeText(caption));
        message.setSearchableText(buildImageSearchableText(message));
    }

    private void fillCommonFields(NormalizedMessage message, Integer appId, Long messageKey, String fromId, String toId,
                                  String groupId, Long messageTime, String rawBody) {
        message.setAppId(appId);
        message.setMessageKey(messageKey);
        message.setFromId(fromId);
        message.setToId(toId);
        message.setGroupId(groupId);
        message.setMessageTime(messageTime == null ? System.currentTimeMillis() : messageTime);
        message.setRawMessageBody(rawBody);
    }

    private void parseMessageBody(NormalizedMessage normalizedMessage, String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            normalizedMessage.setMessageKind(MessageKind.TEXT);
            normalizedMessage.setTextContent("");
            normalizedMessage.setSearchableText("");
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode typeNode = root.get("type");
            if (typeNode != null && "image".equalsIgnoreCase(typeNode.asText())) {
                ImageMessageBody imageMessageBody = objectMapper.treeToValue(root, ImageMessageBody.class);
                normalizedMessage.setMessageKind(MessageKind.IMAGE);
                normalizedMessage.setImageUrl(imageMessageBody.getImageUrl());
                normalizedMessage.setStoragePath(imageMessageBody.getStoragePath());
                normalizedMessage.setFileName(imageMessageBody.getFileName());
                normalizedMessage.setOcrText(safeText(imageMessageBody.getOcrText()));
                normalizedMessage.setCaption(safeText(imageMessageBody.getCaption()));
                normalizedMessage.setSearchableText(buildImageSearchableText(normalizedMessage));
                return;
            }
        }
        catch (Exception ignored) {
        }

        normalizedMessage.setMessageKind(MessageKind.TEXT);
        normalizedMessage.setTextContent(rawBody);
        normalizedMessage.setSearchableText(rawBody);
    }

    private String buildImageSearchableText(NormalizedMessage message) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, message.getCaption());
        appendLine(builder, message.getOcrText());
        appendLine(builder, message.getFileName());
        return builder.toString().trim();
    }

    private void appendLine(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(value.trim());
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildP2PConversationId(String fromId, String toId) {
        if (fromId.compareTo(toId) < 0) {
            return "P2P:" + toId + "|" + fromId;
        }
        if (fromId.compareTo(toId) > 0) {
            return "P2P:" + fromId + "|" + toId;
        }
        throw new IllegalArgumentException("无效的 P2P 会话，两端用户相同");
    }
}
