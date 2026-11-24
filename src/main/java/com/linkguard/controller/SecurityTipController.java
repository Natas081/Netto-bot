package com.linkguard.controller;

import com.linkguard.service.SecurityTipService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityTipController {

    private final SecurityTipService securityTipService;

    public SecurityTipController(SecurityTipService securityTipService) {
        this.securityTipService = securityTipService;
    }

    @GetMapping("/api/tips/random")
    public ResponseEntity<TipResponse> randomTip() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new TipResponse(securityTipService.randomTip()));
    }

    public record TipResponse(String tip) {
    }
}
