package com.ems.service.interfaces;

import com.ems.dto.request.AttendanceRequest;
import com.ems.dto.response.AttendanceResponse;
import org.springframework.data.domain.Page;

public interface AttendanceService {

    AttendanceResponse checkIn(AttendanceRequest request);

    AttendanceResponse checkOut(Long employeeId);

    Page<AttendanceResponse> getAllAttendance(int page, int size);

    AttendanceResponse getAttendanceById(Long id);

    Page<AttendanceResponse> getEmployeeAttendance(
            Long employeeId,
            int page,
            int size);
}