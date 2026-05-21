package com.erick.soporte.controller;

import com.erick.soporte.entity.Conversation;
import com.erick.soporte.repository.ConversationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class HomeController {

    private final ConversationRepository conversationRepository;

    public HomeController(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/conversations")
    public String conversations(Model model) {
        model.addAttribute("conversations", conversationRepository.findAll());
        return "conversations/index";
    }

    @GetMapping("/conversations/create")
    public String createConversation(Model model) {
        model.addAttribute("conversation", new Conversation());
        return "conversations/create";
    }

    @PostMapping("/conversations/save")
    public String saveConversation(@ModelAttribute Conversation conversation) {
        Conversation saved = conversationRepository.save(conversation);
        saved.setCodigo("CONV-" + String.format("%05d", saved.getId()));
        conversationRepository.save(saved);

        return "redirect:/conversations";
    }

    @GetMapping("/conversations/{id}")
    public String detailConversation(@PathVariable Long id, Model model) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conversación no encontrada"));

        model.addAttribute("conversation", conversation);
        return "conversations/detail";
    }
}