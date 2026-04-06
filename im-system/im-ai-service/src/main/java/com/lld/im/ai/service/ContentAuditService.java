package com.lld.im.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lld.im.ai.model.AuditResult;
import com.lld.im.ai.model.NormalizedMessage;
import com.lld.im.ai.repository.AuditResultRepository;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContentAuditService {

    private static final String AUDIT_PROMPT = """
            你是 IM 内容安全分类器。
            请对输入内容做风险分类，标签可选：色情、暴力、辱骂、诈骗、违规引流、正常。
            只输出 JSON：{"riskLevel":"safe|low|medium|high","riskScore":0.0,"action":"allow|review|block","reason":"...","tags":["..."]}。
            """;

    private final RuleEngineService ruleEngineService;

    private final ChatModel chatModel;

    private final ObjectMapper objectMapper;

    private final AuditResultRepository auditResultRepository;

    public ContentAuditService(RuleEngineService ruleEngineService, ChatModel chatModel, ObjectMapper objectMapper,
                               AuditResultRepository auditResultRepository) {
        this.ruleEngineService = ruleEngineService;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.auditResultRepository = auditResultRepository;
    }

    public AuditResult auditMessage(NormalizedMessage message) {
        String content = buildAuditContent(message);
        AuditResult result = auditText(content);
        result.setMessageKey(message.getMessageKey());
        result.setAppId(message.getAppId());
        auditResultRepository.save(result);
        return result;
    }

    public AuditResult auditText(String content) {
        RuleEngineService.RuleMatch ruleMatch = ruleEngineService.match(content);
        if (ruleMatch.matched()) {
            AuditResult result = new AuditResult();
            result.setRiskLevel(ruleMatch.riskLevel());
            result.setRiskScore(ruleMatch.riskScore());
            result.setAction(ruleMatch.action());
            result.setReason(ruleMatch.reason());
            result.setTags(new ArrayList<>(ruleMatch.tags()));
            return result;
        }

        if (!StringUtils.hasText(content)) {
            AuditResult result = new AuditResult();
            result.setRiskLevel("safe");
            result.setRiskScore(0D);
            result.setAction("allow");
            result.setReason("空内容");
            result.setTags(List.of("正常"));
            return result;
        }

        try {
            String response = chatModel.call(
                    new SystemMessage(AUDIT_PROMPT),
                    new UserMessage("请审核以下消息内容：\n" + content)
            );
            JsonNode root = objectMapper.readTree(response);
            AuditResult result = new AuditResult();
            result.setRiskLevel(root.path("riskLevel").asText("safe"));
            result.setRiskScore(root.path("riskScore").asDouble(0D));
            result.setAction(root.path("action").asText("allow"));
            result.setReason(root.path("reason").asText("AI 分类完成"));
            List<String> tags = new ArrayList<>();
            JsonNode tagsNode = root.path("tags");
            if (tagsNode.isArray()) {
                tagsNode.forEach(tag -> tags.add(tag.asText()));
            }
            if (tags.isEmpty()) {
                tags.add("正常");
            }
            result.setTags(tags);
            return result;
        }
        catch (Exception ignored) {
            AuditResult result = new AuditResult();
            result.setRiskLevel("safe");
            result.setRiskScore(0D);
            result.setAction("allow");
            result.setReason("AI 分类降级为安全结果");
            result.setTags(List.of("正常"));
            return result;
        }
    }

    private String buildAuditContent(NormalizedMessage message) {
        if (StringUtils.hasText(message.getTextContent())) {
            return message.getTextContent();
        }
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(message.getCaption())) {
            builder.append(message.getCaption()).append('\n');
        }
        if (StringUtils.hasText(message.getOcrText())) {
            builder.append(message.getOcrText());
        }
        return builder.toString().trim();
    }
}
