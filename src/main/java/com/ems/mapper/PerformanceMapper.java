package com.ems.mapper;

import com.ems.dto.response.PerformanceResponse;
import com.ems.entity.PerformanceReview;

public class PerformanceMapper {

    private PerformanceMapper() {
    }

    public static PerformanceResponse toResponse(
            PerformanceReview review) {

        return PerformanceResponse.builder()
                .id(review.getId())
                .employeeId(review.getEmployee().getId())
                .employeeName(
                        review.getEmployee().getFirstName()
                                + " "
                                + review.getEmployee().getLastName())
                .reviewDate(review.getReviewDate())
                .reviewer(review.getReviewer())
                .rating(review.getRating())
                .comments(review.getComments())
                .goals(review.getGoals())
                .build();
    }

}