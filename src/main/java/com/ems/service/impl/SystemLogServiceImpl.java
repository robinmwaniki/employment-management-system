package com.ems.service.impl;

import com.ems.dto.response.SystemLogResponse;
import com.ems.entity.SystemLog;
import com.ems.mapper.SystemLogMapper;
import com.ems.repository.SystemLogRepository;
import com.ems.service.interfaces.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemLogServiceImpl implements SystemLogService {

    private final SystemLogRepository systemLogRepository;

    @Override
    public void saveLog(
            String username,
            String role,
            String action,
            String description) {

        SystemLog log = SystemLog.builder()
                .username(username)
                .role(role)
                .action(action)
                .description(description)
                .build();

        systemLogRepository.save(log);
    }

    @Override
    public Page<SystemLogResponse> getLogs(
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size);

        return systemLogRepository
                .findAllByOrderByCreatedAtDesc(pageable)
                .map(SystemLogMapper::toResponse);
    }
}