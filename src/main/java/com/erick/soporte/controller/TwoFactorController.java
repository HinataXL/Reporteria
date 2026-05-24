package com.erick.soporte.controller;

import com.erick.soporte.entity.User;
import com.erick.soporte.repository.UserRepository;
import com.erick.soporte.security.CustomUserPrincipal;
import com.erick.soporte.security.QrCodeService;
import com.erick.soporte.security.TotpService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/settings/2fa")
public class TwoFactorController {

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final QrCodeService qrCodeService;

    public TwoFactorController(
            UserRepository userRepository,
            TotpService totpService,
            QrCodeService qrCodeService
    ) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping
    public String setup(Authentication authentication, Model model) {
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

        return "settings/2fa";
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