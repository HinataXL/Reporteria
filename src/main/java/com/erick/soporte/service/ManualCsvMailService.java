package com.erick.soporte.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;

@Service
public class ManualCsvMailService {

    private final JavaMailSender mailSender;
    private final String mailUsername;
    private final String mailPassword;
    private final String defaultFrom;
    private final String defaultTo;
    private final String defaultCc;

    public ManualCsvMailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${report.mail.from:}") String defaultFrom,
            @Value("${report.mail.to:}") String defaultTo,
            @Value("${report.mail.cc:}") String defaultCc
    ) {
        this.mailSender = mailSender;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
        this.defaultFrom = defaultFrom;
        this.defaultTo = defaultTo;
        this.defaultCc = defaultCc;
    }

    public MailDefaults defaults() {
        return new MailDefaults(resolveFrom(), defaultTo, defaultCc, isConfigured());
    }

    public void sendCsvReport(
            ConversationCsvReportService.CsvReport report,
            LocalDate from,
            LocalDate to,
            String toAddresses,
            String ccAddresses
    ) throws MessagingException {
        if (!isConfigured()) {
            throw new IllegalStateException("Falta configurar MAIL_USERNAME y MAIL_PASSWORD.");
        }
        if (!hasText(toAddresses)) {
            throw new IllegalArgumentException("Debe indicar al menos un destinatario.");
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

        helper.setFrom(resolveFrom());
        helper.setTo(splitAddresses(toAddresses));
        if (hasText(ccAddresses)) {
            helper.setCc(splitAddresses(ccAddresses));
        }

        helper.setSubject("Reporte Conversaciones " + from + " a " + to);
        helper.setText("""
                Hola,

                Se adjunta el reporte CSV de conversaciones.

                Rango: %s a %s
                Registros incluidos: %d
                
                Correo enviado automaticamente, no responder.
                """.formatted(from, to, report.rows()));
        helper.addAttachment(report.filename(), new ByteArrayResource(report.content()), "text/csv");

        mailSender.send(message);
    }

    private String resolveFrom() {
        if (hasText(defaultFrom)) {
            return defaultFrom.trim();
        }

        return mailUsername != null ? mailUsername.trim() : "";
    }

    private boolean isConfigured() {
        return hasText(mailUsername) && hasText(mailPassword) && hasText(resolveFrom());
    }

    private String[] splitAddresses(String value) {
        return Arrays.stream(value.split("[,;]"))
                .map(String::trim)
                .filter(this::hasText)
                .toArray(String[]::new);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record MailDefaults(String from, String to, String cc, boolean configured) {
    }
}
