package com.ems.service.impl;

import com.ems.entity.Employee;
import com.ems.entity.LeaveRequestEntity;
import com.ems.entity.LeaveStatus;
import com.ems.entity.Payroll;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.LeaveRepository;
import com.ems.repository.PayrollRepository;
import com.ems.service.interfaces.EmailService;
import com.ems.service.interfaces.SystemLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for employee self-service ownership boundaries.
 */
@ExtendWith(MockitoExtension.class)
class SelfServiceOwnershipTests {

    @Mock
    private PayrollRepository payrollRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private SystemLogService systemLogService;

    @Mock
    private EmailService emailService;

    @Mock
    private KenyanPayrollCalculator payrollCalculator;

    @InjectMocks
    private PayrollServiceImpl payrollService;

    @Mock
    private LeaveRepository leaveRepository;

    @InjectMocks
    private LeaveServiceImpl leaveService;

    @Test
    void employeeCannotReadAnotherEmployeesPayroll() {
        Employee authenticatedEmployee = Employee.builder().id(10L).build();
        Employee payrollOwner = Employee.builder().id(20L).build();
        Payroll payroll = Payroll.builder()
                .id(501L)
                .employee(payrollOwner)
                .build();

        when(employeeRepository.findById(10L))
                .thenReturn(Optional.of(authenticatedEmployee));
        when(payrollRepository.findById(501L))
                .thenReturn(Optional.of(payroll));

        assertThrows(ResourceNotFoundException.class,
                () -> payrollService.getEmployeePayroll(10L, 501L));
    }

    @Test
    void employeeCannotCancelAnotherEmployeesLeaveRequest() {
        Employee leaveOwner = Employee.builder().id(20L).build();
        LeaveRequestEntity leave = LeaveRequestEntity.builder()
                .id(601L)
                .employee(leaveOwner)
                .status(LeaveStatus.PENDING)
                .build();

        when(leaveRepository.findById(601L))
                .thenReturn(Optional.of(leave));

        assertThrows(ResourceNotFoundException.class,
                () -> leaveService.cancelOwnLeave(601L, 10L));

        verify(leaveRepository, never()).saveAndFlush(leave);
        verify(systemLogService, never()).saveLog(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(emailService, never()).sendEmail(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
