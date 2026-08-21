package com.erick.soporte.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerEndpointInventoryTest {

    private static final Class<?>[] CONTROLLERS = {
            AdminController.class,
            AdminCsvMailController.class,
            AgentDashboardController.class,
            ApplicationErrorController.class,
            AuthController.class,
            CallRecordController.class,
            ConversationStatusApiController.class,
            DashboardApiController.class,
            FrontendErrorController.class,
            HomeController.class,
            ProfileController.class,
            QpayproWebhookController.class,
            SessionController.class,
            SupervisorDashboardController.class,
            SupervisorReportController.class,
            TwoFactorController.class,
            TwoFactorVerificationController.class,
            UserController.class,
            ZohoClientController.class,
            ZohoCrmTaskController.class
    };

    @Test
    void exposesExpectedEndpointInventory() {
        assertThat(discoverEndpoints()).containsExactlyInAnyOrder(
                "GET /",
                "GET /2fa/verify",
                "GET /admin/dashboard",
                "GET /admin/frontend-errors",
                "GET /admin/gemini",
                "GET /admin/logs",
                "GET /admin/reports/email-csv",
                "GET /admin/zoho",
                "GET /admin/zoho-crm/tasks",
                "GET /admin/zoho-crm/tasks/dashboard",
                "GET /agent/dashboard",
                "GET /agent/zoho-crm/tasks/dashboard",
                "GET /api/agent-dashboard/status-conversations",
                "GET /api/clients/search",
                "GET /api/dashboard/ai-report",
                "GET /api/dashboard/client-360",
                "GET /api/dashboard/issue-trends",
                "GET /api/dashboard/metrics",
                "GET /api/dashboard/peak-hour",
                "GET /api/dashboard/status-conversations",
                "GET /api/webhooks/qpaypro/health",
                "GET /calls",
                "GET /calls/{id}/edit",
                "GET /calls/create",
                "GET /calls/export/csv",
                "GET /conversations",
                "GET /conversations/{id}",
                "GET /conversations/create",
                "GET /conversations/edit/{id}",
                "GET /conversations/export/csv",
                "GET /login",
                "GET /profile",
                "GET /settings/2fa",
                "GET /supervisor/dashboard",
                "GET /supervisor/dashboard-v2",
                "GET /supervisor/report/pdf",
                "GET /users",
                "GET /users/create",
                "POST /2fa/verify",
                "POST /admin/reports/email-csv/send",
                "POST /admin/zoho-crm/tasks/refresh-requests/{id}/approve",
                "POST /admin/zoho-crm/tasks/refresh-requests/{id}/approve/async",
                "POST /admin/zoho-crm/tasks/refresh-requests/{id}/reject",
                "POST /admin/zoho-crm/tasks/refresh-requests/{id}/reject/async",
                "POST /admin/zoho/sync-clients",
                "POST /agent/zoho-crm/tasks/refresh-request",
                "POST /api/calls/save",
                "POST /api/conversations/{id}/status",
                "POST /api/conversations/save",
                "POST /api/frontend-errors",
                "POST /api/session/keep-alive",
                "POST /api/webhooks/qpaypro",
                "POST /calls/{id}/zoho-ticket",
                "POST /calls/save",
                "POST /calls/update/{id}",
                "POST /conversations/{id}/zoho-ticket",
                "POST /conversations/bulk/assign",
                "POST /conversations/bulk/export",
                "POST /conversations/bulk/priority",
                "POST /conversations/bulk/status",
                "POST /conversations/update/{id}",
                "POST /profile/change-password",
                "POST /settings/2fa/disable",
                "POST /settings/2fa/enable",
                "POST /settings/2fa/passkeys/created",
                "POST /settings/2fa/passkeys/{credentialId}/delete",
                "POST /settings/2fa/passkeys/{credentialId}/label",
                "POST /users/{id}/delete",
                "POST /users/{id}/passkeys/reset",
                "POST /users/{id}/password",
                "POST /users/{id}/role",
                "POST /users/save",
                "REQUEST /error"
        );
    }

    private Set<String> discoverEndpoints() {
        Set<String> endpoints = new LinkedHashSet<>();

        for (Class<?> controller : CONTROLLERS) {
            String classPath = firstPath(AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class));
            for (Method method : controller.getDeclaredMethods()) {
                addEndpoint(endpoints, "GET", classPath, method, GetMapping.class);
                addEndpoint(endpoints, "POST", classPath, method, PostMapping.class);
                addEndpoint(endpoints, "PUT", classPath, method, PutMapping.class);
                addEndpoint(endpoints, "PATCH", classPath, method, PatchMapping.class);
                addEndpoint(endpoints, "DELETE", classPath, method, DeleteMapping.class);
                if (!hasHttpShortcut(method)) {
                    addEndpoint(endpoints, "REQUEST", classPath, method, RequestMapping.class);
                }
            }
        }

        return endpoints;
    }

    private boolean hasHttpShortcut(Method method) {
        return AnnotatedElementUtils.findMergedAnnotation(method, GetMapping.class) != null
                || AnnotatedElementUtils.findMergedAnnotation(method, PostMapping.class) != null
                || AnnotatedElementUtils.findMergedAnnotation(method, PutMapping.class) != null
                || AnnotatedElementUtils.findMergedAnnotation(method, PatchMapping.class) != null
                || AnnotatedElementUtils.findMergedAnnotation(method, DeleteMapping.class) != null;
    }

    private <A extends Annotation> void addEndpoint(
            Set<String> endpoints,
            String verb,
            String classPath,
            Method method,
            Class<A> annotationType
    ) {
        A annotation = AnnotatedElementUtils.findMergedAnnotation(method, annotationType);
        if (annotation == null) {
            return;
        }

        String[] paths = paths(annotation);
        if (paths.length == 0) {
            paths = new String[]{""};
        }

        for (String path : paths) {
            endpoints.add(verb + " " + normalize(classPath, path));
        }
    }

    private String[] paths(Annotation annotation) {
        try {
            String[] path = (String[]) annotation.annotationType().getMethod("path").invoke(annotation);
            String[] value = (String[]) annotation.annotationType().getMethod("value").invoke(annotation);
            return path.length > 0 ? path : value;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("No fue posible leer el path del endpoint", ex);
        }
    }

    private String firstPath(RequestMapping mapping) {
        if (mapping == null) {
            return "";
        }

        return Arrays.stream(mapping.path().length > 0 ? mapping.path() : mapping.value())
                .findFirst()
                .orElse("");
    }

    private String normalize(String classPath, String methodPath) {
        String joined = Arrays.stream(new String[]{classPath, methodPath})
                .filter(part -> part != null && !part.isBlank())
                .map(part -> part.startsWith("/") ? part.substring(1) : part)
                .map(part -> part.endsWith("/") ? part.substring(0, part.length() - 1) : part)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining("/"));

        return joined.isBlank() ? "/" : "/" + joined;
    }
}
