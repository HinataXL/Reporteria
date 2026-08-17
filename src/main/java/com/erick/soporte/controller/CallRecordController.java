package com.erick.soporte.controller;

import com.erick.soporte.entity.CallRecord;
import com.erick.soporte.entity.User;
import com.erick.soporte.repository.CallRecordRepository;
import com.erick.soporte.repository.UserRepository;
import com.erick.soporte.security.CustomUserPrincipal;
import com.erick.soporte.service.AuditLogService;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
public class CallRecordController {

    private static final ZoneId APP_ZONE = ZoneId.of("America/Guatemala");
    private final CallRecordRepository callRecordRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public CallRecordController(
            CallRecordRepository callRecordRepository,
            UserRepository userRepository,
            AuditLogService auditLogService
    ) {
        this.callRecordRepository = callRecordRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/calls")
    public String index(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String resultado,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String agent,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String comercio,
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
        Specification<CallRecord> specification = callSpecification(user, q, tipo, resultado, estado, agent, cliente, comercio, from, to);

        Page<CallRecord> calls = callRecordRepository.findAll(specification, pageable);
        List<CallRecord> filteredCalls = callRecordRepository.findAll(specification);

        model.addAttribute("userName", user.getNombreCompleto());
        model.addAttribute("userRole", user.getRol());
        model.addAttribute("userEmail", user.getCorreo());
        model.addAttribute("calls", calls);
        model.addAttribute("totalMetric", filteredCalls.size());
        model.addAttribute("incomingMetric", countByType(filteredCalls, "Entrante"));
        model.addAttribute("outgoingMetric", countByType(filteredCalls, "Saliente"));
        model.addAttribute("followUpMetric", filteredCalls.stream().filter(c -> Boolean.TRUE.equals(c.getRequiereSeguimiento())).count());
        model.addAttribute("avgDurationMetric", filteredCalls.stream()
                .filter(c -> c.getDuracionMinutos() != null)
                .mapToInt(CallRecord::getDuracionMinutos)
                .average()
                .orElse(0));
        model.addAttribute("agents", userRepository.findAll().stream()
                .map(this::fullName)
                .filter(name -> !name.isBlank())
                .sorted(String::compareToIgnoreCase)
                .toList());
        model.addAttribute("q", q);
        model.addAttribute("tipo", tipo);
        model.addAttribute("resultado", resultado);
        model.addAttribute("estado", estado);
        model.addAttribute("agent", agent);
        model.addAttribute("cliente", cliente);
        model.addAttribute("comercio", comercio);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("reverseDir", "asc".equalsIgnoreCase(dir) ? "desc" : "asc");

        return "calls/index";
    }

    @GetMapping("/calls/create")
    public String create(
            @RequestParam(required = false, defaultValue = "false") boolean popup,
            @RequestParam(required = false, defaultValue = "false") boolean pip,
            Model model
    ) {
        CallRecord call = new CallRecord();
        call.setFechaInicio(LocalDateTime.now(APP_ZONE));
        call.setTipoLlamada("Entrante");
        call.setResultado("Atendida");
        call.setEstado("Registrada");
        model.addAttribute("call", call);
        model.addAttribute("popupMode", popup || pip);
        model.addAttribute("pipMode", pip);
        return "calls/create";
    }

    @PostMapping("/calls/save")
    public String save(
            @ModelAttribute CallRecord call,
            Authentication authentication,
            HttpServletRequest request
    ) {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();
        hydrateCall(call, user);
        CallRecord saved = callRecordRepository.save(call);

        if (saved.getCodigo() == null || saved.getCodigo().isBlank()) {
            saved.setCodigo("CALL-" + String.format("%05d", saved.getId()));
            saved = callRecordRepository.save(saved);
        }

        auditLogService.registrar(
                "CREAR_LLAMADA",
                "LLAMADAS",
                "Se registro la llamada " + saved.getCodigo(),
                authentication,
                request
        );

        return "redirect:/calls";
    }

    @GetMapping("/calls/{id}/edit")
    public String edit(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "false") boolean popup,
            @RequestParam(required = false, defaultValue = "false") boolean pip,
            Model model,
            Authentication authentication
    ) {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();
        CallRecord call = callRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Llamada no encontrada"));
        if (!canManageCalls(user) && !belongsToAgent(call, user)) {
            throw new RuntimeException("No tienes permisos para ver esta llamada");
        }

        model.addAttribute("call", call);
        model.addAttribute("popupMode", popup || pip);
        model.addAttribute("pipMode", pip);
        return "calls/create";
    }

    @PostMapping("/calls/update/{id}")
    public String update(
            @PathVariable Long id,
            @ModelAttribute CallRecord form,
            Authentication authentication,
            HttpServletRequest request
    ) {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();
        CallRecord call = callRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Llamada no encontrada"));
        if (!canManageCalls(user) && !belongsToAgent(call, user)) {
            throw new RuntimeException("No tienes permisos para editar esta llamada");
        }

        call.setClienteNombre(form.getClienteNombre());
        call.setNombreComercio(form.getNombreComercio());
        call.setClienteTelefono(form.getClienteTelefono());
        call.setClienteCorreo(form.getClienteCorreo());
        call.setTipoLlamada(form.getTipoLlamada());
        call.setResultado(form.getResultado());
        call.setAsunto(form.getAsunto());
        call.setDescripcion(form.getDescripcion());
        call.setObservaciones(form.getObservaciones());
        call.setFechaInicio(form.getFechaInicio());
        call.setFechaFin(form.getFechaFin());
        call.setDuracionMinutos(resolveDuration(form));
        call.setRequiereSeguimiento(Boolean.TRUE.equals(form.getRequiereSeguimiento()));
        call.setFechaSeguimiento(Boolean.TRUE.equals(form.getRequiereSeguimiento()) ? form.getFechaSeguimiento() : null);
        call.setEstado(resolveState(form));
        call.setUpdatedBy(user.getNombreCompleto());
        requireCallData(call);

        callRecordRepository.save(call);
        auditLogService.registrar("ACTUALIZAR_LLAMADA", "LLAMADAS", "Se actualizo la llamada " + call.getCodigo(), authentication, request);
        return "redirect:/calls";
    }

    @GetMapping("/calls/export/csv")
    public void exportCsv(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String resultado,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String agent,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String comercio,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Authentication authentication,
            HttpServletResponse response
    ) throws IOException {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();
        Specification<CallRecord> specification = callSpecification(user, q, tipo, resultado, estado, agent, cliente, comercio, from, to);
        List<CallRecord> calls = callRecordRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "fechaInicio"));
        writeCsv(response, calls);
    }

    private void hydrateCall(CallRecord call, CustomUserPrincipal user) {
        call.setUserId(user.getId());
        call.setAgenteNombre(user.getNombreCompleto());
        call.setCreatedBy(user.getNombreCompleto());
        call.setUpdatedBy(user.getNombreCompleto());
        call.setRequiereSeguimiento(Boolean.TRUE.equals(call.getRequiereSeguimiento()));
        if (!Boolean.TRUE.equals(call.getRequiereSeguimiento())) {
            call.setFechaSeguimiento(null);
        }
        call.setDuracionMinutos(resolveDuration(call));
        call.setEstado(resolveState(call));
        requireCallData(call);
    }

    private Integer resolveDuration(CallRecord call) {
        if (call.getFechaInicio() != null && call.getFechaFin() != null && !call.getFechaFin().isBefore(call.getFechaInicio())) {
            long minutes = Duration.between(call.getFechaInicio(), call.getFechaFin()).toMinutes();
            return Math.max(1, (int) minutes);
        }

        return call.getDuracionMinutos() != null ? Math.max(0, call.getDuracionMinutos()) : 0;
    }

    private String resolveState(CallRecord call) {
        if (Boolean.TRUE.equals(call.getRequiereSeguimiento())) {
            return "Seguimiento";
        }
        return call.getEstado() == null || call.getEstado().isBlank() ? "Registrada" : call.getEstado();
    }

    private void requireCallData(CallRecord call) {
        if (call.getClienteNombre() == null || call.getClienteNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del cliente es obligatorio.");
        }
        if (call.getClienteTelefono() == null || call.getClienteTelefono().trim().isEmpty()) {
            throw new IllegalArgumentException("El telefono es obligatorio.");
        }
        if (call.getTipoLlamada() == null || call.getTipoLlamada().trim().isEmpty()) {
            throw new IllegalArgumentException("El tipo de llamada es obligatorio.");
        }
        if (call.getResultado() == null || call.getResultado().trim().isEmpty()) {
            throw new IllegalArgumentException("El resultado es obligatorio.");
        }
        if (call.getAsunto() == null || call.getAsunto().trim().isEmpty()) {
            throw new IllegalArgumentException("El asunto es obligatorio.");
        }
        if (call.getFechaInicio() == null) {
            call.setFechaInicio(LocalDateTime.now(APP_ZONE));
        }
    }

    private Specification<CallRecord> callSpecification(
            CustomUserPrincipal user,
            String q,
            String tipo,
            String resultado,
            String estado,
            String agent,
            String cliente,
            String comercio,
            String from,
            String to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!canManageCalls(user)) {
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
                        cb.like(cb.lower(root.get("nombreComercio")), value),
                        cb.like(cb.lower(root.get("clienteTelefono")), value),
                        cb.like(cb.lower(root.get("asunto")), value),
                        cb.like(cb.lower(root.get("agenteNombre")), value)
                ));
            }

            if (tipo != null && !tipo.isBlank()) predicates.add(cb.equal(cb.lower(root.get("tipoLlamada")), tipo.toLowerCase()));
            if (resultado != null && !resultado.isBlank()) predicates.add(cb.equal(cb.lower(root.get("resultado")), resultado.toLowerCase()));
            if (estado != null && !estado.isBlank()) predicates.add(cb.equal(cb.lower(root.get("estado")), estado.toLowerCase()));
            if (agent != null && !agent.isBlank()) predicates.add(cb.like(cb.lower(root.get("agenteNombre")), "%" + agent.toLowerCase() + "%"));
            if (cliente != null && !cliente.isBlank()) predicates.add(cb.like(cb.lower(root.get("clienteNombre")), "%" + cliente.toLowerCase() + "%"));
            if (comercio != null && !comercio.isBlank()) predicates.add(cb.like(cb.lower(root.get("nombreComercio")), "%" + comercio.toLowerCase() + "%"));
            if (from != null && !from.isBlank()) predicates.add(cb.greaterThanOrEqualTo(root.get("fechaInicio"), LocalDate.parse(from).atStartOfDay()));
            if (to != null && !to.isBlank()) predicates.add(cb.lessThan(root.get("fechaInicio"), LocalDate.parse(to).plusDays(1).atStartOfDay()));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String sortProperty(String sort) {
        return switch (sort) {
            case "cliente" -> "clienteNombre";
            case "tipo" -> "tipoLlamada";
            case "resultado" -> "resultado";
            case "estado" -> "estado";
            case "agente" -> "agenteNombre";
            case "duracion" -> "duracionMinutos";
            case "fecha" -> "fechaInicio";
            default -> "id";
        };
    }

    private boolean canManageCalls(CustomUserPrincipal user) {
        String role = user.getRol();
        return "ADMIN".equalsIgnoreCase(role) || "SUPERVISOR".equalsIgnoreCase(role);
    }

    private boolean belongsToAgent(CallRecord call, CustomUserPrincipal user) {
        return (call.getUserId() != null && call.getUserId().equals(user.getId()))
                || (call.getAgenteNombre() != null && call.getAgenteNombre().equalsIgnoreCase(user.getNombreCompleto()));
    }

    private long countByType(List<CallRecord> calls, String type) {
        return calls.stream()
                .filter(call -> call.getTipoLlamada() != null && call.getTipoLlamada().equalsIgnoreCase(type))
                .count();
    }

    private String fullName(User user) {
        return ((user.getNombre() != null ? user.getNombre() : "") + " " + (user.getApellido() != null ? user.getApellido() : "")).trim();
    }

    private void writeCsv(HttpServletResponse response, List<CallRecord> calls) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=historico_llamadas.csv");

        PrintWriter writer = response.getWriter();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        writer.println("Codigo,Cliente,Nombre Comercio,Telefono,Correo,Tipo Llamada,Resultado,Asunto,Inicio,Fin,Duracion Minutos,Requiere Seguimiento,Fecha Seguimiento,Estado,Agente,Observaciones");

        for (CallRecord call : calls.stream().sorted(Comparator.comparing(CallRecord::getFechaInicio, Comparator.nullsLast(Comparator.naturalOrder())).reversed()).toList()) {
            writer.println(
                    safe(call.getCodigo()) + "," +
                            safe(call.getClienteNombre()) + "," +
                            safe(call.getNombreComercio()) + "," +
                            safe(call.getClienteTelefono()) + "," +
                            safe(call.getClienteCorreo()) + "," +
                            safe(call.getTipoLlamada()) + "," +
                            safe(call.getResultado()) + "," +
                            safe(call.getAsunto()) + "," +
                            safe(formatDate(call.getFechaInicio(), formatter)) + "," +
                            safe(formatDate(call.getFechaFin(), formatter)) + "," +
                            safe(call.getDuracionMinutos() != null ? String.valueOf(call.getDuracionMinutos()) : "0") + "," +
                            safe(Boolean.TRUE.equals(call.getRequiereSeguimiento()) ? "Si" : "No") + "," +
                            safe(formatDate(call.getFechaSeguimiento(), formatter)) + "," +
                            safe(call.getEstado()) + "," +
                            safe(call.getAgenteNombre()) + "," +
                            safe(call.getObservaciones())
            );
        }

        writer.flush();
    }

    private String formatDate(LocalDateTime value, DateTimeFormatter formatter) {
        return value != null ? value.format(formatter) : "";
    }

    private String safe(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
