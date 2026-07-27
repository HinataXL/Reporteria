package com.erick.soporte.controller;

import com.erick.soporte.entity.User;
import com.erick.soporte.repository.UserRepository;
import com.erick.soporte.security.CustomUserPrincipal;
import com.erick.soporte.security.TotpService;
import com.erick.soporte.service.ActiveSessionService;
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

    public TwoFactorVerificationController(
            UserRepository userRepository,
            TotpService totpService,
            ActiveSessionService activeSessionService
    ) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.activeSessionService = activeSessionService;
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

        return "redirect:/conversations";
    }
}
