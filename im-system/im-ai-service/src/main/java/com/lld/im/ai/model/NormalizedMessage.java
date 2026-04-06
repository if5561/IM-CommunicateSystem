package com.lld.im.ai.model;

import lombok.Data;

@Data
public class NormalizedMessage {

    private Long messageKey;

    private Integer appId;

    private ConversationKind conversationKind;

    private String conversationId;

    private String fromId;

    private String toId;

    private String groupId;

    private Long messageTime;

    private MessageKind messageKind;

    private String rawMessageBody;

    private String textContent;

    private String imageUrl;

    private String storagePath;

    private String fileName;

    private String ocrText;

    private String caption;

    private String searchableText;
}
