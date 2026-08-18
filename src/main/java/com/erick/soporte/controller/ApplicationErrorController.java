package com.erick.soporte.controller;

import com.erick.soporte.entity.FrontendErrorLog;
import com.erick.soporte.repository.FrontendErrorLogRepository;
import com.erick.soporte.security.CustomUserPrincipal;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.PrintWriter;
import java.io.StringWriter;

@Controller
public class ApplicationErrorController {

    private static final int TEXT_LIMIT = 4000;

    private final FrontendErrorLogRepository frontendErrorLogRepository;

    public ApplicationErrorController(FrontendErrorLogRepository frontendErrorLogRepository) {
        this.frontendErrorLogRepository = frontendErrorLogRepository;
    }

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Authentication authentication, Model model) {
        int status = statusCode(request);
        Throwable exception = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        String path = stringValue(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI), request.getRequestURI());
        String message = errorMessage(request, exception, status);
        String reference = "ERR-" + System.currentTimeMillis();

        saveServerError(reference, status, path, message, exception, request, authentication);

        model.addAttribute("status", status);
        model.addAttribute("title", titleForStatus(status));
        model.addAttribute("message", publicMessage(status));
        model.addAttribute("path", path);
        model.addAttribute("reference", reference);
        model.addAttribute("isNotFound", status == HttpStatus.NOT_FOUND.value());
        return "error/custom-error";
    }

    private void saveServerError(
            String reference,
            int status,
            String path,
            String message,
            Throwable exception,
            HttpServletRequest request,
            Authentication authentication
    ) {
        try {
            FrontendErrorLog log = new FrontendErrorLog();
            if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal user) {
                log.setUsuarioId(user.getId());
                log.setUsuarioNombre(limit(user.getNombreCompleto(), 180));
                log.setUsuarioCorreo(limit(user.getCorreo(), 180));
                log.setRol(limit(user.getRol(), 80));
            }

            log.setLevel(status >= 500 ? "ERROR" : "WARN");
            log.setEventType("server.error");
            log.setMessage(limit(reference + " | HTTP " + status + " | " + message, TEXT_LIMIT));
            log.setSource(limit(exception != null ? exception.getClass().getName() : "Spring ErrorController", TEXT_LIMIT));
            log.setStackTrace(limit(stackTrace(exception), TEXT_LIMIT));
            log.setPageUrl(limit(path, TEXT_LIMIT));
            log.setIp(limit(request.getRemoteAddr(), 80));
            log.setUserAgent(limit(request.getHeader("User-Agent"), TEXT_LIMIT));
            frontendErrorLogRepository.save(log);
        } catch (Exception ignored) {
            // Evita que el registro del error provoque otro error en la pagina /error.
        }
    }

    private int statusCode(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status instanceof Integer code) {
            return code;
        }
        try {
            return Integer.parseInt(String.valueOf(status));
        } catch (Exception ignored) {
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
    }

    private String errorMessage(HttpServletRequest request, Throwable exception, int status) {
        if (exception != null && exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }

        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        if (message != null && !String.valueOf(message).isBlank()) {
            return String.valueOf(message);
        }

        return titleForStatus(status);
    }

    private String titleForStatus(int status) {
        if (status == HttpStatus.NOT_FOUND.value()) {
            return "Pagina no encontrada";
        }
        if (status == HttpStatus.FORBIDDEN.value()) {
            return "Acceso restringido";
        }
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return "Sesion requerida";
        }
        return "No fue posible completar la solicitud";
    }

    private String publicMessage(int status) {
        if (status == HttpStatus.NOT_FOUND.value()) {
            return "La ruta que intentaste abrir no existe o fue movida.";
        }
        if (status == HttpStatus.FORBIDDEN.value()) {
            return "Tu usuario no tiene permisos para ver esta seccion.";
        }
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return "Inicia sesion nuevamente para continuar.";
        }
        return "Registramos el error para revision del equipo administrador. Puedes volver e intentar de nuevo.";
    }

    private String stackTrace(Throwable exception) {
        if (exception == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private String stringValue(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.strip();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
