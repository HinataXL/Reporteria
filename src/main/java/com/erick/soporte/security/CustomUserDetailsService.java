package com.erick.soporte.security;

import com.erick.soporte.entity.User;
import com.erick.soporte.repository.UserRepository;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Value("${app.allowed-email-domain}")
    private String allowedEmailDomain;

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {

        if (correo == null || !correo.toLowerCase().endsWith(allowedEmailDomain.toLowerCase())) {
            throw new UsernameNotFoundException("Dominio de correo no permitido");
        }

        User user = userRepository.findByCorreo(correo)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        if (user.getEstado() == null || user.getEstado() == 0) {
            throw new UsernameNotFoundException("Usuario inactivo");
        }

        String roleName = user.getRole() != null
                ? user.getRole().getNombre()
                : "AGENTE";

        return new CustomUserPrincipal(
                user,
                List.of(new SimpleGrantedAuthority("ROLE_" + roleName))
        );
    }
}
