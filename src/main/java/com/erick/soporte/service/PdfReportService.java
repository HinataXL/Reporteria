package com.erick.soporte.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;


import java.io.ByteArrayOutputStream;

@Service
public class PdfReportService {

    public byte[] generatePdf(
            long total,
            long pendientes,
            long resueltas,
            long escaladas,
            double promedioTiempo,
            String aiReport
    ) {
        try {
            String html = buildPdfHtml(
                    total,
                    pendientes,
                    resueltas,
                    escaladas,
                    promedioTiempo,
                    aiReport
            );

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();

            return outputStream.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF", e);
        }
    }

    private String buildPdfHtml(
            long total,
            long pendientes,
            long resueltas,
            long escaladas,
            double promedioTiempo,
            String aiReport
    ) {
        return """
                <html>
                <head>
                    <meta charset="UTF-8" />
                    <style>
                        @page {
                            size: A4;
                            margin: 24px;
                        }

                        body {
                            font-family: Arial, sans-serif;
                            color: #10231F;
                            background: #F3F7F8;
                            margin: 0;
                            padding: 0;
                        }

                        .header {
                            background: #10231F;
                            color: white;
                            padding: 30px;
                            border-radius: 18px;
                            margin-bottom: 22px;
                        }

                        .eyebrow {
                            color: #00D9FF;
                            font-size: 11px;
                            font-weight: bold;
                            letter-spacing: 2px;
                            text-transform: uppercase;
                            margin-bottom: 8px;
                        }

                        .title {
                            font-size: 30px;
                            font-weight: 800;
                            margin: 0;
                        }

                        .subtitle {
                            font-size: 13px;
                            color: #D7E4E8;
                            margin-top: 8px;
                        }

                        .date {
                            font-size: 12px;
                            color: #FFD700;
                            margin-top: 14px;
                        }

                        .metrics {
                            width: 100%%;
                            border-collapse: separate;
                            border-spacing: 10px;
                            margin-bottom: 22px;
                        }

                        .metric {
                            background: white;
                            border: 1px solid #DDE8EB;
                            border-radius: 14px;
                            padding: 18px;
                            width: 25%%;
                        }

                        .metric-label {
                            font-size: 11px;
                            color: #60727A;
                            font-weight: bold;
                            text-transform: uppercase;
                        }

                        .metric-value {
                            font-size: 30px;
                            font-weight: 800;
                            margin-top: 8px;
                            color: #1BA0C8;
                        }

                        .gold { color: #C79A00; }
                        .green { color: #009E60; }
                        .red { color: #D64545; }

                        .section {
                            background: white;
                            border: 1px solid #DDE8EB;
                            border-radius: 18px;
                            padding: 24px;
                            margin-bottom: 18px;
                        }

                        .section-title {
                            font-size: 20px;
                            font-weight: 800;
                            margin: 0 0 12px 0;
                            color: #10231F;
                        }

                        .paragraph {
                            font-size: 13px;
                            line-height: 1.6;
                            color: #2D4650;
                        }

                        .ai-report {
                            white-space: pre-line;
                            font-size: 13px;
                            line-height: 1.65;
                            color: #243B44;
                        }

                        .highlight {
                            background: #EAF8FB;
                            border-left: 5px solid #1BA0C8;
                            padding: 16px;
                            border-radius: 12px;
                            font-size: 13px;
                            line-height: 1.6;
                        }

                        .footer {
                            margin-top: 26px;
                            padding-top: 14px;
                            border-top: 1px solid #DDE8EB;
                            text-align: center;
                            font-size: 10px;
                            color: #6B7C83;
                        }
                    </style>
                </head>

                <body>

                    <div class="header">
                        <div class="eyebrow">Reporte automático</div>
                        <h1 class="title">Reporte Metricas Soporte</h1>
                        <div class="subtitle">
                            Métricas operativas, análisis IA y recomendaciones para mejora continua.
                        </div>
                        <div class="date">
                            Generado automáticamente por el sistema de reportería.
                        </div>
                    </div>

                    <table class="metrics">
                        <tr>
                            <td class="metric">
                                <div class="metric-label">Total conversaciones</div>
                                <div class="metric-value">%d</div>
                            </td>

                            <td class="metric">
                                <div class="metric-label">Pendientes</div>
                                <div class="metric-value gold">%d</div>
                            </td>

                            <td class="metric">
                                <div class="metric-label">Resueltas</div>
                                <div class="metric-value green">%d</div>
                            </td>

                            <td class="metric">
                                <div class="metric-label">Escaladas</div>
                                <div class="metric-value red">%d</div>
                            </td>
                        </tr>
                    </table>

                    <div class="section">
                        <h2 class="section-title">Resumen operativo</h2>
                        <p class="paragraph">
                            Durante el período analizado se registraron <strong>%d</strong> conversaciones.
                            Actualmente existen <strong>%d</strong> conversaciones pendientes,
                            <strong>%d</strong> resueltas y <strong>%d</strong> escaladas.
                        </p>
                    </div>

                    <div class="section">
                        <h2 class="section-title">Tiempo promedio de gestión</h2>
                        <div class="highlight">
                            El tiempo promedio de gestión es de
                            <strong>%.1f minutos</strong> por conversación registrada.
                        </div>
                    </div>

                    <div class="section">
                        <h2 class="section-title">Análisis IA</h2>
                        <div class="ai-report">%s</div>
                    </div>

                    <div class="section">
                        <h2 class="section-title">Conclusión</h2>
                        <p class="paragraph">
                            Este reporte permite visualizar el estado general del equipo de soporte,
                            identificar acumulación de casos, revisar conversaciones escaladas y tomar
                            decisiones operativas con base en datos actualizados.
                        </p>
                    </div>

                    <div class="footer">
                        Sistema de Reportería de Soporte · PDF generado automáticamente
                    </div>

                </body>
                </html>
                """.formatted(
                total,
                pendientes,
                resueltas,
                escaladas,
                total,
                pendientes,
                resueltas,
                escaladas,
                promedioTiempo,
                escapeHtml(aiReport)
        );
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}