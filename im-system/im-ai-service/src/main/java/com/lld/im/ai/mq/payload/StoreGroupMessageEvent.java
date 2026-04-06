package com.lld.im.ai.mq.payload;

import lombok.Data;

@Data
public class StoreGroupMessageEvent {

    private GroupChatMessageContentPayload groupChatMessageContent;
}
