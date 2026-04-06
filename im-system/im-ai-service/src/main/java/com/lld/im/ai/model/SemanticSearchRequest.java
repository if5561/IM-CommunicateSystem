package com.lld.im.ai.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SemanticSearchRequest {

    @NotNull
    private Integer appId;

    private String conversationId;

    @NotBlank
    private String query;

    @Min(1)
    private Integer topK;
}
