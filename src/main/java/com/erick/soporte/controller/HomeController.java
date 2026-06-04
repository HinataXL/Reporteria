package com.erick.soporte.controller;

import com.erick.soporte.entity.Conversation;
import com.erick.soporte.repository.ConversationRepository;
import com.erick.soporte.repository.DepartmentRepository;
import com.erick.soporte.repository.IssueTypeRepository;
import com.erick.soporte.repository.RejectionCodeRepository;
import com.erick.soporte.security.CustomUserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.erick.soporte.entity.IssueType;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;

@Controller
public class HomeController {

    private final ConversationRepository conversationRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final RejectionCodeRepository rejectionCodeRepository;
    private final DepartmentRepository departmentRepository;

    public HomeController(
            ConversationRepository conversationRepository,
            IssueTypeRepository issueTypeRepository,
            RejectionCodeRepository rejectionCodeRepository,
            DepartmentRepository departmentRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.issueTypeRepository = issueTypeRepository;
        this.rejectionCodeRepository = rejectionCodeRepository;
        this.departmentRepository = departmentRepository;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/conversations")
    public String conversations(Model model, Authentication authentication) {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();

        model.addAttribute("conversations", conversationRepository.findAll());
        model.addAttribute("userName", user.getNombreCompleto());
        model.addAttribute("userEmail", user.getCorreo());
        model.addAttribute("userRole", user.getRol());

        return "conversations/index";
    }

    @GetMapping("/conversations/create")
    public String createConversation(Model model) {
        model.addAttribute("conversation", new Conversation());

        model.addAttribute("issueTypes", issueTypeRepository.findByActivoTrueOrderByNombreAsc());
        model.addAttribute("rejectionCodes", rejectionCodeRepository.findByActivoTrueOrderByCodigoAsc());
        model.addAttribute("departments", departmentRepository.findByActivoTrueOrderByNombreAsc());

        return "conversations/create";
    }

    @PostMapping("/conversations/save")
    public String saveConversation(
            @ModelAttribute Conversation conversation,
            Authentication authentication
    ) {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();

        conversation.setUserId(user.getId());
        conversation.setAgenteNombre(user.getNombreCompleto());

        if (!Boolean.TRUE.equals(conversation.getTicketAperturado())) {
            conversation.setTicketAperturado(false);
            conversation.setNumeroTicket(null);
        }

        if (!Boolean.TRUE.equals(conversation.getConversacionTransferida())) {
            conversation.setConversacionTransferida(false);
            conversation.setDepartmentId(null);
        }

        if (conversation.getIssueTypeId() == null) {
            conversation.setRejectionCodeId(null);
        }
        if (conversation.getIssueTypeId() != null) {
            IssueType issueType = issueTypeRepository
                    .findById(conversation.getIssueTypeId())
                    .orElseThrow(() -> new RuntimeException("Tipo de problema no encontrado"));

            conversation.setAsunto(issueType.getNombre());
        }

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

    @GetMapping("/conversations/edit/{id}")
    public String editConversation(@PathVariable Long id, Model model) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conversación no encontrada"));

        model.addAttribute("conversation", conversation);
        model.addAttribute("issueTypes", issueTypeRepository.findByActivoTrueOrderByNombreAsc());
        model.addAttribute("rejectionCodes", rejectionCodeRepository.findByActivoTrueOrderByCodigoAsc());
        model.addAttribute("departments", departmentRepository.findByActivoTrueOrderByNombreAsc());

        return "conversations/edit";
    }

    @PostMapping("/conversations/update/{id}")
    public String updateConversation(
            @PathVariable Long id,
            @ModelAttribute Conversation form
    ) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conversación no encontrada"));

        conversation.setClienteNombre(form.getClienteNombre());
        conversation.setClienteTelefono(form.getClienteTelefono());
        conversation.setClienteCorreo(form.getClienteCorreo());
        conversation.setChannelId(form.getChannelId());
        conversation.setIssueTypeId(form.getIssueTypeId());
        conversation.setRejectionCodeId(form.getRejectionCodeId());
        conversation.setDescripcion(form.getDescripcion());
        conversation.setStatusId(form.getStatusId());
        conversation.setPriorityId(form.getPriorityId());
        conversation.setTiempoGestionMinutos(form.getTiempoGestionMinutos());
        conversation.setObservaciones(form.getObservaciones());

        conversation.setTicketAperturado(Boolean.TRUE.equals(form.getTicketAperturado()));
        conversation.setNumeroTicket(Boolean.TRUE.equals(form.getTicketAperturado()) ? form.getNumeroTicket() : null);

        conversation.setConversacionTransferida(Boolean.TRUE.equals(form.getConversacionTransferida()));
        conversation.setDepartmentId(Boolean.TRUE.equals(form.getConversacionTransferida()) ? form.getDepartmentId() : null);

        conversationRepository.save(conversation);

        return "redirect:/conversations/" + id;
    }

    @GetMapping("/conversations/export/csv")
    public void exportCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=historico_conversaciones.csv");

        PrintWriter writer = response.getWriter();

        writer.println("Codigo,Cliente,Telefono,Correo,Asunto,Canal,Estado,Prioridad,Ticket Aperturado,Numero Ticket,Transferida,Departamento,Tiempo Gestion,Fecha Inicio,Observaciones");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        for (Conversation c : conversationRepository.findAll()) {
            writer.println(
                    safe(c.getCodigo()) + "," +
                            safe(c.getClienteNombre()) + "," +
                            safe(c.getClienteTelefono()) + "," +
                            safe(c.getClienteCorreo()) + "," +
                            safe(c.getAsunto()) + "," +
                            safe(channelName(c.getChannelId())) + "," +
                            safe(statusName(c.getStatusId())) + "," +
                            safe(priorityName(c.getPriorityId())) + "," +
                            safe(Boolean.TRUE.equals(c.getTicketAperturado()) ? "Sí" : "No") + "," +
                            safe(c.getNumeroTicket()) + "," +
                            safe(Boolean.TRUE.equals(c.getConversacionTransferida()) ? "Sí" : "No") + "," +
                            safe(departmentName(c.getDepartmentId())) + "," +
                            safe(String.valueOf(c.getTiempoGestionMinutos())) + "," +
                            safe(c.getFechaInicio() != null ? c.getFechaInicio().format(formatter) : "") + "," +
                            safe(c.getObservaciones())
            );
        }

        writer.flush();
    }

    private String safe(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String channelName(Long id) {
        if (id == null) return "";

        return switch (id.intValue()) {
            case 1 -> "WhatsApp";
            case 2 -> "Instagram";
            case 3 -> "Facebook";
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
            case 2 -> "Normal";
            case 3 -> "Alta";
            default -> "Desconocida";
        };
    }

    private String departmentName(Integer id) {
        if (id == null) return "";

        return switch (id) {
            case 1 -> "Liquidaciones";
            case 2 -> "Afiliaciones";
            case 3 -> "Ventas";
            case 4 -> "Capacitaciones";
            default -> "Desconocido";
        };
    }
}