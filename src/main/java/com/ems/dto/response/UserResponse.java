package com.ems.dto.response;

import com.ems.entity.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {

    private Long id;

    private Long employeeId;

    private String employeeName;

    private String username;

    private Role role;

}