package com.erick.soporte.controller;

import com.erick.soporte.service.AuditLogService;
import com.erick.soporte.service.ConversationCsvReportService;
import com.erick.soporte.service.ManualCsvMailService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
public class AdminCsvMailController {

    private final ConversationCsvReportService csvReportService;
    private final ManualCsvMailService mailService;
    private final AuditLogService auditLogService;

    public AdminCsvMailController(
            ConversationCsvReportService csvReportService,
            ManualCsvMailService mailService,
            AuditLogService auditLogService
    ) {
        this.csvReportService = csvReportService;
        this.mailService = mailService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/admin/reports/email-csv")
    public String form(Model model) {
        LocalDate today = LocalDate.now();
        model.addAttribute("from", today.minusDays(7));
        model.addAttribute("to", today);
        model.addAttribute("mailDefaults", mailService.defaults());
        return "admin/email-csv";
    }

    @PostMapping("/admin/reports/email-csv/send")
    public String send(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam String recipients,
            @RequestParam(required = false) String cc,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            ConversationCsvReportService.CsvReport report = csvReportService.generate(from, to);
            mailService.sendCsvReport(report, from, to, recipients, cc);
            auditLogService.registrar(
                    "ENVIO_CSV_EMAIL",
                    "REPORTES",
                    "Envio manual de CSV del " + from + " al " + to + " con " + report.rows() + " registros.",
                    authentication,
                    request
            );

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Reporte enviado correctamente. Registros incluidos: " + report.rows()
            );
        } catch (MessagingException | IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("lastFrom", from);
            redirectAttributes.addFlashAttribute("lastTo", to);
            redirectAttributes.addFlashAttribute("lastRecipients", recipients);
            redirectAttributes.addFlashAttribute("lastCc", cc);
        }

        return "redirect:/admin/reports/email-csv";
    }
}
