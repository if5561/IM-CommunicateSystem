package com.lld.im.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lld.im.ai.model.ImageInsightResult;
import com.lld.im.ai.model.ImageMessageBody;
import com.lld.im.ai.model.MessageKind;
import com.lld.im.ai.model.NormalizedMessage;
import com.lld.im.ai.mq.payload.StoreGroupMessageEvent;
import com.lld.im.ai.mq.payload.StoreP2PMessageEvent;
import org.springframework.stereotype.Service;

@Service
public class MessageIngestionService {

    private final ObjectMapper objectMapper;

    private final MessageNormalizationService messageNormalizationService;

    private final ImageInsightService imageInsightService;

    private final SemanticIndexService semanticIndexService;

    private final ContentAuditService contentAuditService;

    public MessageIngestionService(ObjectMapper objectMapper, MessageNormalizationService messageNormalizationService,
                                   ImageInsightService imageInsightService, SemanticIndexService semanticIndexService,
                                   ContentAuditService contentAuditService) {
        this.objectMapper = objectMapper;
        this.messageNormalizationService = messageNormalizationService;
        this.imageInsightService = imageInsightService;
        this.semanticIndexService = semanticIndexService;
        this.contentAuditService = contentAuditService;
    }

    public void ingestP2P(String payload) throws Exception {
        StoreP2PMessageEvent event = objectMapper.readValue(payload, StoreP2PMessageEvent.class);
        if (event.getMessageContent() == null) {
            return;
        }
        NormalizedMessage message = messageNormalizationService.normalizeP2P(event.getMessageContent());
        process(message);
    }

    public void ingestGroup(String payload) throws Exception {
        StoreGroupMessageEvent event = objectMapper.readValue(payload, StoreGroupMessageEvent.class);
        if (event.getGroupChatMessageContent() == null) {
            return;
        }
        NormalizedMessage message = messageNormalizationService.normalizeGroup(event.getGroupChatMessageContent());
        process(message);
    }

    private void process(NormalizedMessage message) throws Exception {
        if (message.getMessageKind() == MessageKind.IMAGE) {
            ImageMessageBody imageMessageBody = objectMapper.readValue(message.getRawMessageBody(), ImageMessageBody.class);
            ImageInsightResult insightResult = imageInsightService.analyze(imageMessageBody);
            messageNormalizationService.applyImageInsight(message, insightResult.getOcrText(), insightResult.getCaption());
        }

        semanticIndexService.upsert(message);
        contentAuditService.auditMessage(message);
    }
}
