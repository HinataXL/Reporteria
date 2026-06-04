package com.erick.soporte.controller;

import com.erick.soporte.entity.Conversation;
import com.erick.soporte.repository.ConversationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.erick.soporte.service.GeminiReportService;

@RestController
public class DashboardApiController {

    private final ConversationRepository conversationRepository;
    private final GeminiReportService geminiReportService;

    public DashboardApiController(ConversationRepository conversationRepository, GeminiReportService geminiReportService) {
        this.conversationRepository = conversationRepository;
        this.geminiReportService = geminiReportService;

    }
    @GetMapping("/api/dashboard/ai-report")
    public Map<String, Object> aiReport() {

        List<Conversation> conversations = conversationRepository.findAll();

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

        String reporte = geminiReportService.generateDashboardReport(
                total,
                pendientes,
                resueltas,
                escaladas,
                promedioTiempo
        );

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("report", reporte);

        return response;
    }
    @GetMapping("/api/dashboard/metrics")
    public Map<String, Object> metrics() {
        List<Conversation> conversations = conversationRepository.findAll();

        long total = conversations.size();
        long pendientes = conversations.stream().filter(c -> c.getStatusId() != null && c.getStatusId() == 1).count();
        long resueltas = conversations.stream().filter(c -> c.getStatusId() != null && c.getStatusId() == 3).count();
        long escaladas = conversations.stream().filter(c -> c.getStatusId() != null && c.getStatusId() == 4).count();

        double promedioTiempo = conversations.stream()
                .filter(c -> c.getTiempoGestionMinutos() != null)
                .mapToInt(Conversation::getTiempoGestionMinutos)
                .average()
                .orElse(0);

        Map<String, Long> porAgente = conversations.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getAgenteNombre() != null ? c.getAgenteNombre() : "Sin agente",
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

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

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");

        Map<String, Long> productividadDiaria = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .collect(Collectors.groupingBy(
                        c -> c.getFechaInicio().format(formatter),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        Map<String, Long> porAsunto = conversations.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getAsunto() != null ? c.getAsunto() : "Sin asunto",
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("total", total);
        response.put("pendientes", pendientes);
        response.put("resueltas", resueltas);
        response.put("escaladas", escaladas);
        response.put("promedioTiempo", String.format("%.1f", promedioTiempo));

        response.put("agenteLabels", porAgente.keySet());
        response.put("agenteValues", porAgente.values());

        response.put("canalLabels", porCanal.keySet());
        response.put("canalValues", porCanal.values());

        response.put("prioridadLabels", porPrioridad.keySet());
        response.put("prioridadValues", porPrioridad.values());

        response.put("productividadLabels", productividadDiaria.keySet());
        response.put("productividadValues", productividadDiaria.values());
        response.put("asuntoLabels", porAsunto.keySet());
        response.put("asuntoValues", porAsunto.values());

        return response;
    }

    private String channelName(Long id) {
        if (id == null) return "Desconocido";

        return switch (id.intValue()) {
            case 1 -> "WhatsApp";
            case 2 -> "Facebook";
            case 3 -> "Instagram";
            case 4 -> "Web Chat";
            case 5 -> "Correo";
            default -> "Desconocido";
        };
    }

    private String priorityName(Long id) {
        if (id == null) return "Desconocida";

        return switch (id.intValue()) {
            case 1 -> "Baja";
            case 2 -> "Media";
            case 3 -> "Alta";
            case 4 -> "Crítica";
            default -> "Desconocida";
        };
    }
}