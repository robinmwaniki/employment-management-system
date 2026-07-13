package com.ems.service.interfaces;

import com.ems.dto.response.SystemLogResponse;
import org.springframework.data.domain.Page;

public interface SystemLogService {

    void saveLog(
            String username,
            String role,
            String action,
            String description);

    Page<SystemLogResponse> getLogs(
            int page,
            int size);

}