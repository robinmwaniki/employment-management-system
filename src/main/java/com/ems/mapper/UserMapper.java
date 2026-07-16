package com.ems.mapper;

import com.ems.dto.response.UserResponse;
import com.ems.entity.User;

public class UserMapper {

    public static UserResponse toResponse(User user) {

        UserResponse.UserResponseBuilder builder = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .enabled(user.getEnabled());

        if (user.getEmployee() != null) {

            builder.employeeId(user.getEmployee().getId());

            builder.employeeName(
                    user.getEmployee().getFirstName()
                            + " "
                            + user.getEmployee().getLastName());

        } else {

            builder.employeeName("Not Assigned");
        }

        return builder.build();
    }
}