package com.erick.soporte.service;

import com.erick.soporte.security.CustomUserPrincipal;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Service
public class LoginAlertMailService {

    private static final Logger log = LoggerFactory.getLogger(LoginAlertMailService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final JavaMailSender mailSender;
    private final String mailUsername;
    private final String mailPassword;
    private final String defaultFrom;
    private final String reportMailTo;
    private final boolean enabled;
    private final String watchedEmail;
    private final String alertRecipients;

    public LoginAlertMailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${report.mail.from:}") String defaultFrom,
            @Value("${report.mail.to:}") String reportMailTo,
            @Value("${login.alert.enabled:true}") boolean enabled,
            @Value("${login.alert.user:pablo.flores@fixss.com}") String watchedEmail,
            @Value("${login.alert.to:}") String alertRecipients
    ) {
        this.mailSender = mailSender;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
        this.defaultFrom = defaultFrom;
        this.reportMailTo = reportMailTo;
        this.enabled = enabled;
        this.watchedEmail = watchedEmail;
        this.alertRecipients = alertRecipients;
    }

    @Async
    public void notifyIfWatchedUserLoggedIn(CustomUserPrincipal principal, HttpServletRequest request) {
        if (!enabled || principal == null || !equalsIgnoreCase(principal.getCorreo(), watchedEmail)) {
            return;
        }

        String recipients = hasText(alertRecipients) ? alertRecipients : reportMailTo;
        if (!isMailConfigured() || !hasText(recipients)) {
            log.warn("No se envio alerta de login para {} porque falta configuracion de correo o destinatario.", watchedEmail);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(resolveFrom());
            helper.setTo(splitAddresses(recipients));
            helper.setSubject("Alerta de conexion: " + principal.getCorreo());
            helper.setText("""
                    Hola,

                    El usuario monitoreado ingreso al sistema de Reporteria.

                    Usuario: %s
                    Nombre: %s
                    Fecha y hora: %s
                    IP: %s
                    Navegador: %s
                    """.formatted(
                    principal.getCorreo(),
                    principal.getNombreCompleto(),
                    LocalDateTime.now().format(FORMATTER),
                    clientIp(request),
                    request.getHeader("User-Agent")
            ));

            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("No se pudo enviar alerta de login para {}: {}", watchedEmail, ex.getMessage());
        }
    }

    private boolean isMailConfigured() {
        return hasText(mailUsername) && hasText(mailPassword) && hasText(resolveFrom());
    }

    private String resolveFrom() {
        if (hasText(defaultFrom)) {
            return defaultFrom.trim();
        }

        return mailUsername != null ? mailUsername.trim() : "";
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private String[] splitAddresses(String value) {
        return Arrays.stream(value.split("[,;]"))
                .map(String::trim)
                .filter(this::hasText)
                .toArray(String[]::new);
    }

    private boolean equalsIgnoreCase(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
