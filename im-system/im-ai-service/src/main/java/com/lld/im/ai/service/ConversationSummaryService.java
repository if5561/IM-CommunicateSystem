package com.lld.im.ai.service;

import com.lld.im.ai.config.AiModuleProperties;
import com.lld.im.ai.model.ConversationSummaryRequest;
import com.lld.im.ai.model.ConversationSummaryResponse;
import com.lld.im.ai.model.NormalizedMessage;
import com.lld.im.ai.repository.MessageArchiveRepository;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ConversationSummaryService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final MessageArchiveRepository messageArchiveRepository;

    private final ChatModel chatModel;

    private final AiModuleProperties aiModuleProperties;

    public ConversationSummaryService(MessageArchiveRepository messageArchiveRepository, ChatModel chatModel,
                                      AiModuleProperties aiModuleProperties) {
        this.messageArchiveRepository = messageArchiveRepository;
        this.chatModel = chatModel;
        this.aiModuleProperties = aiModuleProperties;
    }

    public ConversationSummaryResponse summarize(ConversationSummaryRequest request) {
        int maxMessages = request.getMaxMessages() == null ? aiModuleProperties.getMaxSummaryMessages()
                : Math.min(request.getMaxMessages(), aiModuleProperties.getMaxSummaryMessages());
        List<NormalizedMessage> messages = messageArchiveRepository.findConversationMessages(request.getAppId(),
                        request.getConversationId(), request.getFromTime(), request.getToTime(), false)
                .stream()
                .limit(maxMessages)
                .toList();

        if (messages.isEmpty()) {
            return ConversationSummaryResponse.builder()
                    .conversationId(request.getConversationId())
                    .messageCount(0)
                    .summary("当前时间范围内没有可总结的文本消息。")
                    .build();
        }

        String transcript = messages.stream()
                .map(message -> "[" + TIME_FORMATTER.format(Instant.ofEpochMilli(message.getMessageTime()))
                        + "][" + message.getFromId() + "] " + message.getTextContent())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

        String summary = chatModel.call(
                new SystemMessage("你是 IM 会话总结助手，请用中文提炼要点、待办和风险，保持简洁。"),
                new UserMessage("请总结以下会话内容：\n" + transcript)
        );

        return ConversationSummaryResponse.builder()
                .conversationId(request.getConversationId())
                .messageCount(messages.size())
                .summary(summary)
                .build();
    }
}
