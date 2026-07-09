package com.ems.service.impl;

import com.ems.dto.request.PerformanceRequest;
import com.ems.dto.response.PerformanceResponse;
import com.ems.entity.Employee;
import com.ems.entity.Performance;
import com.ems.exception.ResourceNotFoundException;
import com.ems.mapper.PerformanceMapper;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.PerformanceRepository;
import com.ems.service.interfaces.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PerformanceServiceImpl implements PerformanceService {

    private final PerformanceRepository performanceRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public PerformanceResponse addReview(PerformanceRequest request) {

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id: " + request.getEmployeeId()));

        Performance performance = Performance.builder()
                .employee(employee)
                .reviewDate(LocalDate.now())
                .score(request.getScore())
                .managerComments(request.getManagerComments())
                .employeeComments(request.getEmployeeComments())
                .build();

        Performance savedPerformance = performanceRepository.save(performance);

        return PerformanceMapper.toResponse(savedPerformance);
    }

    @Override
    public PerformanceResponse getReview(Long id) {

        Performance performance = performanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Performance review not found with id: " + id));

        return PerformanceMapper.toResponse(performance);
    }

    @Override
    public List<PerformanceResponse> getEmployeeReviews(Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id: " + employeeId));

        return performanceRepository.findByEmployee(employee)
                .stream()
                .map(PerformanceMapper::toResponse)
                .collect(Collectors.toList());
    }
}