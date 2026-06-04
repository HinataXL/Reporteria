package com.erick.soporte.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class GeminiReportService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    private String lastReport = null;
    private long lastGenerated = 0;

    public String generateDashboardReport(
            long total,
            long pendientes,
            long resueltas,
            long escaladas,
            double promedioTiempo
    ) {
        long now = System.currentTimeMillis();

        if (lastReport != null && (now - lastGenerated) < 300000) {
            return lastReport;
        }

        String prompt = """
        Eres un asistente de análisis operativo para un equipo de soporte.

        Tu objetivo es explicar las métricas de forma clara, neutral y constructiva.
        Evita un tono alarmista o agresivo. No critiques al equipo.
        Enfócate en interpretar los datos, encontrar patrones y proponer oportunidades de mejora.

        Genera un reporte en español con esta estructura:

        1. Resumen general
        Explica qué muestran las métricas principales.

        2. Lectura de las métricas
        Comenta qué significa el volumen total, pendientes, resueltas, escaladas y tiempo promedio.

        3. Hallazgos relevantes
        Identifica patrones visibles en los datos sin exagerar conclusiones.

        4. Oportunidades de mejora
        Sugiere mejoras prácticas y realistas.

        5. Recomendación final
        Da una conclusión breve y útil para el supervisor.

        Métricas actuales:
        - Total conversaciones: %d
        - Pendientes: %d
        - Resueltas: %d
        - Escaladas: %d
        - Tiempo promedio de gestión: %.1f minutos

        Redacta con tono profesional, calmado, objetivo y orientado a mejora continua.
        No uses lenguaje negativo fuerte.
        No inventes datos que no estén presentes.
        """.formatted(
                total,
                pendientes,
                resueltas,
                escaladas,
                promedioTiempo
        );

        try {
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

            Map response = restTemplate.postForObject(url, body, Map.class);

            List candidates = (List) response.get("candidates");
            Map candidate = (Map) candidates.get(0);
            Map content = (Map) candidate.get("content");
            List parts = (List) content.get("parts");
            Map part = (Map) parts.get(0);

            String report = String.valueOf(part.get("text"));

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
}