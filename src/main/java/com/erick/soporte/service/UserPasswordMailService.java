package com.erick.soporte.service;

import com.erick.soporte.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Service
public class UserPasswordMailService {

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@$%";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;
    private final String mailUsername;
    private final String mailPassword;
    private final String defaultFrom;

    public UserPasswordMailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${report.mail.from:}") String defaultFrom
    ) {
        this.mailSender = mailSender;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
        this.defaultFrom = defaultFrom;
    }

    public String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            password.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }

    public void sendTemporaryPassword(User user, String temporaryPassword) throws MessagingException {
        if (!isConfigured()) {
            throw new IllegalStateException("Falta configurar MAIL_USERNAME y MAIL_PASSWORD.");
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());

        helper.setFrom(resolveFrom());
        helper.setTo(user.getCorreo());
        helper.setSubject("Nueva contrasena temporal - Insights Desk");
        helper.setText("""
                Hola %s,

                Se genero una nueva contrasena temporal para tu usuario de Insights Desk.

                Usuario: %s
                Contrasena temporal: %s

                Por seguridad, cambia tu contrasena desde Mi perfil al ingresar nuevamente.

                Correo enviado automaticamente, no responder.
                """.formatted(resolveName(user), user.getCorreo(), temporaryPassword));

        mailSender.send(message);
    }

    private String resolveName(User user) {
        String nombre = user.getNombre() == null ? "" : user.getNombre().trim();
        return nombre.isBlank() ? "usuario" : nombre;
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
