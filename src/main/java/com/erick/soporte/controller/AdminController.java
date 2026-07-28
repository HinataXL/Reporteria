package com.erick.soporte.controller;

import com.erick.soporte.repository.ConversationRepository;
import com.erick.soporte.repository.UserRepository;
import com.erick.soporte.service.GeminiReportService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.erick.soporte.repository.AuditLogRepository;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class AdminController {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectProvider<GeminiReportService> geminiReportServiceProvider;

    public AdminController(
            UserRepository userRepository,
            ConversationRepository conversationRepository,
            AuditLogRepository auditLogRepository,
            ObjectProvider<GeminiReportService> geminiReportServiceProvider
    ) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.auditLogRepository = auditLogRepository;
        this.geminiReportServiceProvider = geminiReportServiceProvider;
    }

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalConversations", conversationRepository.count());
        model.addAttribute("totalAuditLogs", auditLogRepository.count());
        model.addAttribute("geminiStatus", geminiStatus());

        return "admin/dashboard";
    }

    @GetMapping("/admin/gemini")
    public String gemini(Model model) {
        model.addAttribute("geminiStatus", geminiStatus());
        return "admin/gemini";
    }

    @GetMapping("/admin/logs")
    public String logs(Model model) {
        model.addAttribute("logs", auditLogRepository.findTop100ByOrderByFechaDesc());
        model.addAttribute("totalAuditLogs", auditLogRepository.count());
        return "admin/logs";
    }

    private Object geminiStatus() {
        GeminiReportService geminiReportService = geminiReportServiceProvider.getIfAvailable();
        if (geminiReportService != null) {
            return geminiReportService.getUsageSnapshot();
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("model", "No disponible");
        fallback.put("configured", false);
        fallback.put("dashboardRequests", 0);
        fallback.put("issueTrendRequests", 0);
        fallback.put("cacheHits", 0);
        fallback.put("externalCalls", 0);
        fallback.put("successfulCalls", 0);
        fallback.put("failedCalls", 0);
        fallback.put("lastCallAt", null);
        fallback.put("lastSuccessAt", null);
        fallback.put("lastFailureAt", null);
        fallback.put("lastOperation", "Servicio IA no registrado");
        fallback.put("lastError", "Spring no encontro el servicio GeminiReportService");
        return fallback;
    }

}
