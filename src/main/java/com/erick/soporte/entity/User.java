package com.erick.soporte.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    private Long id;

    private String nombre;

    private String apellido;

    private String correo;

    private String password;

    private Integer estado;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public String getCorreo() {
        return correo;
    }

    public String getPassword() {
        return password;
    }

    public Integer getEstado() {
        return estado;
    }

    public Role getRole() {
        return role;
    }
}