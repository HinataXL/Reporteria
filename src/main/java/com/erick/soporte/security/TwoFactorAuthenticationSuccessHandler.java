package com.erick.soporte.security;

import com.erick.soporte.entity.User;
import com.erick.soporte.repository.UserRepository;
import com.erick.soporte.service.ActiveSessionService;
import com.erick.soporte.service.LoginAlertMailService;
import com.erick.soporte.service.PasskeyEnrollmentService;
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
    private final ActiveSessionService activeSessionService;
    private final LoginAlertMailService loginAlertMailService;
    private final PasskeyEnrollmentService passkeyEnrollmentService;

    public TwoFactorAuthenticationSuccessHandler(
            UserRepository userRepository,
            ActiveSessionService activeSessionService,
            LoginAlertMailService loginAlertMailService,
            PasskeyEnrollmentService passkeyEnrollmentService
    ) {
        this.userRepository = userRepository;
        this.activeSessionService = activeSessionService;
        this.loginAlertMailService = loginAlertMailService;
        this.passkeyEnrollmentService = passkeyEnrollmentService;
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
            setPasskeySetupState(request, principal);
            response.sendRedirect("/2fa/verify");
            return;
        }

        request.getSession().setAttribute("2FA_VERIFIED", true);
        activeSessionService.register(request.getSession().getId(), principal);
        loginAlertMailService.notifyIfWatchedUserLoggedIn(principal, request);

        if (setPasskeySetupState(request, principal)) {
            response.sendRedirect("/settings/2fa?passkeyRequired");
            return;
        }

        if (principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"))) {
            response.sendRedirect("/supervisor/dashboard");
        } else if (principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_AGENTE"))) {
            response.sendRedirect("/agent/dashboard");
        } else {
            response.sendRedirect("/conversations");
        }
    }

    private boolean setPasskeySetupState(HttpServletRequest request, CustomUserPrincipal principal) {
        boolean missingPasskey = !passkeyEnrollmentService.hasPasskey(principal.getUsername());
        if (missingPasskey) {
            request.getSession().setAttribute("PASSKEY_SETUP_REQUIRED", true);
            return true;
        }

        request.getSession().removeAttribute("PASSKEY_SETUP_REQUIRED");
        return false;
    }
}
