package com.lld.im.ai.controller;

import com.lld.im.ai.model.ConversationSummaryRequest;
import com.lld.im.ai.model.ConversationSummaryResponse;
import com.lld.im.ai.service.ConversationSummaryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/summary")
public class ConversationSummaryController {

    private final ConversationSummaryService conversationSummaryService;

    public ConversationSummaryController(ConversationSummaryService conversationSummaryService) {
        this.conversationSummaryService = conversationSummaryService;
    }

    @PostMapping("/conversation")
    public ConversationSummaryResponse summarize(@Valid @RequestBody ConversationSummaryRequest request) {
        return conversationSummaryService.summarize(request);
    }
}
