package com.lld.im.ai.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConversationSummaryRequest {

    @NotNull
    private Integer appId;

    @NotBlank
    private String conversationId;

    private Long fromTime;

    private Long toTime;

    private Integer maxMessages;
}
