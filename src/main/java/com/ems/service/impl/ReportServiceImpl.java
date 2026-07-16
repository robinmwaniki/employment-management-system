package com.ems.service.impl;

import com.ems.dto.response.AttendanceResponse;
import com.ems.dto.response.EmployeeResponse;
import com.ems.dto.response.LeaveResponse;
import com.ems.dto.response.PayrollResponse;
import com.ems.dto.response.PerformanceResponse;
import com.ems.mapper.AttendanceMapper;
import com.ems.mapper.EmployeeMapper;
import com.ems.mapper.LeaveMapper;
import com.ems.mapper.PayrollMapper;
import com.ems.mapper.PerformanceMapper;
import com.ems.repository.AttendanceRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.LeaveRepository;
import com.ems.repository.PayrollRepository;
import com.ems.repository.PerformanceRepository;
import com.ems.service.interfaces.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final EmployeeRepository employeeRepository;
    private final PayrollRepository payrollRepository;
    private final LeaveRepository leaveRepository;
    private final AttendanceRepository attendanceRepository;
    private final PerformanceRepository performanceRepository;

    @Override
    public List<EmployeeResponse> employeeReport() {

        return employeeRepository.findAll()
                .stream()
                .map(EmployeeMapper::toResponse)
                .toList();
    }

    @Override
    public List<PayrollResponse> payrollReport() {

        return payrollRepository.findAll()
                .stream()
                .map(PayrollMapper::toResponse)
                .toList();
    }

    @Override
    public List<LeaveResponse> leaveReport() {

        return leaveRepository.findAll()
                .stream()
                .map(LeaveMapper::toResponse)
                .toList();
    }

    @Override
    public List<AttendanceResponse> attendanceReport() {

        return attendanceRepository.findAll()
                .stream()
                .map(AttendanceMapper::toResponse)
                .toList();
    }

    @Override
    public List<PerformanceResponse> performanceReport() {

        return performanceRepository.findAll()
                .stream()
                .map(PerformanceMapper::toResponse)
                .toList();
    }
}