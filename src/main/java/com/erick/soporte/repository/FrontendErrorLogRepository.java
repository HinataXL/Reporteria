package com.erick.soporte.repository;

import com.erick.soporte.entity.FrontendErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FrontendErrorLogRepository extends JpaRepository<FrontendErrorLog, Long> {

    List<FrontendErrorLog> findTop150ByOrderByCreatedAtDesc();

    long countByLevelIgnoreCase(String level);
}
