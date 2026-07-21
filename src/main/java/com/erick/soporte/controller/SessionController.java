package com.erick.soporte.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    @PostMapping("/keep-alive")
    public ResponseEntity<Map<String, Object>> keepAlive(
            HttpSession session
    ) {

        session.setAttribute(
                "lastKeepAlive",
                LocalDateTime.now(
                        ZoneId.of("America/Guatemala")
                )
        );

        return ResponseEntity.ok(
                Map.of(
                        "alive", true,
                        "message", "Sesión renovada"
                )
        );
    }
}
