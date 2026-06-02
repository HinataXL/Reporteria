package com.erick.soporte.repository;

import com.erick.soporte.entity.IssueType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueTypeRepository extends JpaRepository<IssueType, Integer> {

    List<IssueType> findByActivoTrueOrderByNombreAsc();
}