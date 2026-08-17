package com.erick.soporte.controller;

import com.erick.soporte.entity.FrontendErrorLog;
import com.erick.soporte.repository.FrontendErrorLogRepository;
import com.erick.soporte.security.CustomUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FrontendErrorController {

    private static final int TEXT_LIMIT = 4000;

    private final FrontendErrorLogRepository frontendErrorLogRepository;

    public FrontendErrorController(FrontendErrorLogRepository frontendErrorLogRepository) {
        this.frontendErrorLogRepository = frontendErrorLogRepository;
    }

    @PostMapping("/api/frontend-errors")
    public ResponseEntity<Map<String, Object>> save(
            @RequestBody FrontendErrorRequest payload,
            Authentication authentication,
            HttpServletRequest request
    ) {
        FrontendErrorRequest safePayload = payload != null
                ? payload
                : new FrontendErrorRequest("ERROR", "browser", "Reporte vacio", null, null, null, null, null);
        FrontendErrorLog log = new FrontendErrorLog();

        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal user) {
            log.setUsuarioId(user.getId());
            log.setUsuarioNombre(limit(user.getNombreCompleto(), 180));
            log.setUsuarioCorreo(limit(user.getCorreo(), 180));
            log.setRol(limit(user.getRol(), 80));
        }

        log.setLevel(limit(defaultIfBlank(safePayload.level(), "ERROR"), 30));
        log.setEventType(limit(defaultIfBlank(safePayload.eventType(), "browser"), 80));
        log.setMessage(limit(safePayload.message(), TEXT_LIMIT));
        log.setSource(limit(safePayload.source(), TEXT_LIMIT));
        log.setStackTrace(limit(safePayload.stackTrace(), TEXT_LIMIT));
        log.setPageUrl(limit(safePayload.pageUrl(), TEXT_LIMIT));
        log.setLineNumber(safePayload.lineNumber());
        log.setColumnNumber(safePayload.columnNumber());
        log.setIp(limit(request.getRemoteAddr(), 80));
        log.setUserAgent(limit(request.getHeader("User-Agent"), TEXT_LIMIT));

        frontendErrorLogRepository.save(log);

        return ResponseEntity.ok(Map.of("saved", true));
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String cleaned = value.strip();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    public record FrontendErrorRequest(
            String level,
            String eventType,
            String message,
            String source,
            String stackTrace,
            String pageUrl,
            Integer lineNumber,
            Integer columnNumber
    ) {
    }
}
