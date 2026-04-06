package com.lld.im.ai.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class RuleEngineService {

    private static final Map<String, List<String>> HIGH_RISK_KEYWORDS = Map.of(
            "色情", List.of("约炮", "裸聊", "招嫖"),
            "暴力", List.of("砍死", "炸药", "爆炸"),
            "辱骂", List.of("傻逼", "废物", "滚蛋"),
            "诈骗", List.of("刷流水", "刷单返利", "投资内幕", "验证码发我")
    );

    private static final Pattern URL_PATTERN = Pattern.compile("https?://|www\\.");

    public RuleMatch match(String content) {
        if (!StringUtils.hasText(content)) {
            return RuleMatch.safe();
        }

        List<String> tags = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : HIGH_RISK_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (content.contains(keyword)) {
                    tags.add(entry.getKey());
                    return new RuleMatch(true, "high", 0.99D, tags, "block", "命中高危关键词：" + keyword);
                }
            }
        }

        if (URL_PATTERN.matcher(content).find() && (content.contains("转账") || content.contains("加微信"))) {
            tags.add("诈骗");
            return new RuleMatch(true, "medium", 0.72D, tags, "review", "命中链接+诱导交易规则");
        }

        return RuleMatch.safe();
    }

    public record RuleMatch(boolean matched, String riskLevel, double riskScore, List<String> tags, String action,
                            String reason) {

        public static RuleMatch safe() {
            return new RuleMatch(false, "safe", 0D, List.of(), "allow", "规则未命中");
        }
    }
}
