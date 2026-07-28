package com.erick.soporte.repository;

import com.erick.soporte.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByCorreo(String correo);

    @Query(value = """
            SELECT setval(
                pg_get_serial_sequence('users', 'id'),
                GREATEST(COALESCE((SELECT MAX(id) FROM users), 0) + 1, 1),
                false
            )
            """, nativeQuery = true)
    Long syncUserIdSequence();
}
