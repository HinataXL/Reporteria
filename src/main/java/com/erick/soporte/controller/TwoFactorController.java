package com.erick.soporte.controller;

import com.erick.soporte.entity.User;
import com.erick.soporte.repository.UserRepository;
import com.erick.soporte.security.CustomUserPrincipal;
import com.erick.soporte.security.QrCodeService;
import com.erick.soporte.security.TotpService;
import com.erick.soporte.service.AuditLogService;
import com.erick.soporte.service.PasskeyEnrollmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/2fa")
public class TwoFactorController {

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final QrCodeService qrCodeService;
    private final PasskeyEnrollmentService passkeyEnrollmentService;
    private final AuditLogService auditLogService;

    public TwoFactorController(
            UserRepository userRepository,
            TotpService totpService,
            QrCodeService qrCodeService,
            PasskeyEnrollmentService passkeyEnrollmentService,
            AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.qrCodeService = qrCodeService;
        this.passkeyEnrollmentService = passkeyEnrollmentService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public String setup(Authentication authentication, HttpSession session, Model model) {
        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();

        User user = userRepository.findByCorreo(principal.getCorreo())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (user.getTotpSecret() == null || user.getTotpSecret().isBlank()) {
            user.setTotpSecret(totpService.generateSecret());
            userRepository.save(user);
        }

        String otpUrl = totpService.buildOtpAuthUrl(
                "Soporte",
                user.getCorreo(),
                user.getTotpSecret()
        );

        String qrBase64 = qrCodeService.generateBase64Qr(otpUrl);

        model.addAttribute("qrBase64", qrBase64);
        model.addAttribute("secret", user.getTotpSecret());
        model.addAttribute("enabled", Boolean.TRUE.equals(user.getTotpEnabled()));
        boolean hasPasskey = passkeyEnrollmentService.hasPasskey(principal.getUsername());
        model.addAttribute("hasPasskey", hasPasskey);
        model.addAttribute("passkeyRequired", Boolean.TRUE.equals(session.getAttribute("PASSKEY_SETUP_REQUIRED")) && !hasPasskey);
        model.addAttribute("passkeys", passkeyEnrollmentService.findPasskeys(principal.getUsername()));

        return "settings/2fa";
    }

    @PostMapping("/passkeys/created")
    @ResponseBody
    public ResponseEntity<Void> passkeyCreated(
            Authentication authentication,
            HttpSession session,
            HttpServletRequest request
    ) {
        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
        if (!passkeyEnrollmentService.hasPasskey(principal.getUsername())) {
            return ResponseEntity.badRequest().build();
        }

        session.removeAttribute("PASSKEY_SETUP_REQUIRED");
        auditLogService.registrar(
                "CREAR_PASSKEY",
                "SEGURIDAD",
                "Se registro una passkey para " + principal.getUsername(),
                authentication,
                request
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/passkeys/{credentialId}/label")
    public String updatePasskeyLabel(
            @PathVariable String credentialId,
            @RequestParam String label,
            Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
        boolean updated = passkeyEnrollmentService.updateLabel(principal.getUsername(), credentialId, label);

        if (updated) {
            auditLogService.registrar(
                    "RENOMBRAR_PASSKEY",
                    "SEGURIDAD",
                    "Se renombro una passkey de " + principal.getUsername(),
                    authentication,
                    request
            );
            redirectAttributes.addFlashAttribute("success", "Passkey actualizada correctamente.");
        } else {
            redirectAttributes.addFlashAttribute("error", "No fue posible actualizar la passkey.");
        }

        return "redirect:/settings/2fa";
    }

    @PostMapping("/passkeys/{credentialId}/delete")
    public String deletePasskey(
            @PathVariable String credentialId,
            Authentication authentication,
            HttpSession session,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
        boolean deleted = passkeyEnrollmentService.deletePasskey(principal.getUsername(), credentialId);

        if (deleted) {
            auditLogService.registrar(
                    "ELIMINAR_PASSKEY",
                    "SEGURIDAD",
                    "Se elimino una passkey de " + principal.getUsername(),
                    authentication,
                    request
            );

            if (passkeyEnrollmentService.requiresPasskey(principal.getUsername(), principal.getRol())) {
                session.setAttribute("PASSKEY_SETUP_REQUIRED", true);
                redirectAttributes.addFlashAttribute("error", "Debes registrar una nueva passkey para continuar.");
                return "redirect:/settings/2fa?passkeyRequired";
            }

            redirectAttributes.addFlashAttribute("success", "Passkey eliminada correctamente.");
        } else {
            redirectAttributes.addFlashAttribute("error", "No fue posible eliminar la passkey.");
        }

        return "redirect:/settings/2fa";
    }

    @PostMapping("/enable")
    public String enable(
            @RequestParam int code,
            Authentication authentication,
            Model model
    ) {
        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();

        User user = userRepository.findByCorreo(principal.getCorreo())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        boolean valid = totpService.verifyCode(user.getTotpSecret(), code);

        if (!valid) {
            model.addAttribute("error", "Código inválido. Intenta de nuevo.");

            String otpUrl = totpService.buildOtpAuthUrl(
                    "Soporte",
                    user.getCorreo(),
                    user.getTotpSecret()
            );

            model.addAttribute("qrBase64", qrCodeService.generateBase64Qr(otpUrl));
            model.addAttribute("secret", user.getTotpSecret());
            model.addAttribute("enabled", false);

            return "settings/2fa";
        }

        user.setTotpEnabled(true);
        userRepository.save(user);

        return "redirect:/settings/2fa?enabled";
    }

    @PostMapping("/disable")
    public String disable(Authentication authentication) {
        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();

        User user = userRepository.findByCorreo(principal.getCorreo())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);

        return "redirect:/settings/2fa?disabled";
    }
}
