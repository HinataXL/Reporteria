package com.erick.soporte.service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseTextConfig;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
public class OpenAiReportService {

    private final OpenAIClient client;

    public OpenAiReportService(@Value("${openai.api.key}") String apiKey) {
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    public String generateDashboardReport(
            long total,
            long pendientes,
            long resueltas,
            long escaladas,
            double promedioTiempo
    ) {

        String prompt = """
                Eres una IA supervisora de un centro de soporte.

                Analiza estas métricas y genera:
                1. Resumen ejecutivo
                2. Riesgos operativos
                3. Puntos de mejora
                4. Recomendaciones accionables

                Métricas:
                - Total conversaciones: %d
                - Pendientes: %d
                - Resueltas: %d
                - Escaladas: %d
                - Tiempo promedio gestión: %.1f minutos

                Responde en español profesional.
                """.formatted(
                total,
                pendientes,
                resueltas,
                escaladas,
                promedioTiempo
        );

        ResponseCreateParams params = ResponseCreateParams.builder()
                .model("gpt-5-mini")
                .input(prompt)
                .text(ResponseTextConfig.builder().build())
                .build();

        var response = client.responses().create(params);

        return response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(text -> text.text())
                .reduce("", String::concat);
    }
}