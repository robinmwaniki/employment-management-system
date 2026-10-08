package com.ems.service.impl;

import com.ems.dto.response.SystemLogResponse;
import com.ems.entity.SystemLog;
import com.ems.mapper.SystemLogMapper;
import com.ems.repository.SystemLogRepository;
import com.ems.service.interfaces.SystemLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemLogServiceImpl implements SystemLogService {

    /** Matches the length of the description column. */
    private static final int MAX_DESCRIPTION = 1000;

    private final SystemLogRepository systemLogRepository;

    /**
     * Records an audit entry. When a user is signed in, the actor is taken from
     * the security context and the supplied username and role are ignored, so
     * callers cannot record the wrong person. A failure to write the audit entry
     * is logged and never breaks the business operation that triggered it.
     */
    @Override
    public void saveLog(
            String username,
            String role,
            String action,
            String description) {

        try {

            String actor = username;
            String actorRole = role;

            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null
                    && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken)) {

                actor = authentication.getName();

                actorRole = authentication.getAuthorities()
                        .stream()
                        .findFirst()
                        .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                        .orElse(role);
            }

            SystemLog log = SystemLog.builder()
                    .username(actor)
                    .role(actorRole)
                    .action(action)
                    .description(abbreviate(description))
                    .build();

            systemLogRepository.save(log);

        } catch (RuntimeException ex) {
            SystemLogServiceImpl.log.error(
                    "Could not write audit log entry for action {}", action, ex);
        }
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

    private String abbreviate(String text) {

        if (text == null || text.length() <= MAX_DESCRIPTION) {
            return text;
        }

        return text.substring(0, MAX_DESCRIPTION - 3) + "...";
    }
}