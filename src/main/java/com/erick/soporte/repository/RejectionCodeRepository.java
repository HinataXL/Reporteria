package com.erick.soporte.repository;

import com.erick.soporte.entity.RejectionCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RejectionCodeRepository extends JpaRepository<RejectionCode, Integer> {

    List<RejectionCode> findByActivoTrueOrderByCodigoAsc();
}