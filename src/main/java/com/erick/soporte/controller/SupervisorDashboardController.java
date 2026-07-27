package com.erick.soporte.controller;

import com.erick.soporte.entity.Conversation;
import com.erick.soporte.repository.ConversationRepository;
import com.erick.soporte.service.ActiveSessionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Controller
public class SupervisorDashboardController {

    private final ConversationRepository conversationRepository;
    private final ActiveSessionService activeSessionService;

    public SupervisorDashboardController(
            ConversationRepository conversationRepository,
            ActiveSessionService activeSessionService
    ) {
        this.conversationRepository = conversationRepository;
        this.activeSessionService = activeSessionService;
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

        long cerradas = conversations.stream()
                .filter(c -> c.getStatusId() != null && c.getStatusId() == 5)
                .count();

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
        Map<String, Object> issueTrend = calculateIssueTrend(conversations, null);

        Map<String, Long> porAgente = conversations.stream()
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

        String effectiveGranularity = resolveGranularity("auto", conversations);
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

        model.addAttribute("total", total);
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("resueltas", resueltas);
        model.addAttribute("escaladas", escaladas);
        model.addAttribute("cerradas", cerradas);
        model.addAttribute("promedioTiempo", String.format("%.1f", promedioTiempo));
        model.addAttribute("operationalHealth", operationalHealth);
        model.addAttribute("weeklyTrend", weeklyTrend);
        model.addAttribute("peakHours", peakHours);
        model.addAttribute("issueTrend", issueTrend);

        model.addAttribute("agenteLabels", porAgente.keySet());
        model.addAttribute("agenteValues", porAgente.values());

        model.addAttribute("canalLabels", porCanal.keySet());
        model.addAttribute("canalValues", porCanal.values());

        model.addAttribute("prioridadLabels", porPrioridad.keySet());
        model.addAttribute("prioridadValues", porPrioridad.values());

        model.addAttribute("productividadLabels", productividadAgrupada.keySet());
        model.addAttribute("productividadValues", productividadAgrupada.values());
        model.addAttribute("granularity", effectiveGranularity);
        model.addAttribute("asuntoLabels", porAsunto.keySet());
        model.addAttribute("asuntoValues", porAsunto.values());
        model.addAttribute("clienteLabels", porCliente.keySet());
        model.addAttribute("clienteValues", porCliente.values());
        model.addAttribute("clienteTiempoValues", porCliente.keySet().stream()
                .map(cliente -> tiempoGestionPorCliente.getOrDefault(cliente, 0))
                .toList());
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        model.addAttribute("recientes", conversations.stream()
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
                .toList());
        model.addAttribute("activeAgents", activeSessionService.activeAgents());

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

    private String statusName(Long id) {
        if (id == null) return "Desconocido";

        return switch (id.intValue()) {
            case 1 -> "Pendiente";
            case 2 -> "En proceso";
            case 3 -> "Resuelto";
            case 4 -> "Escalado";
            case 5 -> "Cerrado";
            default -> "Desconocido";
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
        return trend;
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
