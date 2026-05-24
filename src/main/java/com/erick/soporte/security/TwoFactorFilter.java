package com.erick.soporte.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TwoFactorFilter extends OncePerRequestFilter {

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

        boolean allowed =
                uri.startsWith("/2fa")
                        || uri.startsWith("/logout")
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
}