package com.erick.soporte.repository;

import com.erick.soporte.entity.SupportClient;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupportClientRepository extends JpaRepository<SupportClient, Long> {

    Optional<SupportClient> findByZohoContactId(String zohoContactId);

    Optional<SupportClient> findFirstByActiveTrueAndEmailIgnoreCase(String email);

    @Query("""
            SELECT client
            FROM SupportClient client
            WHERE client.active = true
              AND (client.phone = :phone OR client.mobile = :phone)
            ORDER BY client.fullName ASC
            """)
    List<SupportClient> findByPhoneOrMobile(@Param("phone") String phone, Pageable pageable);

    @Query("""
            SELECT client
            FROM SupportClient client
            WHERE client.active = true
              AND (
                LOWER(COALESCE(client.fullName, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                OR LOWER(COALESCE(client.email, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                OR LOWER(COALESCE(client.phone, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                OR LOWER(COALESCE(client.mobile, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                OR LOWER(COALESCE(client.accountName, '')) LIKE LOWER(CONCAT('%', :term, '%'))
              )
            ORDER BY client.fullName ASC
            """)
    List<SupportClient> searchActiveClients(@Param("term") String term, Pageable pageable);
}
