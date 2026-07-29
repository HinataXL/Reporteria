package com.erick.soporte.service;

import com.erick.soporte.entity.Conversation;
import com.erick.soporte.repository.ConversationRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
public class ConversationCsvReportService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ConversationRepository conversationRepository;

    public ConversationCsvReportService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    public CsvReport generate(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Debe seleccionar fecha desde y fecha hasta.");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("La fecha hasta no puede ser menor que la fecha desde.");
        }

        List<Conversation> conversations = conversationsBetween(from, to);
        byte[] content = buildCsv(conversations).getBytes(StandardCharsets.UTF_8);
        String filename = "reporte_conversaciones_" + from + "_" + to + ".csv";

        return new CsvReport(filename, content, conversations.size());
    }

    private List<Conversation> conversationsBetween(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();

        return conversationRepository.findAll().stream()
                .filter(conversation -> conversation.getFechaInicio() != null)
                .filter(conversation -> !conversation.getFechaInicio().isBefore(start))
                .filter(conversation -> conversation.getFechaInicio().isBefore(endExclusive))
                .sorted(Comparator.comparing(Conversation::getFechaInicio).thenComparing(Conversation::getId))
                .toList();
    }

    private String buildCsv(List<Conversation> conversations) {
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("Cliente,Telefono,Asunto,Fecha Inicio,Fecha Guardado,Observaciones\n");

        for (Conversation c : conversations) {
            csv.append(safe(c.getClienteNombre())).append(',')
                    .append(safe(c.getClienteTelefono())).append(',')
                    .append(safe(c.getAsunto())).append(',')
                    .append(safe(c.getFechaInicio() != null ? c.getFechaInicio().format(DATE_TIME_FORMATTER) : "")).append(',')
                    .append(safe(finalizacionCsv(c))).append(',')
                    .append(safe(c.getObservaciones()))
                    .append('\n');
        }

        return csv.toString();
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String finalizacionCsv(Conversation conversation) {
        LocalDateTime finalizacion = resolveFinalizacion(conversation);
        return finalizacion != null ? finalizacion.format(DATE_TIME_FORMATTER) : "";
    }

    private LocalDateTime resolveFinalizacion(Conversation conversation) {
        if (conversation.getFechaInicio() != null
                && conversation.getTiempoGestionMinutos() != null
                && conversation.getTiempoGestionMinutos() > 0) {
            return conversation.getFechaInicio().plusMinutes(conversation.getTiempoGestionMinutos());
        }

        if (conversation.getFechaFinalizacion() != null) {
            return conversation.getFechaFinalizacion();
        }

        return conversation.getFechaInicio();
    }

    public record CsvReport(String filename, byte[] content, int rows) {
    }
}
