package com.erick.soporte.controller;

import com.erick.soporte.entity.Conversation;
import com.erick.soporte.repository.ConversationRepository;
import com.erick.soporte.service.GeminiReportService;
import com.erick.soporte.service.PdfReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/supervisor/report")
public class SupervisorReportController {

    private final ConversationRepository conversationRepository;
    private final GeminiReportService geminiReportService;
    private final PdfReportService pdfReportService;
    private String channelName(Long id) {
        if (id == null) return "Desconocido";

        return switch (id.intValue()) {
            case 1 -> "WhatsApp";
            case 2 -> "Instagram";
            case 3 -> "Facebook";
            default -> "Desconocido";
        };
    }

    private String priorityName(Long id) {
        if (id == null) return "Desconocida";

        return switch (id.intValue()) {
            case 1 -> "Baja";
            case 2 -> "Normal";
            case 3 -> "Alta";
            default -> "Desconocida";
        };
    }

    public SupervisorReportController(
            ConversationRepository conversationRepository,
            GeminiReportService geminiReportService,
            PdfReportService pdfReportService
    ) {
        this.conversationRepository = conversationRepository;
        this.geminiReportService = geminiReportService;
        this.pdfReportService = pdfReportService;
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> downloadPdf() {

        List<Conversation> conversations = conversationRepository.findAll();

        Map<String, Long> porCanal = conversations.stream()
                .collect(Collectors.groupingBy(
                        c -> channelName(c.getChannelId()),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        Map<String, Long> porPrioridad = conversations.stream()
                .collect(Collectors.groupingBy(
                        c -> priorityName(c.getPriorityId()),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        Map<String, Long> porAsunto = conversations.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getAsunto() != null ? c.getAsunto() : "Sin asunto",
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        long total = conversations.size();

        long pendientes = conversations.stream()
                .filter(c -> c.getStatusId() != null && c.getStatusId() == 1)
                .count();

        long resueltas = conversations.stream()
                .filter(c -> c.getStatusId() != null && c.getStatusId() == 3)
                .count();

        long escaladas = conversations.stream()
                .filter(c -> c.getStatusId() != null && c.getStatusId() == 4)
                .count();

        double promedioTiempo = conversations.stream()
                .filter(c -> c.getTiempoGestionMinutos() != null)
                .mapToInt(Conversation::getTiempoGestionMinutos)
                .average()
                .orElse(0);

        String aiReport = geminiReportService.generateDashboardReport(
                total,
                pendientes,
                resueltas,
                escaladas,
                promedioTiempo
        );

        byte[] pdf = pdfReportService.generatePdf(
                total,
                pendientes,
                resueltas,
                escaladas,
                promedioTiempo,
                aiReport,
                porCanal,
                porPrioridad,
                porAsunto
        );

        String fileName = "ReporteSupervisor_" +
                LocalDateTime.now()
                        .toLocalDate()
                + ".pdf";

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + fileName
                )
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}