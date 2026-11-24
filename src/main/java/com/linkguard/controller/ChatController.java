package com.linkguard.controller;

import com.linkguard.dto.ChatRequest;
import com.linkguard.dto.ChatResponse;
import com.linkguard.exception.InvalidUrlException;
import com.linkguard.service.LinkAnalysisService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
public class ChatController {

    private final LinkAnalysisService linkAnalysisService;

    public ChatController(LinkAnalysisService linkAnalysisService) {
        this.linkAnalysisService = linkAnalysisService;
    }

    @PostMapping(path = "/api/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatResponse> chat(@RequestBody(required = false) ChatRequest request) {
        ChatResponse response = linkAnalysisService.analyze(request == null ? null : request.message());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping(path = "/api/chat/analyses/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatResponse> analysis(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(linkAnalysisService.poll(id));
    }

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ChatResponse> invalidUrl(InvalidUrlException ex) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(ChatResponse.inconclusive(null, ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ChatResponse> invalidJson() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(ChatResponse.inconclusive(null, "Envie um link no campo message do JSON."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ChatResponse> invalidAnalysisId() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(ChatResponse.inconclusive(null, "Esta verificação é inválida. Envie o link novamente."));
    }
}
