package com.lld.im.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "im.ai")
public class AiModuleProperties {

    private String p2pQueue = "im.ai.store-p2p.queue";

    private String groupQueue = "im.ai.store-group.queue";

    private int defaultTopK = 5;

    private int maxSummaryMessages = 200;

    private int searchCandidateLimit = 1000;
}
