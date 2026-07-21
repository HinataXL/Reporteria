package com.erick.soporte.controller;

import com.erick.soporte.entity.Conversation;
import com.erick.soporte.repository.ConversationRepository;
import com.erick.soporte.security.CustomUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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
public class AgentDashboardController {

    private final ConversationRepository conversationRepository;

    public AgentDashboardController(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @GetMapping("/agent/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();

        List<Conversation> conversations = conversationRepository.findAll()
                .stream()
                .filter(c -> belongsToAgent(c, user))
                .toList();

        long total = conversations.size();
        long pendientes = countByStatus(conversations, 1);
        long enProceso = countByStatus(conversations, 2);
        long resueltas = countByStatus(conversations, 3);
        long escaladas = countByStatus(conversations, 4);
        long cerradas = countByStatus(conversations, 5);

        int tiempoTotal = conversations.stream()
                .mapToInt(c -> c.getTiempoGestionMinutos() != null ? c.getTiempoGestionMinutos() : 0)
                .sum();

        double promedioTiempo = conversations.stream()
                .filter(c -> c.getTiempoGestionMinutos() != null)
                .mapToInt(Conversation::getTiempoGestionMinutos)
                .average()
                .orElse(0);

        String effectiveGranularity = resolveGranularity("auto", conversations);
        Map<String, Long> productividadAgrupada = groupProductivity(conversations, effectiveGranularity);

        Map<String, Long> porEstado = conversations.stream()
                .collect(Collectors.groupingBy(
                        c -> statusName(c.getStatusId()),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        Map<String, Long> porPrioridad = conversations.stream()
                .collect(Collectors.groupingBy(
                        c -> priorityName(c.getPriorityId()),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        Map<String, Long> porCanal = conversations.stream()
                .collect(Collectors.groupingBy(
                        c -> channelName(c.getChannelId()),
                        LinkedHashMap::new,
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
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<String, Long> topClientes = conversations.stream()
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

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        List<Map<String, Object>> recientes = conversations.stream()
                .sorted(Comparator.comparing(
                        Conversation::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .limit(8)
                .map(c -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("codigo", c.getCodigo());
                    item.put("cliente", c.getClienteNombre());
                    item.put("asunto", c.getAsunto());
                    item.put("estado", statusName(c.getStatusId()));
                    item.put("tiempoGestion", c.getTiempoGestionMinutos() != null ? c.getTiempoGestionMinutos() : 0);
                    item.put("fecha", c.getFechaInicio() != null ? c.getFechaInicio().format(dateTimeFormatter) : "Sin fecha");
                    return item;
                })
                .toList();

        model.addAttribute("userName", user.getNombreCompleto());
        model.addAttribute("userRole", user.getRol());
        model.addAttribute("userEmail", user.getCorreo());
        model.addAttribute("total", total);
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("enProceso", enProceso);
        model.addAttribute("resueltas", resueltas);
        model.addAttribute("escaladas", escaladas);
        model.addAttribute("cerradas", cerradas);
        model.addAttribute("tiempoTotal", tiempoTotal);
        model.addAttribute("promedioTiempo", String.format("%.1f", promedioTiempo));
        model.addAttribute("productividadLabels", productividadAgrupada.keySet());
        model.addAttribute("productividadValues", productividadAgrupada.values());
        model.addAttribute("granularity", effectiveGranularity);
        model.addAttribute("estadoLabels", porEstado.keySet());
        model.addAttribute("estadoValues", porEstado.values());
        model.addAttribute("prioridadLabels", porPrioridad.keySet());
        model.addAttribute("prioridadValues", porPrioridad.values());
        model.addAttribute("canalLabels", porCanal.keySet());
        model.addAttribute("canalValues", porCanal.values());
        model.addAttribute("asuntoLabels", topAsuntos.keySet());
        model.addAttribute("asuntoValues", topAsuntos.values());
        model.addAttribute("clienteLabels", topClientes.keySet());
        model.addAttribute("clienteValues", topClientes.values());
        model.addAttribute("recientes", recientes);

        return "agent/dashboard";
    }

    private boolean belongsToAgent(Conversation conversation, CustomUserPrincipal user) {
        if (conversation.getUserId() != null && conversation.getUserId().equals(user.getId())) {
            return true;
        }

        return conversation.getAgenteNombre() != null
                && conversation.getAgenteNombre().equalsIgnoreCase(user.getNombreCompleto());
    }

    private long countByStatus(List<Conversation> conversations, int statusId) {
        return conversations.stream()
                .filter(c -> c.getStatusId() != null && c.getStatusId() == statusId)
                .count();
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

    private String priorityName(Long id) {
        if (id == null) return "Desconocida";

        return switch (id.intValue()) {
            case 1 -> "Baja";
            case 2 -> "Media";
            case 3 -> "Alta";
            case 4 -> "Critica";
            default -> "Desconocida";
        };
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
}
