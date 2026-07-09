package com.ems.mapper;

import com.ems.dto.response.PerformanceResponse;
import com.ems.entity.Performance;

public class PerformanceMapper {

    private PerformanceMapper() {
    }

    public static PerformanceResponse toResponse(Performance performance) {

        return PerformanceResponse.builder()
                .id(performance.getId())
                .employeeId(performance.getEmployee().getId())
                .employeeName(
                        performance.getEmployee().getFirstName()
                                + " "
                                + performance.getEmployee().getLastName()
                )
                .reviewDate(performance.getReviewDate())
                .score(performance.getScore())
                .managerComments(performance.getManagerComments())
                .employeeComments(performance.getEmployeeComments())
                .build();
    }
}