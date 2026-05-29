package com.erick.soporte.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final TwoFactorAuthenticationSuccessHandler successHandler;
    private final TwoFactorFilter twoFactorFilter;

    public SecurityConfig(
            CustomUserDetailsService customUserDetailsService,
            TwoFactorAuthenticationSuccessHandler successHandler,
            TwoFactorFilter twoFactorFilter
    ) {
        this.customUserDetailsService = customUserDetailsService;
        this.successHandler = successHandler;
        this.twoFactorFilter = twoFactorFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authenticationProvider(authenticationProvider())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/img/**").permitAll()

                        .requestMatchers("/2fa/**").authenticated()
                        .requestMatchers("/settings/2fa/**").authenticated()

                        .requestMatchers("/users/**").hasRole("ADMIN")

                        .requestMatchers("/conversations/export/**").hasAnyRole("ADMIN", "SUPERVISOR")
                        .requestMatchers("/conversations/edit/**", "/conversations/update/**").hasAnyRole("ADMIN", "SUPERVISOR")
                        .requestMatchers("/conversations/create", "/conversations/save").hasAnyRole("ADMIN", "SUPERVISOR", "AGENTE")
                        .requestMatchers("/conversations/**").hasAnyRole("ADMIN", "SUPERVISOR", "AGENTE")
                        .requestMatchers("/supervisor/**").hasAnyRole("ADMIN", "SUPERVISOR")
                        .requestMatchers("/api/dashboard/**").hasAnyRole("ADMIN", "SUPERVISOR")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/profile/**").authenticated()

                        .anyRequest().authenticated()
                )

                .formLogin(login -> login
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                );
                http.addFilterBefore(
                twoFactorFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}