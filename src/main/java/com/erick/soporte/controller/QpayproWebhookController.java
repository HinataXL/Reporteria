package com.erick.soporte.controller;

import com.erick.soporte.entity.WebhookEvent;
import com.erick.soporte.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class QpayproWebhookController {

    private final WebhookEventRepository webhookEventRepository;


    public QpayproWebhookController(
            WebhookEventRepository webhookEventRepository

    ) {
        this.webhookEventRepository = webhookEventRepository;

    }

    @PostMapping("/qpaypro")
    public ResponseEntity<String> receiveQpayproWebhook(
            @RequestBody String payload,
            HttpServletRequest request
    ) {

        WebhookEvent event = new WebhookEvent();

        event.setSource("QPAYPRO");
        event.setPayload(payload);
        event.setIp(request.getRemoteAddr());
        event.setUserAgent(request.getHeader("User-Agent"));

        webhookEventRepository.save(event);

        return ResponseEntity.ok("OK");
    }

    @GetMapping("/qpaypro/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("QPAYPRO WEBHOOK READY");
    }

    private String getText(JsonNode json, String... keys) {
        for (String key : keys) {
            if (json.has(key) && !json.get(key).isNull()) {
                return json.get(key).asText();
            }
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
