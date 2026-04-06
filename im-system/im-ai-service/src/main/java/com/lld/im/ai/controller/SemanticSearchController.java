package com.lld.im.ai.controller;

import com.lld.im.ai.model.SemanticSearchHit;
import com.lld.im.ai.model.SemanticSearchRequest;
import com.lld.im.ai.service.SemanticIndexService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai/search")
public class SemanticSearchController {

    private final SemanticIndexService semanticIndexService;

    public SemanticSearchController(SemanticIndexService semanticIndexService) {
        this.semanticIndexService = semanticIndexService;
    }

    @PostMapping("/semantic")
    public List<SemanticSearchHit> semanticSearch(@Valid @RequestBody SemanticSearchRequest request) {
        return semanticIndexService.search(request);
    }
}
