package com.ems.dto.request;

import com.ems.entity.Role;
import lombok.Data;

@Data
public class UserRequest {

    private Long employeeId;

    private String username;

    private String password;

    private Role role;

}