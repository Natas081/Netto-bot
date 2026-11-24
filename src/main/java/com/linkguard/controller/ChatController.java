package com.linkguard.controller;

import com.linkguard.dto.ChatRequest;
import com.linkguard.dto.ChatResponse;
import com.linkguard.dto.ChatResponse.Status;
import com.linkguard.service.LinkAnalysisService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final LinkAnalysisService linkAnalysisService;

    public ChatController(LinkAnalysisService linkAnalysisService) {
        this.linkAnalysisService = linkAnalysisService;
    }

    @PostMapping(path = "/api/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatResponse> chat(@RequestBody(required = false) ChatRequest request) {
        ChatResponse response = linkAnalysisService.analyze(request == null ? null : request.message());
        if (response.status() == Status.INVALIDO) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ChatResponse> invalidJson() {
        return ResponseEntity.badRequest().body(ChatResponse.invalid("Envie um link no campo message do JSON."));
    }
}
