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