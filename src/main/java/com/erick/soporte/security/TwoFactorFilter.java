package com.erick.soporte.security;

import com.erick.soporte.service.ActiveSessionService;
import com.erick.soporte.service.LoginAlertMailService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TwoFactorFilter extends OncePerRequestFilter {

    private final CustomUserDetailsService customUserDetailsService;
    private final ActiveSessionService activeSessionService;
    private final LoginAlertMailService loginAlertMailService;

    public TwoFactorFilter(
            CustomUserDetailsService customUserDetailsService,
            ActiveSessionService activeSessionService,
            LoginAlertMailService loginAlertMailService
    ) {
        this.customUserDetailsService = customUserDetailsService;
        this.activeSessionService = activeSessionService;
        this.loginAlertMailService = loginAlertMailService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {

            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();

        if (authentication instanceof WebAuthnAuthentication) {
            markPasskeySessionAsVerified(request, authentication);
            filterChain.doFilter(request, response);
            return;
        }

        boolean allowed =
                uri.startsWith("/2fa")
                        || uri.startsWith("/logout")
                        || uri.startsWith("/webauthn")
                        || uri.startsWith("/login/webauthn")
                        || uri.startsWith("/css")
                        || uri.startsWith("/js")
                        || uri.startsWith("/img");

        if (allowed) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);

        Boolean verified = session != null
                ? (Boolean) session.getAttribute("2FA_VERIFIED")
                : null;

        if (Boolean.TRUE.equals(verified)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.sendRedirect("/2fa/verify");
    }

    private void markPasskeySessionAsVerified(HttpServletRequest request, Authentication authentication) {
        HttpSession session = request.getSession();
        session.setAttribute("2FA_VERIFIED", true);

        if (Boolean.TRUE.equals(session.getAttribute("PASSKEY_SESSION_REGISTERED"))) {
            return;
        }

        UserDetails details = customUserDetailsService.loadUserByUsername(authentication.getName());
        if (details instanceof CustomUserPrincipal principal) {
            activeSessionService.register(session.getId(), principal);
            loginAlertMailService.notifyIfWatchedUserLoggedIn(principal, request);
            session.setAttribute("PASSKEY_SESSION_REGISTERED", true);
        }
    }
}
