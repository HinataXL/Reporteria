package com.erick.soporte.controller;

import com.erick.soporte.repository.ConversationRepository;
import com.erick.soporte.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.erick.soporte.repository.AuditLogRepository;

@Controller
public class AdminController {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final AuditLogRepository auditLogRepository;

    public AdminController(
            UserRepository userRepository,
            ConversationRepository conversationRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalConversations", conversationRepository.count());

        return "admin/dashboard";
    }

    @GetMapping("/admin/logs")
    public String logs(Model model) {
        model.addAttribute("logs", auditLogRepository.findTop100ByOrderByFechaDesc());
        return "admin/logs";
    }

}