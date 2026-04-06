package com.lld.im.ai.model;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class AuditResult {

    private Long messageKey;

    private Integer appId;

    private String riskLevel;

    private double riskScore;

    private String action;

    private String reason;

    private List<String> tags = new ArrayList<>();

    private Instant auditedAt = Instant.now();
}
