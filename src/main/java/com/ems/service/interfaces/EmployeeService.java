package com.ems.service.interfaces;

import com.ems.dto.request.EmployeeRequest;
import com.ems.dto.request.ProfileUpdateRequest;
import com.ems.dto.response.EmployeeResponse;
import org.springframework.data.domain.Page;

public interface EmployeeService {

    EmployeeResponse createEmployee(EmployeeRequest request);

    Page<EmployeeResponse> getAllEmployees(int page, int size);

    Page<EmployeeResponse> searchEmployees(String keyword, int page, int size);

    EmployeeResponse getEmployeeById(Long id);

    EmployeeResponse updateEmployee(Long id, EmployeeRequest request);

    /**
     * Self-service update: changes only name, email and phone number.
     */
    EmployeeResponse updateOwnProfile(Long employeeId, ProfileUpdateRequest request);

    void deleteEmployee(Long id);
}
