package com.erick.soporte.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.erick.soporte.entity.IssueType;

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

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "agente_nombre")
    private String agenteNombre;

    private Integer issueTypeId;
    private Integer rejectionCodeId;

    private Boolean ticketAperturado = false;
    private String numeroTicket;

    private Boolean conversacionTransferida = false;
    private Integer departmentId;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getAgenteNombre() {
        return agenteNombre;
    }

    public void setAgenteNombre(String agenteNombre) {
        this.agenteNombre = agenteNombre;
    }

    public Integer getIssueTypeId() {
        return issueTypeId;
    }

    public void setIssueTypeId(Integer issueTypeId) {
        this.issueTypeId = issueTypeId;
    }

    public Integer getRejectionCodeId() {
        return rejectionCodeId;
    }

    public void setRejectionCodeId(Integer rejectionCodeId) {
        this.rejectionCodeId = rejectionCodeId;
    }

    public Boolean getTicketAperturado() {
        return ticketAperturado;
    }

    public void setTicketAperturado(Boolean ticketAperturado) {
        this.ticketAperturado = ticketAperturado;
    }

    public String getNumeroTicket() {
        return numeroTicket;
    }

    public void setNumeroTicket(String numeroTicket) {
        this.numeroTicket = numeroTicket;
    }

    public Boolean getConversacionTransferida() {
        return conversacionTransferida;
    }

    public void setConversacionTransferida(Boolean conversacionTransferida) {
        this.conversacionTransferida = conversacionTransferida;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }
    
}