package com.lld.im.ai.repository;

import com.lld.im.ai.model.AuditResult;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class AuditResultRepository {

    private final Map<Long, AuditResult> results = new ConcurrentHashMap<>();

    public void save(AuditResult auditResult) {
        if (auditResult.getMessageKey() != null) {
            results.put(auditResult.getMessageKey(), auditResult);
        }
    }

    public Optional<AuditResult> findByMessageKey(Long messageKey) {
        return Optional.ofNullable(results.get(messageKey));
    }
}
