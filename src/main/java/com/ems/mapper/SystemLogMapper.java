package com.ems.mapper;

import com.ems.dto.response.SystemLogResponse;
import com.ems.entity.SystemLog;

public class SystemLogMapper {

    private SystemLogMapper() {
    }

    public static SystemLogResponse toResponse(SystemLog log) {

        return SystemLogResponse.builder()
                .id(log.getId())
                .username(log.getUsername())
                .role(log.getRole())
                .action(log.getAction())
                .description(log.getDescription())
                .createdAt(log.getCreatedAt())
                .build();
    }

}