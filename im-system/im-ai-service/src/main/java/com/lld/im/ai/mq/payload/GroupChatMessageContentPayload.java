package com.lld.im.ai.mq.payload;

import lombok.Data;

import java.util.List;

@Data
public class GroupChatMessageContentPayload {

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

    private String groupId;

    private List<String> memberId;
}
