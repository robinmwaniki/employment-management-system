package com.ems.service.impl;

import com.ems.dto.request.LeaveRequest;
import com.ems.dto.response.LeaveResponse;
import com.ems.entity.Employee;
import com.ems.entity.LeaveRequestEntity;
import com.ems.entity.LeaveStatus;
import com.ems.exception.ResourceNotFoundException;
import com.ems.mapper.LeaveMapper;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.LeaveRepository;
import com.ems.service.interfaces.EmailService;
import com.ems.service.interfaces.LeaveService;
import com.ems.service.interfaces.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class LeaveServiceImpl implements LeaveService {

    private final LeaveRepository leaveRepository;
    private final EmployeeRepository employeeRepository;
    private final SystemLogService systemLogService;
    private final EmailService emailService;

    @Override
    public LeaveResponse applyLeave(LeaveRequest request) {

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found with id: " + request.getEmployeeId()));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException(
                    "End date cannot be before the start date.");
        }

        if (leaveRepository
                .existsByEmployeeAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employee,
                        request.getEndDate(),
                        request.getStartDate())) {

            throw new IllegalArgumentException(
                    "Employee already has a leave request during this period.");
        }

        LeaveRequestEntity leave = LeaveRequestEntity.builder()
                .employee(employee)
                .leaveType(request.getLeaveType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .reason(request.getReason())
                .status(LeaveStatus.PENDING)
                .appliedDate(LocalDate.now())
                .build();

        LeaveRequestEntity savedLeave = leaveRepository.save(leave);

        systemLogService.saveLog(
                employee.getFirstName() + " " + employee.getLastName(),
                "EMPLOYEE",
                "APPLY LEAVE",
                "Applied for " + request.getLeaveType() + " leave");

        emailService.sendEmail(
                employee.getEmail(),
                "Leave Request Submitted",
                "Dear "
                        + employee.getFirstName()
                        + ",\n\n"
                        + "Your "
                        + request.getLeaveType()
                        + " leave request has been submitted successfully.\n\n"
                        + "Status: PENDING\n"
                        + "Start Date: " + request.getStartDate()
                        + "\nEnd Date: " + request.getEndDate()
                        + "\n\nHR will review your request.");

        return LeaveMapper.toResponse(savedLeave);
    }

    @Override
    public Page<LeaveResponse> getAllLeaves(int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        return leaveRepository.findAll(pageable)
                .map(LeaveMapper::toResponse);
    }

    @Override
    public LeaveResponse getLeaveById(Long id) {

        LeaveRequestEntity leave = leaveRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Leave request not found with id: " + id));

        return LeaveMapper.toResponse(leave);
    }

    @Override
    public LeaveResponse approveLeave(Long id) {

        LeaveRequestEntity leave = leaveRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Leave request not found with id: " + id));

        leave.setStatus(LeaveStatus.APPROVED);

        LeaveRequestEntity saved = leaveRepository.save(leave);

        systemLogService.saveLog(
                "HR Admin",
                "ADMIN",
                "APPROVE LEAVE",
                "Approved leave for "
                        + leave.getEmployee().getFirstName()
                        + " "
                        + leave.getEmployee().getLastName());

        emailService.sendEmail(
                leave.getEmployee().getEmail(),
                "Leave Approved",
                "Congratulations "
                        + leave.getEmployee().getFirstName()
                        + ",\n\n"
                        + "Your leave request has been APPROVED.\n\n"
                        + "Start Date: " + leave.getStartDate()
                        + "\nEnd Date: " + leave.getEndDate());

        return LeaveMapper.toResponse(saved);
    }

    @Override
    public LeaveResponse rejectLeave(Long id) {

        LeaveRequestEntity leave = leaveRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Leave request not found with id: " + id));

        leave.setStatus(LeaveStatus.REJECTED);

        LeaveRequestEntity saved = leaveRepository.save(leave);

        systemLogService.saveLog(
                "HR Admin",
                "ADMIN",
                "REJECT LEAVE",
                "Rejected leave for "
                        + leave.getEmployee().getFirstName()
                        + " "
                        + leave.getEmployee().getLastName());

        emailService.sendEmail(
                leave.getEmployee().getEmail(),
                "Leave Rejected",
                "Dear "
                        + leave.getEmployee().getFirstName()
                        + ",\n\n"
                        + "Unfortunately your leave request has been REJECTED.\n\n"
                        + "Please contact HR for more information.");

        return LeaveMapper.toResponse(saved);
    }

    @Override
    public LeaveResponse cancelLeave(Long id) {

        LeaveRequestEntity leave = leaveRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Leave request not found with id: " + id));

        leave.setStatus(LeaveStatus.CANCELLED);

        LeaveRequestEntity saved = leaveRepository.save(leave);

        systemLogService.saveLog(
                leave.getEmployee().getFirstName()
                        + " "
                        + leave.getEmployee().getLastName(),
                "EMPLOYEE",
                "CANCEL LEAVE",
                "Cancelled leave request");

        emailService.sendEmail(
                leave.getEmployee().getEmail(),
                "Leave Cancelled",
                "Dear "
                        + leave.getEmployee().getFirstName()
                        + ",\n\n"
                        + "Your leave request has been cancelled successfully.");

        return LeaveMapper.toResponse(saved);
    }

    @Override
    public Page<LeaveResponse> getEmployeeLeaves(
            Long employeeId,
            int page,
            int size) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found with id: " + employeeId));

        Pageable pageable = PageRequest.of(page, size);

        return leaveRepository
                .findByEmployee(employee, pageable)
                .map(LeaveMapper::toResponse);
    }
}