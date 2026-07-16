package com.ems.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReportResponse {

    private long totalEmployees;

    private long totalDepartments;

    private long totalPayrolls;

    private long totalLeaves;

    private long approvedLeaves;

    private long pendingLeaves;

    private long rejectedLeaves;

    private long totalAttendance;

    private long lateEmployees;

    private long totalAnnouncements;

}