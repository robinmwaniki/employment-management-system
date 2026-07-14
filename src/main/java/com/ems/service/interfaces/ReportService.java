package com.ems.service.interfaces;

import com.ems.dto.response.AttendanceResponse;
import com.ems.dto.response.EmployeeResponse;
import com.ems.dto.response.LeaveResponse;
import com.ems.dto.response.PayrollResponse;
import com.ems.dto.response.PerformanceResponse;

import java.util.List;

public interface ReportService {

    List<EmployeeResponse> employeeReport();

    List<PayrollResponse> payrollReport();

    List<LeaveResponse> leaveReport();

    List<AttendanceResponse> attendanceReport();

    List<PerformanceResponse> performanceReport();

}