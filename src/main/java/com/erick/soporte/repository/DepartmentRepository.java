package com.erick.soporte.repository;

import com.erick.soporte.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Integer> {

    List<Department> findByActivoTrueOrderByNombreAsc();
}