package com.ems.repository;

import com.ems.entity.Employee;
import com.ems.entity.Performance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    List<Performance> findByEmployee(Employee employee);

}