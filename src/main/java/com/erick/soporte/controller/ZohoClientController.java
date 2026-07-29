package com.erick.soporte.controller;

import com.erick.soporte.entity.SupportClient;
import com.erick.soporte.repository.SupportClientRepository;
import com.erick.soporte.service.AuditLogService;
import com.erick.soporte.service.ZohoDeskClientService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ZohoClientController {

    private final ZohoDeskClientService zohoDeskClientService;
    private final SupportClientRepository supportClientRepository;
    private final AuditLogService auditLogService;

    public ZohoClientController(
            ZohoDeskClientService zohoDeskClientService,
            SupportClientRepository supportClientRepository,
            AuditLogService auditLogService
    ) {
        this.zohoDeskClientService = zohoDeskClientService;
        this.supportClientRepository = supportClientRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/admin/zoho")
    public String zoho(Model model) {
        model.addAttribute("zohoStatus", zohoDeskClientService.status());
        return "admin/zoho";
    }

    @PostMapping("/admin/zoho/sync-clients")
    public String syncClients(
            @RequestParam(defaultValue = "5") int maxPages,
            RedirectAttributes redirectAttributes,
            Authentication authentication,
            HttpServletRequest request
    ) {
        try {
            Map<String, Object> result = zohoDeskClientService.syncContacts(maxPages);
            redirectAttributes.addFlashAttribute("success",
                    "Sincronizacion completa: " + result.get("created") + " nuevos, "
                            + result.get("updated") + " actualizados.");
            auditLogService.registrar(
                    "SINCRONIZAR_ZOHO_CLIENTES",
                    "ZOHO",
                    "Se sincronizaron contactos Zoho: " + result.get("created") + " nuevos, "
                            + result.get("updated") + " actualizados.",
                    authentication,
                    request
            );
        } catch (Exception error) {
            redirectAttributes.addFlashAttribute("error", error.getMessage());
        }

        return "redirect:/admin/zoho";
    }

    @GetMapping("/api/clients/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchClients(
            @RequestParam(required = false) String q,
            Authentication authentication
    ) {
        if (authentication == null || q == null || q.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }

        String term = q.trim();
        List<Map<String, Object>> clients = supportClientRepository
                .searchActiveClients(term, PageRequest.of(0, 12))
                .stream()
                .map(this::clientPayload)
                .toList();

        return ResponseEntity.ok(clients);
    }

    private Map<String, Object> clientPayload(SupportClient client) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", client.getId());
        item.put("zohoContactId", client.getZohoContactId());
        item.put("name", client.getFullName());
        item.put("accountName", client.getAccountName());
        item.put("email", client.getEmail());
        item.put("phone", client.getPhone());
        item.put("mobile", client.getMobile());
        return item;
    }
}
