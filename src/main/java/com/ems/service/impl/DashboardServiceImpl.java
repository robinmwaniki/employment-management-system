package com.ems.service.impl;

import com.ems.dto.response.DashboardResponse;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.service.interfaces.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    public DashboardResponse getDashboardStatistics() {

        LocalDate firstDayOfMonth =
                LocalDate.now().withDayOfMonth(1);

        BigDecimal totalPayroll = employeeRepository.getTotalPayroll();
        BigDecimal highestSalary = employeeRepository.getHighestSalary();
        BigDecimal lowestSalary = employeeRepository.getLowestSalary();
        BigDecimal averageSalary = employeeRepository.getAverageSalary();

        return DashboardResponse.builder()
                .totalEmployees(employeeRepository.count())
                .totalDepartments(departmentRepository.count())
                .employeesHiredThisMonth(
                        employeeRepository.countByHireDateAfter(firstDayOfMonth)
                )
                .totalPayroll(
                        totalPayroll == null ? BigDecimal.ZERO : totalPayroll
                )
                .highestSalary(
                        highestSalary == null ? BigDecimal.ZERO : highestSalary
                )
                .lowestSalary(
                        lowestSalary == null ? BigDecimal.ZERO : lowestSalary
                )
                .averageSalary(
                        averageSalary == null ? BigDecimal.ZERO : averageSalary
                )
                .build();
    }
}