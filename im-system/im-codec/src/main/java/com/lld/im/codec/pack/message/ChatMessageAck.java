package com.lld.im.codec.pack.message;

import lombok.Data;

@Data
public class ChatMessageAck {
    private String messageId;
    private Long messageSequence;
    public ChatMessageAck(String messageId) {
        this.messageId = messageId;
    }

    public ChatMessageAck(String messageId,Long messageSequence) {
        this.messageId = messageId;
        this.messageSequence = messageSequence;
    }
}

