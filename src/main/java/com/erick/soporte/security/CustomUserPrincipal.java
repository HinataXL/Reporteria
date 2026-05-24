package com.erick.soporte.security;

import com.erick.soporte.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class CustomUserPrincipal implements UserDetails {

    private final User user;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserPrincipal(User user, Collection<? extends GrantedAuthority> authorities) {
        this.user = user;
        this.authorities = authorities;
    }

    public Long getId() {
        return user.getId();
    }

    public String getNombre() {
        return user.getNombre();
    }

    public String getApellido() {
        return user.getApellido();
    }

    public String getNombreCompleto() {
        return user.getNombre() + " " + user.getApellido();
    }

    public String getCorreo() {
        return user.getCorreo();
    }

    public String getRol() {
        return user.getRole() != null ? user.getRole().getNombre() : "AGENTE";
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getCorreo();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getEstado() != null && user.getEstado() == 1;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getEstado() != null && user.getEstado() == 1;
    }
}