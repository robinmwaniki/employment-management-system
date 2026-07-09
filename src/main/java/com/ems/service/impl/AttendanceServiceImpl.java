package com.ems.service.impl;

import com.ems.dto.request.AttendanceRequest;
import com.ems.dto.response.AttendanceResponse;
import com.ems.entity.Attendance;
import com.ems.entity.Employee;
import com.ems.exception.ResourceNotFoundException;
import com.ems.mapper.AttendanceMapper;
import com.ems.repository.AttendanceRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.service.interfaces.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public AttendanceResponse checkIn(AttendanceRequest request) {

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found with id: "
                                        + request.getEmployeeId()));

        LocalDate today = LocalDate.now();

        attendanceRepository.findByEmployeeAndAttendanceDate(employee, today)
                .ifPresent(a -> {
                    throw new IllegalArgumentException(
                            "Employee has already checked in today.");
                });

        LocalDateTime checkInTime = LocalDateTime.now();

        boolean late = checkInTime.toLocalTime()
                .isAfter(LocalTime.of(8, 0));

        Attendance attendance = Attendance.builder()
                .employee(employee)
                .attendanceDate(today)
                .checkInTime(checkInTime)
                .late(late)
                .overtime(false)
                .build();

        Attendance savedAttendance =
                attendanceRepository.save(attendance);

        return AttendanceMapper.toResponse(savedAttendance);
    }

    @Override
    public AttendanceResponse checkOut(Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found with id: "
                                        + employeeId));

        Attendance attendance =
                attendanceRepository.findByEmployeeAndAttendanceDate(
                                employee,
                                LocalDate.now())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Employee has not checked in today."));

        if (attendance.getCheckOutTime() != null) {
            throw new IllegalArgumentException(
                    "Employee has already checked out.");
        }

        LocalDateTime checkOutTime = LocalDateTime.now();

        attendance.setCheckOutTime(checkOutTime);

        Duration workingHours =
                Duration.between(
                        attendance.getCheckInTime(),
                        checkOutTime);

        attendance.setWorkingHours(workingHours);

        attendance.setOvertime(
                workingHours.toHours() > 8
        );

        Attendance updatedAttendance =
                attendanceRepository.save(attendance);

        return AttendanceMapper.toResponse(updatedAttendance);
    }

    @Override
    public Page<AttendanceResponse> getAllAttendance(
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size);

        return attendanceRepository.findAll(pageable)
                .map(AttendanceMapper::toResponse);
    }

    @Override
    public Page<AttendanceResponse> getEmployeeAttendance(
            Long employeeId,
            int page,
            int size) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found"));

        Pageable pageable = PageRequest.of(page, size);

        return attendanceRepository.findByEmployee(employee, pageable)
                .map(AttendanceMapper::toResponse);
    }

    @Override
    public AttendanceResponse getAttendanceById(Long id) {

        Attendance attendance =
                attendanceRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Attendance not found with id: " + id));

        return AttendanceMapper.toResponse(attendance);
    }
}