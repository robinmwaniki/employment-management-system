package com.ems.mapper;

import com.ems.dto.response.AttendanceResponse;
import com.ems.entity.Attendance;
import com.ems.entity.AttendanceStatus;

public class AttendanceMapper {

    private AttendanceMapper() {
    }

    public static AttendanceResponse toResponse(Attendance attendance) {

        AttendanceStatus status;

        if (attendance.getCheckInTime() == null) {

            status = AttendanceStatus.ABSENT;

        }
        else if (attendance.isLate()) {

            status = AttendanceStatus.LATE;

        }
        else if (attendance.getCheckOutTime() == null) {

            status = AttendanceStatus.CHECKED_IN;

        }
        else {

            status = AttendanceStatus.PRESENT;

        }

        return AttendanceResponse.builder()
                .id(attendance.getId())
                .employeeId(attendance.getEmployee().getId())
                .employeeName(
                        attendance.getEmployee().getFirstName()
                                + " "
                                + attendance.getEmployee().getLastName())
                .attendanceDate(attendance.getAttendanceDate())
                .checkInTime(attendance.getCheckInTime())
                .checkOutTime(attendance.getCheckOutTime())
                .workingHours(attendance.getWorkingHours())
                .late(attendance.isLate())
                .overtime(attendance.isOvertime())
                .status(status)
                .build();
    }
}