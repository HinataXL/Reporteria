package com.erick.soporte.controller;

import com.erick.soporte.service.ZohoCrmTaskMetricsService;
import com.erick.soporte.security.CustomUserPrincipal;
import com.erick.soporte.service.CrmRefreshPermissionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class ZohoCrmTaskController {

    private final ZohoCrmTaskMetricsService taskMetricsService;
    private final CrmRefreshPermissionService refreshPermissionService;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public ZohoCrmTaskController(
            ZohoCrmTaskMetricsService taskMetricsService,
            CrmRefreshPermissionService refreshPermissionService
    ) {
        this.taskMetricsService = taskMetricsService;
        this.refreshPermissionService = refreshPermissionService;
    }

    @GetMapping("/admin/zoho-crm/tasks")
    public String tasks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String operationCountry,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "25") Integer size,
            @RequestParam(required = false, defaultValue = "false") boolean refreshCrm,
            Model model
    ) {
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(7);
        int pageSize = size != null && List.of(10, 25, 50).contains(size) ? size : 25;

        model.addAttribute("from", effectiveFrom);
        model.addAttribute("to", effectiveTo);
        model.addAttribute("taskType", taskType);
        model.addAttribute("operationCountry", operationCountry);
        model.addAttribute("owner", owner);
        model.addAttribute("status", status);
        model.addAttribute("size", pageSize);
        model.addAttribute("refreshCrm", refreshCrm);

        Map<String, Object> crmStatus = taskMetricsService.status();
        model.addAttribute("crmConfigured", Boolean.TRUE.equals(crmStatus.get("configured")));
        model.addAttribute("crmBaseUrl", valueOrFallback(crmStatus.get("baseUrl"), "No configurado"));
        model.addAttribute("crmAccountsUrl", valueOrFallback(crmStatus.get("accountsUrl"), "No configurado"));
        model.addAttribute("crmTasksModule", valueOrFallback(crmStatus.get("tasksModule"), "Tasks"));
        model.addAttribute("crmCompletedStatuses", valueOrFallback(crmStatus.get("completedStatuses"), "Sin configurar"));
        model.addAttribute("assignedTotal", 0);
        model.addAttribute("completedTotal", 0);
        model.addAttribute("pendingTotal", 0);
        model.addAttribute("complianceText", "0.0%");
        model.addAttribute("agentRows", List.of());
        model.addAttribute("taskRows", List.of());
        model.addAttribute("typeRows", List.of());
        model.addAttribute("countryRows", List.of());
        model.addAttribute("taskTypeOptions", List.of());
        model.addAttribute("countryOptions", List.of());
        model.addAttribute("ownerOptions", List.of());
        model.addAttribute("statusOptions", List.of());
        model.addAttribute("alertCards", List.of());

        try {
            ZohoCrmTaskMetricsService.TaskFilter filter = new ZohoCrmTaskMetricsService.TaskFilter(
                    taskType,
                    operationCountry,
                    owner,
                    status,
                    pageSize,
                    refreshCrm
            );
            ZohoCrmTaskMetricsService.TaskMetrics metrics = refreshCrm
                    ? taskMetricsService.metrics(effectiveFrom, effectiveTo, filter)
                    : taskMetricsService.cachedMetrics(effectiveFrom, effectiveTo, filter);
            model.addAttribute("assignedTotal", metrics.getAssigned());
            model.addAttribute("completedTotal", metrics.getCompleted());
            model.addAttribute("pendingTotal", metrics.getPending());
            model.addAttribute("complianceText", percent(metrics.getCompliance()));
            model.addAttribute("agentRows", metrics.getAgents().stream()
                    .map(agent -> Map.of(
                            "owner", agent.getOwner(),
                            "assigned", agent.getAssigned(),
                            "completed", agent.getCompleted(),
                            "pending", agent.getPending(),
                            "complianceText", percent(agent.getCompliance()),
                            "complianceWidth", Math.max(0, Math.min(100, Math.round(agent.getCompliance()))),
                            "statusesText", statusesText(agent.getStatuses())
                    ))
                    .toList());
            model.addAttribute("taskRows", metrics.getTasks().stream()
                    .limit(pageSize)
                    .map(task -> Map.of(
                            "subject", valueOrFallback(task.subject(), "Sin asunto"),
                            "type", valueOrFallback(task.taskType(), "Sin tipo"),
                            "country", valueOrFallback(task.operationCountry(), "Sin pais"),
                            "status", valueOrFallback(task.status(), "Sin estado"),
                            "owner", valueOrFallback(task.ownerName(), "Sin propietario"),
                            "dueDate", formatDate(task.dueDate()),
                            "createdTime", formatDate(task.createdTime()),
                            "modifiedTime", formatDate(task.modifiedTime())
                    ))
                    .toList());
            model.addAttribute("typeRows", chartRows(metrics.getByType()));
            model.addAttribute("countryRows", chartRows(metrics.getByCountry()));
            model.addAttribute("taskTypeOptions", metrics.getTaskTypeOptions());
            model.addAttribute("countryOptions", metrics.getCountryOptions());
            model.addAttribute("ownerOptions", metrics.getOwnerOptions());
            model.addAttribute("statusOptions", metrics.getStatusOptions());
            model.addAttribute("alertCards", alertCards(metrics, effectiveFrom, effectiveTo, filter));
        } catch (Exception error) {
            model.addAttribute("error", error.getMessage());
        }

        return "admin/zoho-crm-tasks";
    }

    @GetMapping("/admin/zoho-crm/tasks/dashboard")
    public String taskDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String operationCountry,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false, defaultValue = "false") boolean refreshCrm,
            Model model
    ) {
        tasks(from, to, taskType, operationCountry, owner, status, size, refreshCrm, model);
        model.addAttribute("dashboardBeta", true);
        model.addAttribute("crmRefreshRequests", refreshPermissionService.pendingRequests());
        return "admin/zoho-crm-tasks-dashboard";
    }

    @PostMapping("/admin/zoho-crm/tasks/refresh-requests/{id}/approve")
    public String approveRefreshRequest(@PathVariable long id, RedirectAttributes redirectAttributes) {
        if (refreshPermissionService.approve(id)) {
            redirectAttributes.addFlashAttribute("success", "Solicitud CRM autorizada para el agente.");
        } else {
            redirectAttributes.addFlashAttribute("error", "No fue posible autorizar la solicitud CRM.");
        }
        return "redirect:/admin/zoho-crm/tasks/dashboard";
    }

    @PostMapping("/admin/zoho-crm/tasks/refresh-requests/{id}/reject")
    public String rejectRefreshRequest(@PathVariable long id, RedirectAttributes redirectAttributes) {
        if (refreshPermissionService.reject(id)) {
            redirectAttributes.addFlashAttribute("success", "Solicitud CRM rechazada.");
        } else {
            redirectAttributes.addFlashAttribute("error", "No fue posible rechazar la solicitud CRM.");
        }
        return "redirect:/admin/zoho-crm/tasks/dashboard";
    }

    @GetMapping("/agent/zoho-crm/tasks/dashboard")
    public String agentTaskDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String operationCountry,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false, defaultValue = "false") boolean refreshCrm,
            @RequestParam(required = false) String crmPermissionStatus,
            Authentication authentication,
            Model model
    ) {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();
        String agentOwner = user.getNombreCompleto();
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(7);
        int pageSize = size != null && List.of(10, 25, 50).contains(size) ? size : 10;
        boolean allowedRefresh = refreshCrm && refreshPermissionService.consumeApproved(
                agentOwner,
                effectiveFrom,
                effectiveTo,
                taskType,
                operationCountry,
                status,
                pageSize
        );
        if (refreshCrm && !allowedRefresh) {
            model.addAttribute("crmPermissionStatus", "denied");
        } else if (allowedRefresh) {
            model.addAttribute("crmPermissionStatus", "approved");
        } else if (crmPermissionStatus != null && !crmPermissionStatus.isBlank()) {
            model.addAttribute("crmPermissionStatus", crmPermissionStatus);
        }
        tasks(effectiveFrom, effectiveTo, taskType, operationCountry, agentOwner, status, pageSize, allowedRefresh, model);
        model.addAttribute("userName", user.getNombreCompleto());
        model.addAttribute("userRole", user.getRol());
        model.addAttribute("userEmail", user.getCorreo());
        model.addAttribute("agentOwner", agentOwner);
        model.addAttribute("owner", agentOwner);
        model.addAttribute("agentCrmDashboard", true);
        model.addAttribute("crmRefreshApproved", refreshPermissionService.hasApproved(
                agentOwner,
                effectiveFrom,
                effectiveTo,
                taskType,
                operationCountry,
                status,
                pageSize
        ));
        return "agent/zoho-crm-tasks-dashboard";
    }

    @PostMapping("/agent/zoho-crm/tasks/refresh-request")
    public String requestAgentCrmRefresh(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String operationCountry,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            Authentication authentication
    ) {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(7);
        int pageSize = size != null && List.of(10, 25, 50).contains(size) ? size : 10;
        refreshPermissionService.request(
                user.getNombreCompleto(),
                user.getCorreo(),
                effectiveFrom,
                effectiveTo,
                taskType,
                operationCountry,
                status,
                pageSize
        );
        return "redirect:" + agentDashboardUrl(
                effectiveFrom,
                effectiveTo,
                taskType,
                operationCountry,
                status,
                pageSize,
                "requested",
                false
        );
    }

    private String agentDashboardUrl(
            LocalDate from,
            LocalDate to,
            String taskType,
            String operationCountry,
            String status,
            int size,
            String permissionStatus,
            boolean refreshCrm
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/agent/zoho-crm/tasks/dashboard")
                .queryParam("from", from)
                .queryParam("to", to)
                .queryParam("size", size)
                .queryParam("crmPermissionStatus", permissionStatus);
        if (refreshCrm) {
            builder.queryParam("refreshCrm", "true");
        }
        if (taskType != null && !taskType.isBlank()) {
            builder.queryParam("taskType", taskType);
        }
        if (operationCountry != null && !operationCountry.isBlank()) {
            builder.queryParam("operationCountry", operationCountry);
        }
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }
        return builder.build().toUriString();
    }

    private String percent(double value) {
        return String.format(Locale.US, "%.1f%%", value);
    }

    private String valueOrFallback(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private String formatDate(OffsetDateTime value) {
        return value != null ? value.format(DATE_TIME) : "Sin fecha";
    }

    private List<Map<String, Object>> chartRows(Map<String, Long> values) {
        long max = values != null
                ? values.values().stream().mapToLong(Long::longValue).max().orElse(0)
                : 0;
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("label", entry.getKey());
                    row.put("value", entry.getValue());
                    row.put("width", max > 0 ? Math.max(8, Math.round((entry.getValue() * 100.0) / max)) : 0);
                    return row;
                })
                .toList();
    }

    private String statusesText(Map<String, Long> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return "Sin estados";
        }
        return statuses.entrySet().stream()
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .collect(Collectors.joining(", "));
    }

    private List<Map<String, Object>> alertCards(
            ZohoCrmTaskMetricsService.TaskMetrics metrics,
            LocalDate from,
            LocalDate to,
            ZohoCrmTaskMetricsService.TaskFilter filter
    ) {
        List<Map<String, Object>> alerts = new java.util.ArrayList<>();
        alerts.add(alert(
                "Agentes con mas pendientes",
                topPendingAgent(metrics),
                "Carga pendiente por propietario",
                "users",
                "amber"
        ));
        alerts.add(alert(
                "Pais con mayor atraso",
                topPendingCountry(metrics),
                "Pendientes por pais de operacion",
                "map-pin",
                "rose"
        ));
        alerts.add(alert(
                "Tipo con menor cumplimiento",
                lowestComplianceType(metrics),
                "Realizadas vs asignadas",
                "trending-down",
                "violet"
        ));
        alerts.add(alert(
                "Incremento fuerte vs semana anterior",
                strongWeeklyIncrease(metrics, from, to, filter),
                "Comparacion contra rango anterior cacheado",
                "activity",
                "blue"
        ));
        return alerts;
    }

    private Map<String, Object> alert(String title, String value, String detail, String icon, String tone) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("value", value);
        row.put("detail", detail);
        row.put("icon", icon);
        row.put("tone", tone);
        return row;
    }

    private String topPendingAgent(ZohoCrmTaskMetricsService.TaskMetrics metrics) {
        return metrics.getAgents().stream()
                .filter(agent -> agent.getPending() > 0)
                .max(java.util.Comparator.comparingInt(ZohoCrmTaskMetricsService.AgentTaskMetric::getPending))
                .map(agent -> agent.getOwner() + " - " + agent.getPending() + " pendiente(s)")
                .orElse("Sin pendientes por agente");
    }

    private String topPendingCountry(ZohoCrmTaskMetricsService.TaskMetrics metrics) {
        Map<String, Long> pendingByCountry = pendingBy(metrics, ZohoCrmTaskMetricsService.CrmTask::operationCountry, "Sin pais");
        return pendingByCountry.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> entry.getKey() + " - " + entry.getValue() + " pendiente(s)")
                .orElse("Sin atraso por pais");
    }

    private String lowestComplianceType(ZohoCrmTaskMetricsService.TaskMetrics metrics) {
        Set<String> completed = completedStatuses();
        return metrics.getTasks().stream()
                .collect(Collectors.groupingBy(
                        task -> valueOrFallback(task.taskType(), "Sin tipo"),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .entrySet()
                .stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> {
                    long completedCount = entry.getValue().stream()
                            .filter(task -> completed.contains(normalize(task.status())))
                            .count();
                    double compliance = (completedCount * 100.0) / entry.getValue().size();
                    return Map.entry(entry.getKey(), compliance);
                })
                .min(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey() + " - " + percent(entry.getValue()))
                .orElse("Sin tipos suficientes");
    }

    private String strongWeeklyIncrease(
            ZohoCrmTaskMetricsService.TaskMetrics metrics,
            LocalDate from,
            LocalDate to,
            ZohoCrmTaskMetricsService.TaskFilter filter
    ) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(Math.max(0, days - 1));
        try {
            ZohoCrmTaskMetricsService.TaskMetrics previous = taskMetricsService.cachedMetrics(previousFrom, previousTo, filter);
            int currentAssigned = metrics.getAssigned();
            int previousAssigned = previous.getAssigned();
            if (previousAssigned == 0 && currentAssigned > 0) {
                return "+" + currentAssigned + " tarea(s) vs rango anterior sin actividad";
            }
            if (previousAssigned == 0) {
                return "Sin incremento detectado";
            }
            double variation = ((currentAssigned - previousAssigned) * 100.0) / previousAssigned;
            if (variation >= 25) {
                return "+" + percent(variation) + " en tareas asignadas";
            }
            return percent(variation) + " vs semana anterior";
        } catch (Exception ignored) {
            return "Sin cache de la semana anterior";
        }
    }

    private Map<String, Long> pendingBy(
            ZohoCrmTaskMetricsService.TaskMetrics metrics,
            java.util.function.Function<ZohoCrmTaskMetricsService.CrmTask, String> mapper,
            String fallback
    ) {
        Set<String> completed = completedStatuses();
        return metrics.getTasks().stream()
                .filter(task -> !completed.contains(normalize(task.status())))
                .collect(Collectors.groupingBy(
                        task -> valueOrFallback(mapper.apply(task), fallback),
                        Collectors.counting()
                ));
    }

    private Set<String> completedStatuses() {
        Object value = taskMetricsService.status().get("completedStatuses");
        if (value instanceof Set<?> set) {
            return set.stream().map(String::valueOf).map(this::normalize).collect(Collectors.toSet());
        }
        return Set.of();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("Ã¡", "a")
                .replace("Ã©", "e")
                .replace("Ã­", "i")
                .replace("Ã³", "o")
                .replace("Ãº", "u");
    }
}
