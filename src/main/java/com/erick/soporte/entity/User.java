package com.erick.soporte.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;



    private String nombre;

    private String apellido;

    private String correo;

    private String password;

    private Integer estado;

    private String telefono;

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
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public void setCorreo(String correo) { this.correo = correo; }
    public void setPassword(String password) { this.password = password; }
    public void setEstado(Integer estado) { this.estado = estado; }
    public void setRole(Role role) { this.role = role; }
    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }
}