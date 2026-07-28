package com.erick.soporte.controller;

import com.erick.soporte.entity.Conversation;
import com.erick.soporte.repository.ConversationRepository;
import com.erick.soporte.service.ActiveSessionService;
import com.erick.soporte.service.GeminiReportService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@RestController
public class DashboardApiController {

    private final ConversationRepository conversationRepository;
    private final ObjectProvider<GeminiReportService> geminiReportServiceProvider;
    private final ActiveSessionService activeSessionService;

    public DashboardApiController(
            ConversationRepository conversationRepository,
            ObjectProvider<GeminiReportService> geminiReportServiceProvider,
            ActiveSessionService activeSessionService
    ) {
        this.conversationRepository = conversationRepository;
        this.geminiReportServiceProvider = geminiReportServiceProvider;
        this.activeSessionService = activeSessionService;

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

        GeminiReportService geminiReportService = geminiReportServiceProvider.getIfAvailable();
        String reporte = geminiReportService != null
                ? geminiReportService.generateDashboardReport(
                total,
                pendientes,
                resueltas,
                escaladas,
                promedioTiempo
        )
                : "Analisis IA local temporal: Gemini no esta disponible en el contexto de la aplicacion.";

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("report", reporte);

        return response;
    }
    @GetMapping("/api/dashboard/metrics")
    public Map<String, Object> metrics(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "auto") String granularity
    ) {
        List<Conversation> allConversations = conversationRepository.findAll();
        List<Conversation> conversations = allConversations;

        if (from != null && !from.isBlank()) {
            LocalDate fromDate = LocalDate.parse(from);

            conversations = conversations.stream()
                    .filter(c -> c.getFechaInicio() != null &&
                            !c.getFechaInicio().toLocalDate().isBefore(fromDate))
                    .toList();
        }

        if (to != null && !to.isBlank()) {
            LocalDate toDate = LocalDate.parse(to);

            conversations = conversations.stream()
                    .filter(c -> c.getFechaInicio() != null &&
                            !c.getFechaInicio().toLocalDate().isAfter(toDate))
                    .toList();
        }

        long total = conversations.size();
        long pendientes = conversations.stream().filter(c -> c.getStatusId() != null && c.getStatusId() == 1).count();
        long resueltas = conversations.stream().filter(c -> c.getStatusId() != null && c.getStatusId() == 3).count();
        long escaladas = conversations.stream().filter(c -> c.getStatusId() != null && c.getStatusId() == 4).count();
        long cerradas = conversations.stream().filter(c -> c.getStatusId() != null && c.getStatusId() == 5).count();

        double promedioTiempo = conversations.stream()
                .filter(c -> c.getTiempoGestionMinutos() != null)
                .mapToInt(Conversation::getTiempoGestionMinutos)
                .average()
                .orElse(0);
        Map<String, Object> operationalHealth = calculateOperationalHealth(
                total,
                pendientes,
                escaladas,
                promedioTiempo,
                conversations
        );
        Map<String, Object> weeklyTrend = calculateWeeklyTrend(conversations);
        Map<String, Object> peakHours = calculatePeakHours(conversations);
        Map<String, Object> issueTrend = calculateIssueTrend(allConversations, from, to);

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

        String effectiveGranularity = resolveGranularity(granularity, conversations);
        Map<String, Long> productividadAgrupada = groupProductivity(conversations, effectiveGranularity);

        Map<String, Long> porAsunto = conversations.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getAsunto() != null ? c.getAsunto() : "Sin asunto",
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<String, Long> porCliente = conversations.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getClienteNombre() != null && !c.getClienteNombre().isBlank()
                                ? c.getClienteNombre()
                                : "Sin cliente",
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<String, Integer> tiempoGestionPorCliente = conversations.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getClienteNombre() != null && !c.getClienteNombre().isBlank()
                                ? c.getClienteNombre()
                                : "Sin cliente",
                        Collectors.summingInt(c -> c.getTiempoGestionMinutos() != null ? c.getTiempoGestionMinutos() : 0)
                ));

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        List<Map<String, Object>> recientes = conversations.stream()
                .sorted(Comparator.comparing(
                        Conversation::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .limit(5)
                .map(c -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("codigo", c.getCodigo());
                    item.put("cliente", c.getClienteNombre());
                    item.put("canal", channelName(c.getChannelId()));
                    item.put("estado", statusName(c.getStatusId()));
                    item.put("fecha", c.getFechaInicio() != null ? c.getFechaInicio().format(dateTimeFormatter) : "Sin fecha");
                    return item;
                })
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("total", total);
        response.put("pendientes", pendientes);
        response.put("resueltas", resueltas);
        response.put("escaladas", escaladas);
        response.put("cerradas", cerradas);
        response.put("promedioTiempo", String.format("%.1f", promedioTiempo));
        response.put("operationalHealth", operationalHealth);
        response.put("weeklyTrend", weeklyTrend);
        response.put("peakHours", peakHours);
        response.put("issueTrend", issueTrend);

        response.put("agenteLabels", porAgente.keySet());
        response.put("agenteValues", porAgente.values());

        response.put("canalLabels", porCanal.keySet());
        response.put("canalValues", porCanal.values());

        response.put("prioridadLabels", porPrioridad.keySet());
        response.put("prioridadValues", porPrioridad.values());

        response.put("productividadLabels", productividadAgrupada.keySet());
        response.put("productividadValues", productividadAgrupada.values());
        response.put("granularity", effectiveGranularity);
        response.put("asuntoLabels", porAsunto.keySet());
        response.put("asuntoValues", porAsunto.values());
        response.put("clienteLabels", porCliente.keySet());
        response.put("clienteValues", porCliente.values());
        response.put("clienteTiempoValues", porCliente.keySet().stream()
                .map(cliente -> tiempoGestionPorCliente.getOrDefault(cliente, 0))
                .toList());
        response.put("recientes", recientes);
        response.put("activeAgents", activeSessionService.activeAgents());

        return response;
    }

    @GetMapping("/api/dashboard/issue-trends")
    public Map<String, Object> issueTrends(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        List<Conversation> conversations = conversationRepository.findAll();
        Map<String, Object> issueTrend = calculateIssueTrend(conversations, from, to);
        GeminiReportService geminiReportService = geminiReportServiceProvider.getIfAvailable();
        issueTrend.put("analysis", geminiReportService != null
                ? geminiReportService.generateIssueTrendAnalysis(issueTrend)
                : "Aun no es posible consultar Gemini. Revisa la configuracion del servicio IA.");
        return issueTrend;
    }

    @GetMapping("/api/dashboard/client-360")
    public Map<String, Object> client360(@RequestParam String cliente) {
        List<Conversation> conversations = conversationRepository.findAll()
                .stream()
                .filter(c -> c.getClienteNombre() != null && c.getClienteNombre().equals(cliente))
                .toList();

        int tiempoTotal = conversations.stream()
                .mapToInt(c -> c.getTiempoGestionMinutos() != null ? c.getTiempoGestionMinutos() : 0)
                .sum();

        Map<String, Long> porEstado = conversations.stream()
                .collect(Collectors.groupingBy(
                        c -> statusName(c.getStatusId()),
                        Collectors.counting()
                ));

        Map<String, Long> topAsuntos = conversations.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getAsunto() != null ? c.getAsunto() : "Sin asunto",
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<String, Long> agentes = conversations.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getAgenteNombre() != null ? c.getAgenteNombre() : "Sin agente",
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        List<Map<String, Object>> recientes = conversations.stream()
                .sorted(Comparator.comparing(
                        c -> c.getFechaInicio() != null ? c.getFechaInicio() : LocalDateTime.MIN,
                        Comparator.reverseOrder()
                ))
                .limit(5)
                .map(c -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("codigo", c.getCodigo());
                    item.put("asunto", c.getAsunto());
                    item.put("estado", statusName(c.getStatusId()));
                    item.put("agente", c.getAgenteNombre());
                    item.put("tiempoGestion", c.getTiempoGestionMinutos() != null ? c.getTiempoGestionMinutos() : 0);
                    item.put("fecha", c.getFechaInicio() != null ? c.getFechaInicio().format(formatter) : "");
                    item.put("observaciones", c.getObservaciones());
                    return item;
                })
                .toList();

        Conversation latest = conversations.stream()
                .max(Comparator.comparing(
                        c -> c.getFechaInicio() != null ? c.getFechaInicio() : LocalDateTime.MIN
                ))
                .orElse(null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cliente", cliente);
        response.put("telefono", latest != null ? latest.getClienteTelefono() : "");
        response.put("correo", latest != null ? latest.getClienteCorreo() : "");
        response.put("totalConversaciones", conversations.size());
        response.put("tiempoTotalGestion", tiempoTotal);
        response.put("ultimaAtencion", latest != null && latest.getFechaInicio() != null ? latest.getFechaInicio().format(formatter) : "");
        response.put("ticketsAperturados", conversations.stream().filter(c -> Boolean.TRUE.equals(c.getTicketAperturado())).count());
        response.put("transferencias", conversations.stream().filter(c -> Boolean.TRUE.equals(c.getConversacionTransferida())).count());
        response.put("estados", porEstado);
        response.put("topAsuntos", topAsuntos);
        response.put("agentes", agentes);
        response.put("recientes", recientes);

        return response;
    }

    @GetMapping("/api/dashboard/peak-hour")
    public Map<String, Object> peakHourConversations(
            @RequestParam int hour,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        List<Conversation> conversations = conversationRepository.findAll()
                .stream()
                .filter(c -> c.getFechaInicio() != null)
                .filter(c -> c.getFechaInicio().getHour() == hour)
                .toList();

        if (from != null && !from.isBlank()) {
            LocalDate fromDate = LocalDate.parse(from);
            conversations = conversations.stream()
                    .filter(c -> !c.getFechaInicio().toLocalDate().isBefore(fromDate))
                    .toList();
        }

        if (to != null && !to.isBlank()) {
            LocalDate toDate = LocalDate.parse(to);
            conversations = conversations.stream()
                    .filter(c -> !c.getFechaInicio().toLocalDate().isAfter(toDate))
                    .toList();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        int totalManagementTime = conversations.stream()
                .mapToInt(c -> c.getTiempoGestionMinutos() != null ? c.getTiempoGestionMinutos() : 0)
                .sum();

        List<Map<String, Object>> items = conversations.stream()
                .sorted(Comparator.comparing(
                        c -> c.getFechaInicio() != null ? c.getFechaInicio() : LocalDateTime.MIN,
                        Comparator.reverseOrder()
                ))
                .map(c -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("codigo", c.getCodigo());
                    item.put("cliente", c.getClienteNombre() != null && !c.getClienteNombre().isBlank() ? c.getClienteNombre() : "Sin cliente");
                    item.put("asunto", c.getAsunto() != null && !c.getAsunto().isBlank() ? c.getAsunto() : "Sin asunto");
                    item.put("estado", statusName(c.getStatusId()));
                    item.put("agente", c.getAgenteNombre() != null && !c.getAgenteNombre().isBlank() ? c.getAgenteNombre() : "Sin agente");
                    item.put("canal", channelName(c.getChannelId()));
                    item.put("tiempoGestion", c.getTiempoGestionMinutos() != null ? c.getTiempoGestionMinutos() : 0);
                    item.put("fecha", c.getFechaInicio().format(formatter));
                    return item;
                })
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("hour", String.format("%02d:00", hour));
        response.put("nextHour", String.format("%02d:00", hour + 1));
        response.put("range", peakDateRange(conversations));
        response.put("totalConversaciones", conversations.size());
        response.put("tiempoTotalGestion", totalManagementTime);
        response.put("items", items);
        return response;
    }

    @GetMapping("/api/dashboard/status-conversations")
    public Map<String, Object> statusConversations(
            @RequestParam String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        Long statusId = dashboardStatusId(status);
        String label = dashboardStatusLabel(status);

        List<Conversation> conversations = conversationRepository.findAll()
                .stream()
                .filter(c -> statusId.equals(c.getStatusId()))
                .toList();

        if (from != null && !from.isBlank()) {
            LocalDate fromDate = LocalDate.parse(from);
            conversations = conversations.stream()
                    .filter(c -> c.getFechaInicio() != null)
                    .filter(c -> !c.getFechaInicio().toLocalDate().isBefore(fromDate))
                    .toList();
        }

        if (to != null && !to.isBlank()) {
            LocalDate toDate = LocalDate.parse(to);
            conversations = conversations.stream()
                    .filter(c -> c.getFechaInicio() != null)
                    .filter(c -> !c.getFechaInicio().toLocalDate().isAfter(toDate))
                    .toList();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        int totalManagementTime = conversations.stream()
                .mapToInt(c -> c.getTiempoGestionMinutos() != null ? c.getTiempoGestionMinutos() : 0)
                .sum();

        List<Map<String, Object>> items = conversations.stream()
                .sorted(Comparator.comparing(
                        c -> c.getFechaInicio() != null ? c.getFechaInicio() : LocalDateTime.MIN,
                        Comparator.reverseOrder()
                ))
                .map(c -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("codigo", c.getCodigo());
                    item.put("cliente", c.getClienteNombre() != null && !c.getClienteNombre().isBlank() ? c.getClienteNombre() : "Sin cliente");
                    item.put("asunto", c.getAsunto() != null && !c.getAsunto().isBlank() ? c.getAsunto() : "Sin asunto");
                    item.put("estado", statusName(c.getStatusId()));
                    item.put("agente", c.getAgenteNombre() != null && !c.getAgenteNombre().isBlank() ? c.getAgenteNombre() : "Sin agente");
                    item.put("canal", channelName(c.getChannelId()));
                    item.put("tiempoGestion", c.getTiempoGestionMinutos() != null ? c.getTiempoGestionMinutos() : 0);
                    item.put("fecha", c.getFechaInicio() != null ? c.getFechaInicio().format(formatter) : "Sin fecha");
                    return item;
                })
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", status);
        response.put("label", label);
        response.put("range", peakDateRange(conversations));
        response.put("totalConversaciones", conversations.size());
        response.put("tiempoTotalGestion", totalManagementTime);
        response.put("items", items);
        return response;
    }

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
            case 2 -> "Media";
            case 3 -> "Alta";
            case 4 -> "Crítica";
            default -> "Desconocida";
        };
    }

    private String statusName(Long id) {
        if (id == null) return "Desconocido";

        return switch (id.intValue()) {
            case 1 -> "Pendiente";
            case 2 -> "En Proceso";
            case 3 -> "Resuelto";
            case 4 -> "Escalado";
            case 5 -> "Cerrado";
            default -> "Desconocido";
        };
    }

    private Long dashboardStatusId(String status) {
        return switch (status) {
            case "pending" -> 1L;
            case "escalated" -> 4L;
            case "closed" -> 5L;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de dashboard no valido");
        };
    }

    private String dashboardStatusLabel(String status) {
        return switch (status) {
            case "pending" -> "Pendientes";
            case "escalated" -> "Escaladas";
            case "closed" -> "Cerradas";
            default -> "Conversaciones";
        };
    }

    private Map<String, Long> groupProductivity(List<Conversation> conversations, String granularity) {
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd/MM");
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("es"));

        Map<LocalDate, Long> grouped = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .collect(Collectors.groupingBy(
                        c -> bucketStart(c.getFechaInicio().toLocalDate(), granularity),
                        TreeMap::new,
                        Collectors.counting()
                ));

        return grouped.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        e -> formatBucket(e.getKey(), granularity, dayFormatter, monthFormatter),
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private LocalDate bucketStart(LocalDate date, String granularity) {
        return switch (granularity) {
            case "weekly" -> date.with(WeekFields.ISO.dayOfWeek(), 1);
            case "monthly" -> YearMonth.from(date).atDay(1);
            default -> date;
        };
    }

    private String formatBucket(
            LocalDate date,
            String granularity,
            DateTimeFormatter dayFormatter,
            DateTimeFormatter monthFormatter
    ) {
        return switch (granularity) {
            case "weekly" -> "Sem " + date.format(dayFormatter);
            case "monthly" -> monthFormatter.format(date);
            default -> date.format(dayFormatter);
        };
    }

    private String resolveGranularity(String requested, List<Conversation> conversations) {
        if ("daily".equals(requested) || "weekly".equals(requested) || "monthly".equals(requested)) {
            return requested;
        }

        List<LocalDate> dates = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .map(c -> c.getFechaInicio().toLocalDate())
                .toList();

        if (dates.isEmpty()) {
            return "daily";
        }

        LocalDate min = dates.stream().min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate max = dates.stream().max(LocalDate::compareTo).orElse(LocalDate.now());
        long days = ChronoUnit.DAYS.between(min, max) + 1;

        if (days > 120) {
            return "monthly";
        }

        if (days > 31) {
            return "weekly";
        }

        return "daily";
    }

    private Map<String, Object> calculateOperationalHealth(
            long total,
            long pendientes,
            long escaladas,
            double promedioTiempo,
            List<Conversation> conversations
    ) {
        double pendingRate = total > 0 ? (double) pendientes / total : 0;
        double escalationRate = total > 0 ? (double) escaladas / total : 0;
        double pendingPenalty = pendingRate * 35;
        double escalationPenalty = escalationRate * 25;
        double timePenalty = Math.min(25, Math.max(0, (promedioTiempo - 5) * 1.2));
        double trendPenalty = calculateTrendPenalty(conversations);

        int score = (int) Math.round(Math.max(0, Math.min(
                100,
                100 - pendingPenalty - escalationPenalty - timePenalty - trendPenalty
        )));

        String status = score >= 90 ? "stable" : score >= 70 ? "warning" : "critical";
        String label = switch (status) {
            case "stable" -> "Operacion estable";
            case "warning" -> "Operacion con alertas";
            default -> "Operacion critica";
        };

        String summary = switch (status) {
            case "stable" -> "La operacion se mantiene saludable; conviene sostener el ritmo de resolucion.";
            case "warning" -> "Hay senales que requieren seguimiento, principalmente carga pendiente o tiempos de atencion.";
            default -> "La operacion necesita intervencion prioritaria por carga, escalaciones o tiempos altos.";
        };

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("score", score);
        health.put("status", status);
        health.put("label", label);
        health.put("summary", summary);
        health.put("pendingRate", Math.round(pendingRate * 1000.0) / 10.0);
        health.put("escalationRate", Math.round(escalationRate * 1000.0) / 10.0);
        health.put("avgTime", String.format("%.1f", promedioTiempo));
        health.put("trend", trendPenalty > 0 ? "Carga abierta al alza" : "Carga abierta estable");
        return health;
    }

    private double calculateTrendPenalty(List<Conversation> conversations) {
        LocalDate anchor = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .map(c -> c.getFechaInicio().toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        LocalDate currentStart = anchor.minusDays(6);
        LocalDate previousStart = anchor.minusDays(13);

        long currentRisk = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .filter(c -> !c.getFechaInicio().toLocalDate().isBefore(currentStart))
                .filter(c -> !c.getFechaInicio().toLocalDate().isAfter(anchor))
                .filter(this::isOpenOrEscalated)
                .count();

        long previousRisk = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .filter(c -> !c.getFechaInicio().toLocalDate().isBefore(previousStart))
                .filter(c -> c.getFechaInicio().toLocalDate().isBefore(currentStart))
                .filter(this::isOpenOrEscalated)
                .count();

        if (previousRisk == 0) {
            return currentRisk > 0 ? 8 : 0;
        }

        double increase = (double) (currentRisk - previousRisk) / previousRisk;
        return Math.min(15, Math.max(0, increase * 15));
    }

    private boolean isOpenOrEscalated(Conversation conversation) {
        if (conversation.getStatusId() == null) {
            return false;
        }

        long statusId = conversation.getStatusId();
        return statusId == 1 || statusId == 2 || statusId == 4;
    }

    private Map<String, Object> calculateWeeklyTrend(List<Conversation> conversations) {
        LocalDate anchor = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .map(c -> c.getFechaInicio().toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        LocalDate currentStart = anchor.minusDays(6);
        LocalDate previousStart = anchor.minusDays(13);

        List<Conversation> current = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .filter(c -> !c.getFechaInicio().toLocalDate().isBefore(currentStart))
                .filter(c -> !c.getFechaInicio().toLocalDate().isAfter(anchor))
                .toList();

        List<Conversation> previous = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .filter(c -> !c.getFechaInicio().toLocalDate().isBefore(previousStart))
                .filter(c -> c.getFechaInicio().toLocalDate().isBefore(currentStart))
                .toList();

        long currentTotal = current.size();
        long previousTotal = previous.size();
        long currentPending = countStatuses(current, 1, 2);
        long previousPending = countStatuses(previous, 1, 2);
        long currentEscalated = countStatuses(current, 4);
        long previousEscalated = countStatuses(previous, 4);
        double currentAvgTime = averageManagementTime(current);
        double previousAvgTime = averageManagementTime(previous);

        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("range", currentStart.format(DateTimeFormatter.ofPattern("dd/MM")) + " - " + anchor.format(DateTimeFormatter.ofPattern("dd/MM")));
        trend.put("summary", weeklySummary(
                percentChange(currentTotal, previousTotal),
                percentChange(currentPending, previousPending),
                percentChange(currentEscalated, previousEscalated),
                percentChange(currentAvgTime, previousAvgTime)
        ));
        trend.put("volume", trendMetric(currentTotal, previousTotal, false));
        trend.put("pending", trendMetric(currentPending, previousPending, true));
        trend.put("escalated", trendMetric(currentEscalated, previousEscalated, true));
        trend.put("avgTime", trendMetric(currentAvgTime, previousAvgTime, true));
        trend.put("timeTrendLabel", timeTrendLabel(currentAvgTime, previousAvgTime));
        trend.put("timeTrendTone", percentChange(currentAvgTime, previousAvgTime) <= 0 ? "positive" : "negative");
        trend.put("timeTrendDescription", "vs semana anterior");
        trend.put("timeSeries", averageTimeByWeek(conversations, anchor));
        return trend;
    }

    private Map<String, Object> averageTimeByWeek(List<Conversation> conversations, LocalDate anchor) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
        LocalDate firstWeek = anchor.with(WeekFields.ISO.dayOfWeek(), 1).minusWeeks(5);

        Map<LocalDate, Double> byWeek = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .filter(c -> c.getTiempoGestionMinutos() != null)
                .filter(c -> !c.getFechaInicio().toLocalDate().isBefore(firstWeek))
                .filter(c -> !c.getFechaInicio().toLocalDate().isAfter(anchor))
                .collect(Collectors.groupingBy(
                        c -> c.getFechaInicio().toLocalDate().with(WeekFields.ISO.dayOfWeek(), 1),
                        TreeMap::new,
                        Collectors.averagingInt(Conversation::getTiempoGestionMinutos)
                ));

        List<LocalDate> weeks = java.util.stream.IntStream.rangeClosed(0, 5)
                .mapToObj(firstWeek::plusWeeks)
                .toList();

        Map<String, Object> series = new LinkedHashMap<>();
        series.put("labels", weeks.stream()
                .map(week -> "Sem " + week.format(formatter))
                .toList());
        series.put("values", weeks.stream()
                .map(week -> Math.round(byWeek.getOrDefault(week, 0.0) * 10.0) / 10.0)
                .toList());
        return series;
    }

    private String timeTrendLabel(double current, double previous) {
        if (previous == 0 && current == 0) {
            return "0.0%";
        }

        if (previous == 0) {
            return "Nuevo";
        }

        return formatSignedPercent(percentChange(current, previous));
    }

    private Map<String, Object> calculateIssueTrend(List<Conversation> conversations, LocalDate selectedAnchor) {
        LocalDate anchor = selectedAnchor != null
                ? selectedAnchor
                : conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .map(c -> c.getFechaInicio().toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        LocalDate currentStart = anchor.minusDays(6);
        LocalDate previousStart = anchor.minusDays(13);

        Map<String, Long> current = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .filter(c -> !c.getFechaInicio().toLocalDate().isBefore(currentStart))
                .filter(c -> !c.getFechaInicio().toLocalDate().isAfter(anchor))
                .collect(Collectors.groupingBy(this::issueName, Collectors.counting()));

        Map<String, Long> previous = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .filter(c -> !c.getFechaInicio().toLocalDate().isBefore(previousStart))
                .filter(c -> c.getFechaInicio().toLocalDate().isBefore(currentStart))
                .collect(Collectors.groupingBy(this::issueName, Collectors.counting()));

        List<Map<String, Object>> items = java.util.stream.Stream.concat(current.keySet().stream(), previous.keySet().stream())
                .distinct()
                .map(issue -> issueTrendItem(issue, current.getOrDefault(issue, 0L), previous.getOrDefault(issue, 0L)))
                .sorted(Comparator
                        .comparing((Map<String, Object> item) -> Math.abs((Long) item.get("delta"))).reversed()
                        .thenComparing(item -> (Long) item.get("current"), Comparator.reverseOrder()))
                .limit(10)
                .toList();

        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("range", currentStart.format(DateTimeFormatter.ofPattern("dd/MM")) + " - " + anchor.format(DateTimeFormatter.ofPattern("dd/MM")));
        trend.put("previousRange", previousStart.format(DateTimeFormatter.ofPattern("dd/MM")) + " - " + currentStart.minusDays(1).format(DateTimeFormatter.ofPattern("dd/MM")));
        trend.put("items", items);
        trend.put("summary", issueTrendSummary(items));
        return trend;
    }

    private Map<String, Object> calculateIssueTrend(List<Conversation> conversations, String from, String to) {
        LocalDate anchor = resolveTrendAnchor(conversations, from, to);
        LocalDate currentStart = from != null && !from.isBlank()
                ? LocalDate.parse(from)
                : anchor.minusDays(6);

        if (currentStart.isAfter(anchor)) {
            currentStart = anchor;
        }

        LocalDate currentPeriodStart = currentStart;
        LocalDate currentPeriodEnd = anchor;
        long periodDays = ChronoUnit.DAYS.between(currentPeriodStart, currentPeriodEnd) + 1;
        LocalDate previousStart = currentPeriodStart.minusDays(periodDays);
        LocalDate previousEnd = currentPeriodStart.minusDays(1);

        Map<String, Long> current = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .filter(c -> !c.getFechaInicio().toLocalDate().isBefore(currentPeriodStart))
                .filter(c -> !c.getFechaInicio().toLocalDate().isAfter(currentPeriodEnd))
                .collect(Collectors.groupingBy(this::issueName, Collectors.counting()));

        Map<String, Long> previous = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .filter(c -> !c.getFechaInicio().toLocalDate().isBefore(previousStart))
                .filter(c -> !c.getFechaInicio().toLocalDate().isAfter(previousEnd))
                .collect(Collectors.groupingBy(this::issueName, Collectors.counting()));

        List<Map<String, Object>> items = java.util.stream.Stream.concat(current.keySet().stream(), previous.keySet().stream())
                .distinct()
                .map(issue -> issueTrendItem(issue, current.getOrDefault(issue, 0L), previous.getOrDefault(issue, 0L)))
                .sorted(Comparator
                        .comparing((Map<String, Object> item) -> Math.abs((Long) item.get("delta"))).reversed()
                        .thenComparing(item -> (Long) item.get("current"), Comparator.reverseOrder()))
                .limit(10)
                .toList();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("range", currentPeriodStart.format(formatter) + " - " + currentPeriodEnd.format(formatter));
        trend.put("previousRange", previousStart.format(formatter) + " - " + previousEnd.format(formatter));
        trend.put("items", items);
        trend.put("summary", issueTrendSummary(items));
        return trend;
    }

    private Map<String, Object> issueTrendItem(String issue, long current, long previous) {
        long delta = current - previous;
        double change = percentChange(current, previous);
        String direction = delta > 0 ? "up" : delta < 0 ? "down" : "flat";
        String signal = previous == 0 && current > 0 ? "Nuevo esta semana" : formatSignedPercent(change);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("asunto", issue);
        item.put("current", current);
        item.put("previous", previous);
        item.put("delta", delta);
        item.put("change", signal);
        item.put("direction", direction);
        item.put("tone", delta > 0 ? "negative" : "positive");
        return item;
    }

    private String issueTrendSummary(List<Map<String, Object>> items) {
        if (items.isEmpty()) {
            return "Sin asuntos suficientes para comparar contra la semana anterior.";
        }

        Map<String, Object> top = items.get(0);
        return "Mayor variacion: " + top.get("asunto") + " (" + top.get("change") + ").";
    }

    private LocalDate resolveTrendAnchor(List<Conversation> conversations, String from, String to) {
        if (to != null && !to.isBlank()) {
            return LocalDate.parse(to);
        }

        if (from != null && !from.isBlank()) {
            LocalDate fromDate = LocalDate.parse(from);
            return conversations.stream()
                    .filter(c -> c.getFechaInicio() != null)
                    .map(c -> c.getFechaInicio().toLocalDate())
                    .filter(date -> !date.isBefore(fromDate))
                    .max(LocalDate::compareTo)
                    .orElse(fromDate);
        }

        return conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .map(c -> c.getFechaInicio().toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
    }

    private String issueName(Conversation conversation) {
        return conversation.getAsunto() != null && !conversation.getAsunto().isBlank()
                ? conversation.getAsunto()
                : "Sin asunto";
    }

    private Map<String, Object> trendMetric(double current, double previous, boolean lowerIsBetter) {
        double change = percentChange(current, previous);
        boolean improved = lowerIsBetter ? change <= 0 : change >= 0;

        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("current", formatTrendValue(current));
        metric.put("previous", formatTrendValue(previous));
        metric.put("change", formatSignedPercent(change));
        metric.put("direction", change > 0 ? "up" : change < 0 ? "down" : "flat");
        metric.put("tone", improved ? "positive" : "negative");
        return metric;
    }

    private String weeklySummary(double volumeChange, double pendingChange, double escalatedChange, double timeChange) {
        if (pendingChange > 10 || escalatedChange > 10 || timeChange > 10) {
            return "La semana muestra presion operativa; revisar carga pendiente, escalaciones o tiempos.";
        }

        if (volumeChange >= 10 && pendingChange <= 0) {
            return "La demanda crecio, pero la operacion mantiene controlada la carga abierta.";
        }

        if (pendingChange < 0 && escalatedChange <= 0 && timeChange <= 0) {
            return "La semana mejora frente al periodo anterior; baja la carga de riesgo.";
        }

        return "La semana se mantiene estable frente al periodo anterior.";
    }

    private long countStatuses(List<Conversation> conversations, long... statusIds) {
        return conversations.stream()
                .filter(c -> c.getStatusId() != null)
                .filter(c -> {
                    for (long statusId : statusIds) {
                        if (c.getStatusId() == statusId) {
                            return true;
                        }
                    }
                    return false;
                })
                .count();
    }

    private double averageManagementTime(List<Conversation> conversations) {
        return conversations.stream()
                .filter(c -> c.getTiempoGestionMinutos() != null)
                .mapToInt(Conversation::getTiempoGestionMinutos)
                .average()
                .orElse(0);
    }

    private double percentChange(double current, double previous) {
        if (previous == 0) {
            return current > 0 ? 100 : 0;
        }

        return ((current - previous) / previous) * 100;
    }

    private String formatSignedPercent(double value) {
        String sign = value > 0 ? "+" : "";
        return sign + String.format("%.1f", value) + "%";
    }

    private String formatTrendValue(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }

        return String.format("%.1f", value);
    }

    private Map<String, Object> calculatePeakHours(List<Conversation> conversations) {
        int startHour = 7;
        int endHour = 20;
        Map<Integer, Long> byHour = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .filter(c -> c.getFechaInicio().getHour() >= startHour && c.getFechaInicio().getHour() <= endHour)
                .collect(Collectors.groupingBy(
                        c -> c.getFechaInicio().getHour(),
                        TreeMap::new,
                        Collectors.counting()
                ));

        List<String> labels = java.util.stream.IntStream.rangeClosed(startHour, endHour)
                .mapToObj(hour -> String.format("%02d:00", hour))
                .toList();
        List<Long> values = java.util.stream.IntStream.rangeClosed(startHour, endHour)
                .mapToObj(hour -> byHour.getOrDefault(hour, 0L))
                .toList();

        long maxValue = values.stream().mapToLong(Long::longValue).max().orElse(0);
        int maxHour = startHour;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == maxValue) {
                maxHour = startHour + i;
                break;
            }
        }

        String dateRange = peakDateRange(conversations);
        Map<String, Object> peakHours = new LinkedHashMap<>();
        peakHours.put("labels", labels);
        peakHours.put("values", values);
        peakHours.put("maxValue", maxValue);
        peakHours.put("peakHour", String.format("%02d:00", maxHour));
        peakHours.put("range", dateRange);
        peakHours.put("businessHours", "07:00 - 20:00");
        peakHours.put("summary", maxValue > 0
                ? "Alta demanda dentro del horario de atencion, con pico a las " + String.format("%02d:00", maxHour) + " (" + maxValue + " conversaciones)."
                : "Sin conversaciones dentro del horario de atencion de 07:00 a 20:00.");
        return peakHours;
    }

    private String peakDateRange(List<Conversation> conversations) {
        List<LocalDate> dates = conversations.stream()
                .filter(c -> c.getFechaInicio() != null)
                .map(c -> c.getFechaInicio().toLocalDate())
                .toList();

        if (dates.isEmpty()) {
            return "Sin fechas registradas";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate min = dates.stream().min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate max = dates.stream().max(LocalDate::compareTo).orElse(LocalDate.now());
        return min.equals(max) ? min.format(formatter) : min.format(formatter) + " - " + max.format(formatter);
    }
}
