package com.erick.soporte.service;

import com.erick.soporte.entity.AuditLog;
import com.erick.soporte.repository.AuditLogRepository;
import com.erick.soporte.security.CustomUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void registrar(
            String accion,
            String modulo,
            String descripcion,
            Authentication authentication,
            HttpServletRequest request
    ) {
        AuditLog log = new AuditLog();

        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal user) {
            log.setUsuarioId(user.getId());
            log.setUsuarioNombre(user.getNombreCompleto());
            log.setUsuarioCorreo(user.getCorreo());
            log.setRol(user.getRol());
        }

        log.setAccion(accion);
        log.setModulo(modulo);
        log.setDescripcion(descripcion);
        log.setIp(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));

        auditLogRepository.save(log);
    }
}