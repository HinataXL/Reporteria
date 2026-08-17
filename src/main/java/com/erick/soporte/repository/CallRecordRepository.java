package com.erick.soporte.repository;

import com.erick.soporte.entity.CallRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CallRecordRepository extends JpaRepository<CallRecord, Long>, JpaSpecificationExecutor<CallRecord> {
}
