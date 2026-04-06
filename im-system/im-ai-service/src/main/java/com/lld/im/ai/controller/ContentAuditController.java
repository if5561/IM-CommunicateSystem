package com.lld.im.ai.controller;

import com.lld.im.ai.model.AuditResult;
import com.lld.im.ai.model.ManualAuditRequest;
import com.lld.im.ai.repository.AuditResultRepository;
import com.lld.im.ai.service.ContentAuditService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai/audit")
public class ContentAuditController {

    private final ContentAuditService contentAuditService;

    private final AuditResultRepository auditResultRepository;

    public ContentAuditController(ContentAuditService contentAuditService, AuditResultRepository auditResultRepository) {
        this.contentAuditService = contentAuditService;
        this.auditResultRepository = auditResultRepository;
    }

    @PostMapping("/manual")
    public AuditResult manualAudit(@Valid @RequestBody ManualAuditRequest request) {
        return contentAuditService.auditText(request.getText());
    }

    @GetMapping("/{messageKey}")
    @ResponseStatus(HttpStatus.OK)
    public AuditResult getAuditResult(@PathVariable Long messageKey) {
        return auditResultRepository.findByMessageKey(messageKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到审核结果"));
    }
}
