package com.ems.mapper;

import com.ems.dto.response.LeaveResponse;
import com.ems.entity.LeaveRequestEntity;

public class LeaveMapper {

    private LeaveMapper() {
    }

    public static LeaveResponse toResponse(LeaveRequestEntity leave) {

        return LeaveResponse.builder()
                .id(leave.getId())
                .employeeId(leave.getEmployee().getId())
                .employeeName(
                        leave.getEmployee().getFirstName() + " " +
                                leave.getEmployee().getLastName()
                )
                .leaveType(leave.getLeaveType())
                .startDate(leave.getStartDate())
                .endDate(leave.getEndDate())
                .reason(leave.getReason())
                .status(leave.getStatus())
                .appliedDate(leave.getAppliedDate())
                .build();
    }
}