package com.lld.im.ai.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SemanticSearchHit {

    private Long messageKey;

    private String conversationId;

    private String conversationKind;

    private String messageKind;

    private String snippet;

    private String imageUrl;

    private String fromId;

    private Long messageTime;

    private double score;
}
