package com.erick.soporte.controller;

import com.erick.soporte.repository.ConversationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SupervisorDashboardController {

    private final ConversationRepository conversationRepository;

    public SupervisorDashboardController(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @GetMapping("/supervisor/dashboard")
    public String dashboard(Model model) {
        var conversations = conversationRepository.findAll();

        long total = conversations.size();
        long resueltas = conversations.stream().filter(c -> c.getStatusId() != null && c.getStatusId() == 3).count();
        long pendientes = conversations.stream().filter(c -> c.getStatusId() != null && c.getStatusId() == 1).count();
        long escaladas = conversations.stream().filter(c -> c.getStatusId() != null && c.getStatusId() == 4).count();

        double promedioTiempo = conversations.stream()
                .filter(c -> c.getTiempoGestionMinutos() != null)
                .mapToInt(c -> c.getTiempoGestionMinutos())
                .average()
                .orElse(0);

        model.addAttribute("total", total);
        model.addAttribute("resueltas", resueltas);
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("escaladas", escaladas);
        model.addAttribute("promedioTiempo", String.format("%.1f", promedioTiempo));

        return "supervisor/dashboard";
    }
}