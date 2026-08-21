package com.erick.soporte.service;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PasskeyEnrollmentService {

    private final JdbcTemplate jdbcTemplate;
    private final Set<String> requiredRoles;

    public PasskeyEnrollmentService(
            JdbcTemplate jdbcTemplate,
            @Value("${app.passkeys.required-roles:ADMIN,SUPERVISOR,AGENTE}") String requiredRoles
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.requiredRoles = Arrays.stream(requiredRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    public boolean hasPasskey(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }

        try {
            Integer count = jdbcTemplate.queryForObject("""
                    select count(*)
                    from user_credentials credential
                    join user_entities entity
                        on entity.id = credential.user_entity_user_id
                    where lower(entity.name) = lower(?)
                    """, Integer.class, username);

            return count != null && count > 0;
        } catch (DataAccessException exception) {
            return false;
        }
    }

    public boolean isRequiredForRole(String roleName) {
        if (requiredRoles.isEmpty()) {
            return false;
        }

        return requiredRoles.contains(roleName == null ? "" : roleName.toUpperCase());
    }

    public boolean requiresPasskey(String username, String roleName) {
        return isRequiredForRole(roleName) && !hasPasskey(username);
    }

    public List<PasskeyCredentialView> findPasskeys(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }

        try {
            return jdbcTemplate.query("""
                    select credential.credential_id,
                           coalesce(nullif(credential.label, ''), 'Passkey sin nombre') as label,
                           credential.created,
                           credential.last_used
                    from user_credentials credential
                    join user_entities entity
                        on entity.id = credential.user_entity_user_id
                    where lower(entity.name) = lower(?)
                    order by credential.created desc nulls last, credential.credential_id
                    """, (rs, rowNum) -> new PasskeyCredentialView(
                    rs.getString("credential_id"),
                    rs.getString("label"),
                    rs.getObject("created", LocalDateTime.class),
                    rs.getObject("last_used", LocalDateTime.class)
            ), username);
        } catch (DataAccessException exception) {
            return List.of();
        }
    }

    public Map<String, PasskeyUserSummary> summariesByUsername() {
        try {
            return jdbcTemplate.query("""
                    select lower(entity.name) as username,
                           count(credential.credential_id) as total,
                           max(credential.last_used) as last_used
                    from user_entities entity
                    left join user_credentials credential
                        on credential.user_entity_user_id = entity.id
                    group by lower(entity.name)
                    """, rs -> {
                Map<String, PasskeyUserSummary> summaries = new java.util.HashMap<>();
                while (rs.next()) {
                    summaries.put(
                            rs.getString("username"),
                            new PasskeyUserSummary(
                                    rs.getInt("total"),
                                    rs.getObject("last_used", LocalDateTime.class)
                            )
                    );
                }
                return summaries;
            });
        } catch (DataAccessException exception) {
            return Map.of();
        }
    }

    public boolean updateLabel(String username, String credentialId, String label) {
        if (username == null || credentialId == null || credentialId.isBlank()) {
            return false;
        }

        String safeLabel = label == null || label.isBlank()
                ? "Passkey principal"
                : label.trim();

        int updated = jdbcTemplate.update("""
                update user_credentials credential
                set label = ?
                from user_entities entity
                where entity.id = credential.user_entity_user_id
                  and lower(entity.name) = lower(?)
                  and credential.credential_id = ?
                """, safeLabel, username, credentialId);

        return updated > 0;
    }

    public boolean deletePasskey(String username, String credentialId) {
        if (username == null || credentialId == null || credentialId.isBlank()) {
            return false;
        }

        int deleted = jdbcTemplate.update("""
                delete from user_credentials credential
                using user_entities entity
                where entity.id = credential.user_entity_user_id
                  and lower(entity.name) = lower(?)
                  and credential.credential_id = ?
                """, username, credentialId);

        return deleted > 0;
    }

    public int deleteAllForUser(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }

        return jdbcTemplate.update("""
                delete from user_credentials credential
                using user_entities entity
                where entity.id = credential.user_entity_user_id
                  and lower(entity.name) = lower(?)
                """, username);
    }

    public record PasskeyCredentialView(
            String credentialId,
            String label,
            LocalDateTime created,
            LocalDateTime lastUsed
    ) {
    }

    public record PasskeyUserSummary(
            int total,
            LocalDateTime lastUsed
    ) {
        public boolean hasPasskey() {
            return total > 0;
        }
    }
}
