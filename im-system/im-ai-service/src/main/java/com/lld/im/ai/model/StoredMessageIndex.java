package com.lld.im.ai.model;

import lombok.Data;

@Data
public class StoredMessageIndex {

    private NormalizedMessage message;

    private double score;
}
