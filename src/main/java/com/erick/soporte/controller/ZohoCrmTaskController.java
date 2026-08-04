package com.erick.soporte.controller;

import com.erick.soporte.service.ZohoCrmTaskMetricsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class ZohoCrmTaskController {

    private final ZohoCrmTaskMetricsService taskMetricsService;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public ZohoCrmTaskController(ZohoCrmTaskMetricsService taskMetricsService) {
        this.taskMetricsService = taskMetricsService;
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
        } catch (Exception error) {
            model.addAttribute("error", error.getMessage());
        }

        return "admin/zoho-crm-tasks";
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
}
