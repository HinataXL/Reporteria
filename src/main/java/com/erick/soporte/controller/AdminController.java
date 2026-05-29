package com.erick.soporte.controller;

import com.erick.soporte.repository.ConversationRepository;
import com.erick.soporte.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;

    public AdminController(
            UserRepository userRepository,
            ConversationRepository conversationRepository
    ) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
    }

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalConversations", conversationRepository.count());

        return "admin/dashboard";
    }
}