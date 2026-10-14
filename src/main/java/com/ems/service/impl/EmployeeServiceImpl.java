package com.ems.service.impl;

import com.ems.dto.request.EmployeeRequest;
import com.ems.dto.request.ProfileUpdateRequest;
import com.ems.dto.response.EmployeeResponse;
import com.ems.entity.Department;
import com.ems.entity.Employee;
import com.ems.exception.ResourceNotFoundException;
import com.ems.mapper.EmployeeMapper;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.service.interfaces.EmployeeService;
import com.ems.service.interfaces.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final SystemLogService systemLogService;

    @Override
    public EmployeeResponse createEmployee(EmployeeRequest request) {

        if (employeeRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists.");
        }

        if (employeeRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalArgumentException("Phone number already exists.");
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department not found with id: " + request.getDepartmentId()));

        Employee employee = Employee.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .salary(request.getSalary())
                .hireDate(request.getHireDate())
                .department(department)
                .build();

        Employee savedEmployee = employeeRepository.save(employee);

        systemLogService.saveLog(
                "HR Admin",
                "ADMIN",
                "CREATE EMPLOYEE",
                "Created employee "
                        + savedEmployee.getFirstName()
                        + " "
                        + savedEmployee.getLastName());

        return EmployeeMapper.toResponse(savedEmployee);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeResponse> getAllEmployees(int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        return employeeRepository.findAll(pageable)
                .map(EmployeeMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeResponse> searchEmployees(String keyword, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        return employeeRepository
                .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                        keyword,
                        keyword,
                        pageable)
                .map(EmployeeMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeById(Long id) {

        return EmployeeMapper.toResponse(findEmployee(id));
    }

    @Override
    public EmployeeResponse updateEmployee(Long id, EmployeeRequest request) {

        Employee employee = findEmployee(id);

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Department not found with id: " + request.getDepartmentId()));

        requireUniqueContact(id, request.getEmail(), request.getPhoneNumber());

        employee.setFirstName(request.getFirstName());
        employee.setLastName(request.getLastName());
        employee.setEmail(request.getEmail());
        employee.setPhoneNumber(request.getPhoneNumber());
        employee.setSalary(request.getSalary());
        employee.setHireDate(request.getHireDate());
        employee.setDepartment(department);

        Employee updatedEmployee = employeeRepository.save(employee);

        systemLogService.saveLog(
                "HR Admin",
                "ADMIN",
                "UPDATE EMPLOYEE",
                "Updated employee "
                        + updatedEmployee.getFirstName()
                        + " "
                        + updatedEmployee.getLastName());

        return EmployeeMapper.toResponse(updatedEmployee);
    }

    /**
     * Only name, email and phone number are touched, so an employee can never
     * change their own salary, department or hire date through this path.
     */
    @Override
    public EmployeeResponse updateOwnProfile(
            Long employeeId,
            ProfileUpdateRequest request) {

        Employee employee = findEmployee(employeeId);

        requireUniqueContact(employeeId, request.getEmail(), request.getPhoneNumber());

        employee.setFirstName(request.getFirstName().trim());
        employee.setLastName(request.getLastName().trim());
        employee.setEmail(request.getEmail().trim());
        employee.setPhoneNumber(request.getPhoneNumber().trim());

        systemLogService.saveLog(
                "System",
                "EMPLOYEE",
                "UPDATE PROFILE",
                "Employee updated their own contact details");

        return EmployeeMapper.toResponse(employee);
    }

    @Override
    public void deleteEmployee(Long id) {

        Employee employee = findEmployee(id);

        systemLogService.saveLog(
                "HR Admin",
                "ADMIN",
                "DELETE EMPLOYEE",
                "Deleted employee "
                        + employee.getFirstName()
                        + " "
                        + employee.getLastName());

        employeeRepository.delete(employee);
    }

    private Employee findEmployee(Long id) {

        return employeeRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found with id: " + id));
    }

    /**
     * The email and phone number must not belong to a different employee.
     */
    private void requireUniqueContact(
            Long employeeId,
            String email,
            String phoneNumber) {

        employeeRepository.findByEmail(email.trim())
                .filter(other -> !other.getId().equals(employeeId))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Email already exists.");
                });

        employeeRepository.findByPhoneNumber(phoneNumber.trim())
                .filter(other -> !other.getId().equals(employeeId))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Phone number already exists.");
                });
    }
}
