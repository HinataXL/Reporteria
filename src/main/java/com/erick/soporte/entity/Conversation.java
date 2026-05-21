package com.erick.soporte.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;

    @Column(name = "cliente_nombre", nullable = false)
    private String clienteNombre;

    @Column(name = "cliente_telefono")
    private String clienteTelefono;

    @Column(name = "cliente_correo")
    private String clienteCorreo;

    @Column(nullable = false)
    private String asunto;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "tiempo_gestion_minutos")
    private Integer tiempoGestionMinutos = 0;

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio = LocalDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "status_id")
    private Long statusId;

    @Column(name = "priority_id")
    private Long priorityId;

    // Getters y Setters

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
    }

    public String getClienteTelefono() {
        return clienteTelefono;
    }

    public void setClienteTelefono(String clienteTelefono) {
        this.clienteTelefono = clienteTelefono;
    }

    public String getClienteCorreo() {
        return clienteCorreo;
    }

    public void setClienteCorreo(String clienteCorreo) {
        this.clienteCorreo = clienteCorreo;
    }

    public String getAsunto() {
        return asunto;
    }

    public void setAsunto(String asunto) {
        this.asunto = asunto;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Integer getTiempoGestionMinutos() {
        return tiempoGestionMinutos;
    }

    public void setTiempoGestionMinutos(Integer tiempoGestionMinutos) {
        this.tiempoGestionMinutos = tiempoGestionMinutos;
    }

    public LocalDateTime getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDateTime fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    public Long getStatusId() {
        return statusId;
    }

    public void setStatusId(Long statusId) {
        this.statusId = statusId;
    }

    public Long getPriorityId() {
        return priorityId;
    }

    public void setPriorityId(Long priorityId) {
        this.priorityId = priorityId;
    }
}