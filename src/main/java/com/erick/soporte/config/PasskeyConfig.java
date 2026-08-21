package com.erick.soporte.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

@Configuration
public class PasskeyConfig {

    @Bean
    public PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository(
            JdbcOperations jdbcOperations
    ) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbcOperations);
    }

    @Bean
    public UserCredentialRepository userCredentialRepository(JdbcOperations jdbcOperations) {
        return new JdbcUserCredentialRepository(jdbcOperations);
    }

    @Bean
    public ApplicationRunner passkeySchemaInitializer(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("""
                    create table if not exists user_entities (
                        id varchar(255) primary key,
                        name varchar(255) not null unique,
                        display_name varchar(255) not null
                    )
                    """);

            jdbcTemplate.execute("""
                    create table if not exists user_credentials (
                        credential_id varchar(255) primary key,
                        user_entity_user_id varchar(255) not null references user_entities(id) on delete cascade,
                        public_key bytea not null,
                        signature_count bigint not null,
                        uv_initialized boolean not null,
                        backup_eligible boolean,
                        authenticator_transports varchar(255),
                        public_key_credential_type varchar(100) not null,
                        backup_state boolean,
                        attestation_object bytea,
                        attestation_client_data_json bytea,
                        created timestamp,
                        last_used timestamp,
                        label varchar(255)
                    )
                    """);

            jdbcTemplate.execute("""
                    create index if not exists idx_user_credentials_user_entity
                    on user_credentials(user_entity_user_id)
                    """);
        };
    }
}
