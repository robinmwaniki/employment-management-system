package com.ems.service.interfaces;

import com.ems.dto.request.UserRequest;
import com.ems.dto.response.UserResponse;
import org.springframework.data.domain.Page;

public interface UserService {

    UserResponse createUser(UserRequest request);

    UserResponse updateUser(Long id, UserRequest request);

    UserResponse getUser(Long id);

    Page<UserResponse> getAllUsers(int page, int size);

    UserResponse enableUser(Long id);

    UserResponse disableUser(Long id);

    void deleteUser(Long id);

}