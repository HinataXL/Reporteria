package com.erick.soporte.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class GeminiReportService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    private String lastReport = null;
    private long lastGenerated = 0;
    private String lastIssueTrendKey = null;
    private String lastIssueTrendReport = null;
    private long lastIssueTrendGenerated = 0;
    private final AtomicLong dashboardRequests = new AtomicLong();
    private final AtomicLong issueTrendRequests = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong externalCalls = new AtomicLong();
    private final AtomicLong successfulCalls = new AtomicLong();
    private final AtomicLong failedCalls = new AtomicLong();
    private volatile LocalDateTime lastCallAt;
    private volatile LocalDateTime lastSuccessAt;
    private volatile LocalDateTime lastFailureAt;
    private volatile String lastOperation = "Sin actividad";
    private volatile String lastError = null;

    public String generateDashboardReport(
            long total,
            long pendientes,
            long resueltas,
            long escaladas,
            double promedioTiempo
    ) {
        dashboardRequests.incrementAndGet();
        long now = System.currentTimeMillis();

        if (lastReport != null && (now - lastGenerated) < 300000) {
            cacheHits.incrementAndGet();
            return lastReport;
        }

        String prompt = """
            Genera exactamente 3 bloques separados por el símbolo |.

            Bloque 1: desempeño general.
            Bloque 2: canal dominante.
            Bloque 3: área de mejora.

            No uses títulos. No uses markdown. Sé breve, profesional y claro.
""".formatted(
                total,
                pendientes,
                resueltas,
                escaladas,
                promedioTiempo
        );

        try {
            String report = callGemini(prompt, "Resumen dashboard");

            lastReport = report;
            lastGenerated = now;

            return report;

        } catch (Exception e) {

            e.printStackTrace();

            return """
            Análisis IA local temporal:

            No fue posible consultar Gemini.

            Error técnico:
            %s
            """.formatted(e.getMessage());
        }
    }

    public String generateIssueTrendAnalysis(Map<String, Object> issueTrend) {
        issueTrendRequests.incrementAndGet();
        long now = System.currentTimeMillis();
        String cacheKey = String.valueOf(issueTrend);

        if (cacheKey.equals(lastIssueTrendKey) && lastIssueTrendReport != null && (now - lastIssueTrendGenerated) < 300000) {
            cacheHits.incrementAndGet();
            return lastIssueTrendReport;
        }

        String prompt = """
            Analiza la tendencia semanal de asuntos de un dashboard de soporte.

            Datos:
            %s

            Responde en espanol, maximo 4 frases, sin markdown.
            Indica:
            1. asunto que mas crece,
            2. asunto que baja o se estabiliza,
            3. posible causa operativa,
            4. accion recomendada para supervisor.
            """.formatted(issueTrend);

        try {
            String report = callGemini(prompt, "Tendencia de asuntos");
            lastIssueTrendKey = cacheKey;
            lastIssueTrendReport = report;
            lastIssueTrendGenerated = now;
            return report;
        } catch (Exception e) {
            return fallbackIssueTrendAnalysis(issueTrend);
        }
    }

    public Map<String, Object> getUsageSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("model", model);
        snapshot.put("configured", apiKey != null && !apiKey.isBlank() && !apiKey.contains("${"));
        snapshot.put("dashboardRequests", dashboardRequests.get());
        snapshot.put("issueTrendRequests", issueTrendRequests.get());
        snapshot.put("cacheHits", cacheHits.get());
        snapshot.put("externalCalls", externalCalls.get());
        snapshot.put("successfulCalls", successfulCalls.get());
        snapshot.put("failedCalls", failedCalls.get());
        snapshot.put("lastCallAt", lastCallAt);
        snapshot.put("lastSuccessAt", lastSuccessAt);
        snapshot.put("lastFailureAt", lastFailureAt);
        snapshot.put("lastOperation", lastOperation);
        snapshot.put("lastError", lastError);
        return snapshot;
    }

    private String callGemini(String prompt, String operation) {
        externalCalls.incrementAndGet();
        lastCallAt = LocalDateTime.now();
        lastOperation = operation;

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model
                + ":generateContent?key="
                + apiKey;

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", prompt)
                                )
                        )
                )
        );

        try {
            Map response = restTemplate.postForObject(url, body, Map.class);

            List candidates = (List) response.get("candidates");
            Map candidate = (Map) candidates.get(0);
            Map content = (Map) candidate.get("content");
            List parts = (List) content.get("parts");
            Map part = (Map) parts.get(0);

            successfulCalls.incrementAndGet();
            lastSuccessAt = LocalDateTime.now();
            lastError = null;
            return String.valueOf(part.get("text"));
        } catch (Exception e) {
            failedCalls.incrementAndGet();
            lastFailureAt = LocalDateTime.now();
            lastError = e.getMessage();
            throw e;
        }
    }

    private String fallbackIssueTrendAnalysis(Map<String, Object> issueTrend) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) issueTrend.getOrDefault("items", List.of());

        if (items.isEmpty()) {
            return "Aun no hay datos suficientes para identificar tendencia por asuntos frente a la semana anterior.";
        }

        Map<String, Object> top = items.get(0);
        return "El asunto con mayor movimiento es " + top.get("asunto") + ", con " + top.get("current")
                + " conversaciones esta semana frente a " + top.get("previous")
                + " la semana anterior. Revisa si el cambio responde a carga operativa, comunicacion preventiva o una incidencia recurrente.";
    }
}
