package com.erick.soporte.security;

import com.erick.soporte.entity.User;
import com.erick.soporte.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TwoFactorAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;

    public TwoFactorAuthenticationSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();

        User user = userRepository.findByCorreo(principal.getCorreo())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            request.getSession().setAttribute("2FA_VERIFIED", false);
            response.sendRedirect("/2fa/verify");
            return;
        }

        request.getSession().setAttribute("2FA_VERIFIED", true);
        if (principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"))) {
            response.sendRedirect("/supervisor/dashboard");
        } else {
            response.sendRedirect("/conversations");
        }
    }
}