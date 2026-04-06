package com.lld.im.ai.service;

import com.lld.im.ai.config.AiModuleProperties;
import com.lld.im.ai.model.NormalizedMessage;
import com.lld.im.ai.model.SemanticSearchHit;
import com.lld.im.ai.model.SemanticSearchRequest;
import com.lld.im.ai.model.StoredMessageIndex;
import com.lld.im.ai.repository.MessageArchiveRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SemanticIndexService {

    private final EmbeddingModel embeddingModel;

    private final AiModuleProperties aiModuleProperties;

    private final MessageArchiveRepository messageArchiveRepository;

    public SemanticIndexService(EmbeddingModel embeddingModel, AiModuleProperties aiModuleProperties,
                                MessageArchiveRepository messageArchiveRepository) {
        this.embeddingModel = embeddingModel;
        this.aiModuleProperties = aiModuleProperties;
        this.messageArchiveRepository = messageArchiveRepository;
    }

    public void upsert(NormalizedMessage message) {
        if (!StringUtils.hasText(message.getSearchableText())) {
            return;
        }
        float[] vector = embeddingModel.embed(message.getSearchableText());
        messageArchiveRepository.save(message, vector);
    }

    public List<SemanticSearchHit> search(SemanticSearchRequest request) {
        int topK = request.getTopK() == null ? aiModuleProperties.getDefaultTopK() : request.getTopK();
        float[] queryVector = embeddingModel.embed(request.getQuery());
        List<StoredMessageIndex> candidates = messageArchiveRepository.searchByVector(
                request.getAppId(),
                request.getConversationId(),
                queryVector,
                Math.max(topK, aiModuleProperties.getSearchCandidateLimit())
        );
        return candidates.stream()
                .map(item -> toHit(item.getMessage(), item.getScore()))
                .sorted(Comparator.comparingDouble(SemanticSearchHit::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private SemanticSearchHit toHit(NormalizedMessage message, double score) {
        String snippet = StringUtils.hasText(message.getTextContent()) ? message.getTextContent()
                : StringUtils.hasText(message.getCaption()) ? message.getCaption() : message.getOcrText();
        return SemanticSearchHit.builder()
                .messageKey(message.getMessageKey())
                .conversationId(message.getConversationId())
                .conversationKind(message.getConversationKind().name())
                .messageKind(message.getMessageKind().name())
                .snippet(snippet)
                .imageUrl(message.getImageUrl())
                .fromId(message.getFromId())
                .messageTime(message.getMessageTime())
                .score(score)
                .build();
    }

}
