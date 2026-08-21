package com.erick.soporte.service;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PasskeyEnrollmentService {

    private final JdbcTemplate jdbcTemplate;

    public PasskeyEnrollmentService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
}
