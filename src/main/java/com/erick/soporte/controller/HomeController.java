package com.erick.soporte.controller;

import com.erick.soporte.entity.Conversation;
import com.erick.soporte.repository.ConversationRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;

@Controller
public class HomeController {

    private final ConversationRepository conversationRepository;

    public HomeController(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/conversations")
    public String conversations(Model model) {
        model.addAttribute("conversations", conversationRepository.findAll());
        return "conversations/index";
    }

    @GetMapping("/conversations/create")
    public String createConversation(Model model) {
        model.addAttribute("conversation", new Conversation());
        return "conversations/create";
    }

    @PostMapping("/conversations/save")
    public String saveConversation(@ModelAttribute Conversation conversation) {
        Conversation saved = conversationRepository.save(conversation);

        if (saved.getCodigo() == null || saved.getCodigo().isBlank()) {
            saved.setCodigo("CONV-" + String.format("%05d", saved.getId()));
            conversationRepository.save(saved);
        }

        return "redirect:/conversations";
    }

    @GetMapping("/conversations/{id}")
    public String detailConversation(@PathVariable Long id, Model model) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conversación no encontrada"));

        model.addAttribute("conversation", conversation);
        return "conversations/detail";
    }

    @GetMapping("/conversations/export/csv")
    public void exportCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=historico_conversaciones.csv"
        );

        PrintWriter writer = response.getWriter();

        writer.println("Codigo,Cliente,Telefono,Correo,Asunto,Descripcion,Canal,Estado,Prioridad,Tiempo Gestion,Fecha Inicio,Observaciones");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        for (Conversation c : conversationRepository.findAll()) {
            writer.println(
                    safe(c.getCodigo()) + "," +
                            safe(c.getClienteNombre()) + "," +
                            safe(c.getClienteTelefono()) + "," +
                            safe(c.getClienteCorreo()) + "," +
                            safe(c.getAsunto()) + "," +
                            safe(c.getDescripcion()) + "," +
                            safe(channelName(c.getChannelId())) + "," +
                            safe(statusName(c.getStatusId())) + "," +
                            safe(priorityName(c.getPriorityId())) + "," +
                            safe(String.valueOf(c.getTiempoGestionMinutos())) + "," +
                            safe(c.getFechaInicio() != null ? c.getFechaInicio().format(formatter) : "") + "," +
                            safe(c.getObservaciones())
            );
        }

        writer.flush();
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }

        String cleanValue = value.replace("\"", "\"\"");
        return "\"" + cleanValue + "\"";
    }

    private String channelName(Long id) {
        if (id == null) return "";

        return switch (id.intValue()) {
            case 1 -> "WhatsApp";
            case 2 -> "Facebook";
            case 3 -> "Instagram";
            case 4 -> "Web Chat";
            case 5 -> "Correo";
            default -> "Desconocido";
        };
    }

    private String statusName(Long id) {
        if (id == null) return "";

        return switch (id.intValue()) {
            case 1 -> "Pendiente";
            case 2 -> "En Proceso";
            case 3 -> "Resuelto";
            case 4 -> "Escalado";
            case 5 -> "Cerrado";
            default -> "Desconocido";
        };
    }

    private String priorityName(Long id) {
        if (id == null) return "";

        return switch (id.intValue()) {
            case 1 -> "Baja";
            case 2 -> "Media";
            case 3 -> "Alta";
            case 4 -> "Crítica";
            default -> "Desconocida";
        };
    }
}