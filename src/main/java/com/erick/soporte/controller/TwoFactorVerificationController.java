package com.erick.soporte.controller;

import com.erick.soporte.entity.User;
import com.erick.soporte.repository.UserRepository;
import com.erick.soporte.security.CustomUserPrincipal;
import com.erick.soporte.security.TotpService;
import com.erick.soporte.service.ActiveSessionService;
import com.erick.soporte.service.LoginAlertMailService;
import com.erick.soporte.service.PasskeyEnrollmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/2fa")
public class TwoFactorVerificationController {

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final ActiveSessionService activeSessionService;
    private final LoginAlertMailService loginAlertMailService;
    private final PasskeyEnrollmentService passkeyEnrollmentService;

    public TwoFactorVerificationController(
            UserRepository userRepository,
            TotpService totpService,
            ActiveSessionService activeSessionService,
            LoginAlertMailService loginAlertMailService,
            PasskeyEnrollmentService passkeyEnrollmentService
    ) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.activeSessionService = activeSessionService;
        this.loginAlertMailService = loginAlertMailService;
        this.passkeyEnrollmentService = passkeyEnrollmentService;
    }

    @GetMapping("/verify")
    public String verifyPage() {
        return "auth/2fa-verify";
    }

    @PostMapping("/verify")
    public String verifyCode(
            @RequestParam int code,
            Authentication authentication,
            HttpSession session,
            HttpServletRequest request,
            Model model
    ) {
        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();

        User user = userRepository.findByCorreo(principal.getCorreo())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        boolean valid = totpService.verifyCode(user.getTotpSecret(), code);

        if (!valid) {
            model.addAttribute("error", "Código inválido.");
            return "auth/2fa-verify";
        }

        session.setAttribute("2FA_VERIFIED", true);
        activeSessionService.register(session.getId(), principal);
        loginAlertMailService.notifyIfWatchedUserLoggedIn(principal, request);

        if (passkeyEnrollmentService.requiresPasskey(principal.getUsername(), principal.getRol())) {
            session.setAttribute("PASSKEY_SETUP_REQUIRED", true);
            return "redirect:/settings/2fa?passkeyRequired";
        }

        session.removeAttribute("PASSKEY_SETUP_REQUIRED");

        return "redirect:/conversations";
    }
}
