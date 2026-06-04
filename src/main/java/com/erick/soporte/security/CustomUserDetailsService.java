package com.erick.soporte.security;

import com.erick.soporte.entity.User;
import com.erick.soporte.repository.UserRepository;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
        System.out.println("Intentando login con correo: " + correo);

        User user = userRepository.findByCorreo(correo)
                .orElseThrow(() -> {
                    System.out.println("Usuario NO encontrado en BD");
                    return new UsernameNotFoundException("Usuario no encontrado");
                });

        System.out.println("Usuario encontrado: " + user.getCorreo());
        System.out.println("Estado: " + user.getEstado());
        System.out.println("Password hash BD: " + user.getPassword());

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        System.out.println(
                "Password 123456 coincide: " +
                        encoder.matches("123456", user.getPassword())
        );

        if (user.getEstado() == null || user.getEstado() == 0) {

            System.out.println("Usuario inactivo");

            throw new UsernameNotFoundException("Usuario inactivo");
        }

        String roleName = user.getRole() != null
                ? user.getRole().getNombre()
                : "AGENTE";

        System.out.println("Rol: " + roleName);

        return new CustomUserPrincipal(
                user,
                List.of(new SimpleGrantedAuthority("ROLE_" + roleName))
        );
    }
}