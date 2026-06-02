package com.erick.soporte.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "issue_types")
public class IssueType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String nombre;

    @Column(name = "requiere_codigo_rechazo")
    private Boolean requiereCodigoRechazo = false;

    private Boolean activo = true;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Boolean getRequiereCodigoRechazo() {
        return requiereCodigoRechazo;
    }

    public void setRequiereCodigoRechazo(Boolean requiereCodigoRechazo) {
        this.requiereCodigoRechazo = requiereCodigoRechazo;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}