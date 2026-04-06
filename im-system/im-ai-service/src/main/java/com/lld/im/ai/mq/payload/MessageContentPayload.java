package com.lld.im.ai.mq.payload;

import lombok.Data;

@Data
public class MessageContentPayload {

    private Integer appId;

    private Integer clientType;

    private String imei;

    private String messageId;

    private String fromId;

    private String toId;

    private String messageBody;

    private Long messageTime;

    private String extra;

    private Long messageKey;

    private long messageSequence;
}
