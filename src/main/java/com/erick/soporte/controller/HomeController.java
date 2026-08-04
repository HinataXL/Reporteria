package com.erick.soporte.controller;

import com.erick.soporte.entity.Conversation;
import com.erick.soporte.repository.ConversationRepository;
import com.erick.soporte.repository.DepartmentRepository;
import com.erick.soporte.repository.IssueTypeRepository;
import com.erick.soporte.repository.RejectionCodeRepository;
import com.erick.soporte.repository.UserRepository;
import com.erick.soporte.security.CustomUserPrincipal;
import com.erick.soporte.service.AuditLogService;
import com.erick.soporte.service.DashboardRealtimeService;
import com.erick.soporte.service.ZohoDeskClientService;
import com.erick.soporte.entity.IssueType;
import com.erick.soporte.entity.User;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final ConversationRepository conversationRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final RejectionCodeRepository rejectionCodeRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final DashboardRealtimeService dashboardRealtimeService;
    private final ZohoDeskClientService zohoDeskClientService;

    public HomeController(
            ConversationRepository conversationRepository,
            IssueTypeRepository issueTypeRepository,
            RejectionCodeRepository rejectionCodeRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            DashboardRealtimeService dashboardRealtimeService,
            ZohoDeskClientService zohoDeskClientService
    ) {
        this.conversationRepository = conversationRepository;
        this.issueTypeRepository = issueTypeRepository;
        this.rejectionCodeRepository = rejectionCodeRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.dashboardRealtimeService = dashboardRealtimeService;
        this.zohoDeskClientService = zohoDeskClientService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/conversations")
    public String conversations(

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long status,
            @RequestParam(required = false) Long priority,
            @RequestParam(required = false) Long channel,
            @RequestParam(required = false) String agent,
            @RequestParam(required = false) String asunto,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "fecha") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            Model model,
            Authentication authentication
    ) {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();
        int pageSize = List.of(10, 25, 50).contains(size) ? size : 10;
        Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(Math.max(page, 0), pageSize, Sort.by(direction, sortProperty(sort)));
        Specification<Conversation> specification = conversationSpecification(user, q, status, priority, channel, agent, asunto, cliente, from, to);

        model.addAttribute("userName", user.getNombreCompleto());
        model.addAttribute("userRole", user.getRol());
        model.addAttribute("userEmail", user.getCorreo());
        Page<Conversation> conversations = conversationRepository.findAll(specification, pageable);
        List<Conversation> filteredConversations = conversationRepository.findAll(specification);

        model.addAttribute("conversations", conversations);
        model.addAttribute("totalMetric", filteredConversations.size());
        model.addAttribute("pendingMetric", filteredConversations.stream().filter(c -> c.getStatusId() != null && (c.getStatusId() == 1 || c.getStatusId() == 2)).count());
        model.addAttribute("escalatedMetric", filteredConversations.stream().filter(c -> c.getStatusId() != null && c.getStatusId() == 4).count());
        model.addAttribute("closedMetric", filteredConversations.stream().filter(c -> c.getStatusId() != null && c.getStatusId() == 5).count());
        model.addAttribute("avgTimeMetric", filteredConversations.stream()
                .filter(c -> c.getTiempoGestionMinutos() != null)
                .mapToInt(Conversation::getTiempoGestionMinutos)
                .average()
                .orElse(0));
        model.addAttribute("agents", userRepository.findAll().stream()
                .map(this::fullName)
                .filter(name -> !name.isBlank())
                .sorted(String::compareToIgnoreCase)
                .toList());
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("priority", priority);
        model.addAttribute("channel", channel);
        model.addAttribute("agent", agent);
        model.addAttribute("asunto", asunto);
        model.addAttribute("cliente", cliente);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("reverseDir", "asc".equalsIgnoreCase(dir) ? "desc" : "asc");
        model.addAttribute("canManageConversations", canManageConversations(user));

        return "conversations/index";
    }

    @PostMapping("/conversations/bulk/status")
    public String bulkStatus(@RequestParam(required = false) List<Long> ids, @RequestParam Long statusId, Authentication authentication) {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();
        requireManager(user);
        if (ids == null || ids.isEmpty()) return "redirect:/conversations";
        List<Conversation> conversations = authorizedSelection(ids, user);
        conversations.forEach(conversation -> {
            conversation.setStatusId(statusId);
            if (statusId == 5) {
                conversation.setFechaFinalizacion(LocalDateTime.now(ZoneId.of("America/Guatemala")));
            }
        });
        conversationRepository.saveAll(conversations);
        conversations.forEach(c -> dashboardRealtimeService.publishConversationChanged("updated", c));
        return "redirect:/conversations";
    }

    @PostMapping("/conversations/bulk/priority")
    public String bulkPriority(@RequestParam(required = false) List<Long> ids, @RequestParam Long priorityId, Authentication authentication) {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();
        requireManager(user);
        if (ids == null || ids.isEmpty()) return "redirect:/conversations";
        List<Conversation> conversations = authorizedSelection(ids, user);
        conversations.forEach(conversation -> conversation.setPriorityId(priorityId));
        conversationRepository.saveAll(conversations);
        conversations.forEach(c -> dashboardRealtimeService.publishConversationChanged("updated", c));
        return "redirect:/conversations";
    }

    @PostMapping("/conversations/bulk/assign")
    public String bulkAssign(@RequestParam(required = false) List<Long> ids, @RequestParam String agentName, Authentication authentication) {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();
        requireManager(user);
        if (ids == null || ids.isEmpty() || agentName == null || agentName.isBlank()) return "redirect:/conversations";
        List<Conversation> conversations = authorizedSelection(ids, user);
        Long assignedUserId = userRepository.findAll().stream()
                .filter(agent -> fullName(agent).equalsIgnoreCase(agentName))
                .map(User::getId)
                .findFirst()
                .orElse(null);
        conversations.forEach(conversation -> {
            conversation.setAgenteNombre(agentName);
            conversation.setUserId(assignedUserId);
        });
        conversationRepository.saveAll(conversations);
        conversations.forEach(c -> dashboardRealtimeService.publishConversationChanged("updated", c));
        return "redirect:/conversations";
    }

    @PostMapping("/conversations/bulk/export")
    public void bulkExport(@RequestParam(required = false) List<Long> ids, Authentication authentication, HttpServletResponse response) throws IOException {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();
        requireManager(user);
        if (ids == null || ids.isEmpty()) {
            writeCsv(response, List.of(), "conversaciones_seleccionadas.csv");
            return;
        }
        writeCsv(response, authorizedSelection(ids, user), "conversaciones_seleccionadas.csv");
    }

    @GetMapping("/conversations/create")
    public String createConversation(Model model) {
        model.addAttribute("conversation", new Conversation());

        model.addAttribute("issueTypes", issueTypeRepository.findByActivoTrueOrderByNombreAsc());
        model.addAttribute("rejectionCodes", rejectionCodeRepository.findByActivoTrueOrderByCodigoAsc());
        model.addAttribute("departments", departmentRepository.findByActivoTrueOrderByNombreAsc());

        return "conversations/create";
    }

    @PostMapping("/api/conversations/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveConversationAjax(
            @ModelAttribute Conversation conversation,
            Authentication authentication,
            HttpServletRequest request
    ) {
        try {
            CustomUserPrincipal user =
                    (CustomUserPrincipal) authentication.getPrincipal();

            conversation.setUserId(user.getId());
            conversation.setAgenteNombre(user.getNombreCompleto());
            requireNombreComercio(conversation.getNombreComercio());

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
                        .orElseThrow(() ->
                                new RuntimeException("Tipo de problema no encontrado")
                        );

                conversation.setAsunto(issueType.getNombre());
            }

            requireDescriptionForDudasVarias(conversation.getAsunto(), conversation.getDescripcion());

            if (conversation.getFechaInicio() == null) {
                conversation.setFechaInicio(
                        LocalDateTime.now(
                                ZoneId.of("America/Guatemala")
                        )
                );
            }
            conversation.setFechaFinalizacion(resolveFinalizacion(conversation));

            Conversation saved = conversationRepository.save(conversation);

            if (saved.getCodigo() == null || saved.getCodigo().isBlank()) {
                saved.setCodigo(
                        "CONV-" + String.format("%05d", saved.getId())
                );

                saved = conversationRepository.save(saved);
            }

            auditLogService.registrar(
                    "CREAR_CONVERSACION",
                    "CONVERSACIONES",
                    "Se creó la conversación " + saved.getCodigo(),
                    authentication,
                    request
            );

            dashboardRealtimeService.publishConversationChanged("created", saved);

            Map<String, Object> response = new LinkedHashMap<>();

            response.put("success", true);
            response.put("id", saved.getId());
            response.put("codigo", saved.getCodigo());

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            e.printStackTrace();

            Map<String, Object> response = new LinkedHashMap<>();

            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity
                    .internalServerError()
                    .body(response);
        }
    }

    @GetMapping("/conversations/{id}")
    public String detailConversation(@PathVariable Long id, Model model, Authentication authentication) {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conversación no encontrada"));
        if (!canManageConversations(user) && !belongsToAgent(conversation, user)) {
            throw new RuntimeException("No tienes permisos para ver esta conversacion");
        }

        model.addAttribute("conversation", conversation);
        return "conversations/detail";
    }

    @PostMapping("/conversations/{id}/zoho-ticket")
    public String createZohoTicket(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest request,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes
    ) {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conversacion no encontrada"));

        if (!canManageConversations(user) && !belongsToAgent(conversation, user)) {
            throw new RuntimeException("No tienes permisos para crear ticket de esta conversacion");
        }

        try {
            Map<String, Object> ticket = zohoDeskClientService.createTicket(conversation);
            String ticketNumber = String.valueOf(ticket.getOrDefault("ticketNumber", ""));
            String ticketId = String.valueOf(ticket.getOrDefault("id", ""));
            String ticketUrl = String.valueOf(ticket.getOrDefault("webUrl", ""));
            String zohoContactId = String.valueOf(ticket.getOrDefault("zohoContactId", ""));

            conversation.setTicketAperturado(true);
            conversation.setNumeroTicket(!ticketNumber.isBlank() ? ticketNumber : ticketId);
            conversation.setZohoTicketId(ticketId);
            conversation.setZohoTicketUrl(ticketUrl.isBlank() ? null : ticketUrl);
            conversation.setZohoContactId(zohoContactId.isBlank() ? null : zohoContactId);
            conversation.setZohoTicketCreatedAt(LocalDateTime.now(ZoneId.of("America/Guatemala")));
            conversationRepository.save(conversation);

            auditLogService.registrar(
                    "CREAR_TICKET_ZOHO",
                    "ZOHO",
                    "Se creo ticket Zoho " + conversation.getNumeroTicket() + " para " + conversation.getCodigo(),
                    authentication,
                    request
            );
            dashboardRealtimeService.publishConversationChanged("updated", conversation);
            redirectAttributes.addFlashAttribute("success", "Ticket Zoho creado: " + conversation.getNumeroTicket());
        } catch (Exception error) {
            redirectAttributes.addFlashAttribute("error", error.getMessage());
        }

        return "redirect:/conversations/" + id;
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
        conversation.setNombreComercio(form.getNombreComercio());
        requireNombreComercio(form.getNombreComercio());
        conversation.setChannelId(form.getChannelId());
        conversation.setAsunto(form.getAsunto());
        requireDescriptionForDudasVarias(conversation.getAsunto(), form.getDescripcion());
        conversation.setIssueTypeId(form.getIssueTypeId());
        conversation.setRejectionCodeId(form.getRejectionCodeId());
        conversation.setDescripcion(form.getDescripcion());
        conversation.setStatusId(form.getStatusId());
        conversation.setPriorityId(form.getPriorityId());
        conversation.setTiempoGestionMinutos(form.getTiempoGestionMinutos());
        conversation.setFechaFinalizacion(resolveFinalizacion(conversation));
        conversation.setObservaciones(form.getObservaciones());

        conversation.setTicketAperturado(Boolean.TRUE.equals(form.getTicketAperturado()));
        conversation.setNumeroTicket(Boolean.TRUE.equals(form.getTicketAperturado()) ? form.getNumeroTicket() : null);

        conversation.setConversacionTransferida(Boolean.TRUE.equals(form.getConversacionTransferida()));
        conversation.setDepartmentId(Boolean.TRUE.equals(form.getConversacionTransferida()) ? form.getDepartmentId() : null);

        Conversation saved = conversationRepository.save(conversation);
        dashboardRealtimeService.publishConversationChanged("updated", saved);

        return "redirect:/conversations/" + id;
    }

    @GetMapping("/conversations/export/csv")
    public void exportCsv(HttpServletResponse response, Authentication authentication) throws IOException {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();
        writeCsv(response, authorizedSelection(conversationRepository.findAll().stream().map(Conversation::getId).toList(), user), "historico_conversaciones.csv");
    }

    private void writeCsv(HttpServletResponse response, List<Conversation> conversations, String filename) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);

        PrintWriter writer = response.getWriter();

        writer.println("Cliente,Telefono,Asunto,Fecha Inicio,Fecha Guardado,Observaciones");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        for (Conversation c : conversations) {
            writer.println(
                    safe(c.getClienteNombre()) + "," +
                            safe(c.getClienteTelefono()) + "," +
                            safe(c.getAsunto()) + "," +
                            safe(c.getFechaInicio() != null ? c.getFechaInicio().format(formatter) : "") + "," +
                            safe(finalizacionCsv(c, formatter)) + "," +
                            safe(c.getObservaciones())
            );
        }

        writer.flush();
    }

    private Specification<Conversation> conversationSpecification(
            CustomUserPrincipal user,
            String q,
            Long status,
            Long priority,
            Long channel,
            String agent,
            String asunto,
            String cliente,
            String from,
            String to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!canManageConversations(user)) {
                predicates.add(cb.or(
                        cb.equal(root.get("userId"), user.getId()),
                        cb.equal(cb.lower(root.get("agenteNombre")), user.getNombreCompleto().toLowerCase())
                ));
            }

            if (q != null && !q.isBlank()) {
                String value = "%" + q.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("codigo")), value),
                        cb.like(cb.lower(root.get("clienteNombre")), value),
                        cb.like(cb.lower(root.get("asunto")), value),
                        cb.like(cb.lower(root.get("agenteNombre")), value)
                ));
            }
            if (status != null) predicates.add(cb.equal(root.get("statusId"), status));
            if (priority != null) predicates.add(cb.equal(root.get("priorityId"), priority));
            if (channel != null) predicates.add(cb.equal(root.get("channelId"), channel));
            if (agent != null && !agent.isBlank()) predicates.add(cb.like(cb.lower(root.get("agenteNombre")), "%" + agent.toLowerCase() + "%"));
            if (asunto != null && !asunto.isBlank()) predicates.add(cb.like(cb.lower(root.get("asunto")), "%" + asunto.toLowerCase() + "%"));
            if (cliente != null && !cliente.isBlank()) predicates.add(cb.like(cb.lower(root.get("clienteNombre")), "%" + cliente.toLowerCase() + "%"));
            if (from != null && !from.isBlank()) predicates.add(cb.greaterThanOrEqualTo(root.get("fechaInicio"), LocalDate.parse(from).atStartOfDay()));
            if (to != null && !to.isBlank()) predicates.add(cb.lessThan(root.get("fechaInicio"), LocalDate.parse(to).plusDays(1).atStartOfDay()));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String sortProperty(String sort) {
        return switch (sort) {
            case "cliente" -> "clienteNombre";
            case "estado" -> "statusId";
            case "prioridad" -> "priorityId";
            case "agente" -> "agenteNombre";
            case "fecha" -> "fechaInicio";
            default -> "id";
        };
    }

    private boolean canManageConversations(CustomUserPrincipal user) {
        String role = user.getRol();
        return "ADMIN".equalsIgnoreCase(role) || "SUPERVISOR".equalsIgnoreCase(role);
    }

    private void requireManager(CustomUserPrincipal user) {
        if (!canManageConversations(user)) {
            throw new RuntimeException("No tienes permisos para esta accion");
        }
    }

    private List<Conversation> authorizedSelection(List<Long> ids, CustomUserPrincipal user) {
        return conversationRepository.findAllById(ids).stream()
                .filter(conversation -> canManageConversations(user) || belongsToAgent(conversation, user))
                .sorted(Comparator.comparing(Conversation::getId, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    private boolean belongsToAgent(Conversation conversation, CustomUserPrincipal user) {
        return (conversation.getUserId() != null && conversation.getUserId().equals(user.getId()))
                || (conversation.getAgenteNombre() != null && conversation.getAgenteNombre().equalsIgnoreCase(user.getNombreCompleto()));
    }

    private String fullName(User user) {
        return ((user.getNombre() != null ? user.getNombre() : "") + " " + (user.getApellido() != null ? user.getApellido() : "")).trim();
    }

    private String safe(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void requireDescriptionForDudasVarias(String asunto, String descripcion) {
        if ("dudas varias".equals(normalizedText(asunto)) && (descripcion == null || descripcion.trim().isEmpty())) {
            throw new IllegalArgumentException("Para Dudas varias, la descripcion de la conversacion es obligatoria.");
        }
    }

    private void requireNombreComercio(String nombreComercio) {
        if (nombreComercio == null || nombreComercio.trim().isEmpty()) {
            throw new IllegalArgumentException("Has olvidado colocar nombre comercio, porfavor coloca un nombre para guardar");
        }
    }

    private String normalizedText(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase();
    }

    private String finalizacionCsv(Conversation conversation, DateTimeFormatter formatter) {
        LocalDateTime finalizacion = resolveFinalizacion(conversation);
        if (finalizacion != null) {
            return finalizacion.format(formatter);
        }

        return "";
    }

    private LocalDateTime resolveFinalizacion(Conversation conversation) {
        if (conversation.getFechaInicio() != null && conversation.getTiempoGestionMinutos() != null && conversation.getTiempoGestionMinutos() > 0) {
            return conversation.getFechaInicio().plusMinutes(conversation.getTiempoGestionMinutos());
        }

        if (conversation.getFechaFinalizacion() != null) {
            return conversation.getFechaFinalizacion();
        }

        return conversation.getFechaInicio();
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



    @GetMapping("/supervisor/dashboard-v2")
    public String dashboardSneat(Model model) {
        model.addAttribute("pageTitle", "Dashboard Supervisor");
        model.addAttribute("userName", "Erick Pedroza");
        model.addAttribute("userRole", "ADMIN");
        model.addAttribute("content", "supervisor/dashboard-content :: content");

        return "layout/sneat";
    }


}
