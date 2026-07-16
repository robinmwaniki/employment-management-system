package com.ems.service.impl;

import com.ems.dto.request.UserRequest;
import com.ems.dto.response.UserResponse;
import com.ems.entity.Employee;
import com.ems.entity.User;
import com.ems.exception.DuplicateResourceException;
import com.ems.exception.ResourceNotFoundException;
import com.ems.mapper.UserMapper;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.UserRepository;
import com.ems.service.interfaces.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemUserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse createUser(UserRequest request) {

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee not found"));

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already exists.");
        }

        if (userRepository.existsByEmployee(employee)) {
            throw new DuplicateResourceException("This employee already has an account.");
        }

        User user = User.builder()
                .employee(employee)
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .enabled(true)
                .build();

        return UserMapper.toResponse(
                userRepository.save(user));
    }

    @Override
    public UserResponse updateUser(Long id,
                                   UserRequest request) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee not found"));

        user.setEmployee(employee);
        user.setUsername(request.getUsername());

        if (request.getPassword() != null &&
                !request.getPassword().isBlank()) {

            user.setPassword(
                    passwordEncoder.encode(request.getPassword()));
        }

        user.setRole(request.getRole());

        return UserMapper.toResponse(
                userRepository.save(user));
    }

    @Override
    public UserResponse getUser(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        return UserMapper.toResponse(user);
    }

    @Override
    public Page<UserResponse> getAllUsers(int page,
                                          int size) {

        return userRepository.findAll(PageRequest.of(page, size))
                .map(UserMapper::toResponse);
    }

    @Override
    public void deleteUser(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        userRepository.delete(user);
    }

    @Override
    public UserResponse enableUser(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        user.setEnabled(true);

        return UserMapper.toResponse(
                userRepository.save(user));
    }

    @Override
    public UserResponse disableUser(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        user.setEnabled(false);

        return UserMapper.toResponse(
                userRepository.save(user));
    }

}