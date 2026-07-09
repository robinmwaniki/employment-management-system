package com.ems.mapper;

import com.ems.dto.request.DepartmentRequest;
import com.ems.dto.response.DepartmentResponse;
import com.ems.entity.Department;

public class DepartmentMapper {

    public static Department toEntity(DepartmentRequest request) {

        return Department.builder()
                .name(request.getName())
                .code(request.getCode())
                .build();
    }

    public static DepartmentResponse toResponse(Department department) {

        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .code(department.getCode())
                .build();
    }
}