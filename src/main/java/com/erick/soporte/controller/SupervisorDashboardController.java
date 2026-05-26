package com.erick.soporte.controller;

import com.erick.soporte.entity.Conversation;
import com.erick.soporte.repository.ConversationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class SupervisorDashboardController {

    private final ConversationRepository conversationRepository;

    public SupervisorDashboardController(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @GetMapping("/supervisor/dashboard")
    public String dashboard(Model model) {
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

        model.addAttribute("total", total);
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("resueltas", resueltas);
        model.addAttribute("escaladas", escaladas);
        model.addAttribute("promedioTiempo", String.format("%.1f", promedioTiempo));

        model.addAttribute("agenteLabels", porAgente.keySet());
        model.addAttribute("agenteValues", porAgente.values());

        model.addAttribute("canalLabels", porCanal.keySet());
        model.addAttribute("canalValues", porCanal.values());

        model.addAttribute("prioridadLabels", porPrioridad.keySet());
        model.addAttribute("prioridadValues", porPrioridad.values());

        model.addAttribute("productividadLabels", productividadDiaria.keySet());
        model.addAttribute("productividadValues", productividadDiaria.values());

        return "supervisor/dashboard";
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