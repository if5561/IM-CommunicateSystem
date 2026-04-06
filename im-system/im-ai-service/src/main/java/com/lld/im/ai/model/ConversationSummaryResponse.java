package com.lld.im.ai.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationSummaryResponse {

    private String conversationId;

    private int messageCount;

    private String summary;
}
