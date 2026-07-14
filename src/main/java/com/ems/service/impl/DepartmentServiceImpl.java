package com.ems.service.impl;

import com.ems.dto.request.DepartmentRequest;
import com.ems.dto.response.DepartmentResponse;
import com.ems.entity.Department;
import com.ems.exception.DuplicateResourceException;
import com.ems.exception.ResourceNotFoundException;
import com.ems.mapper.DepartmentMapper;
import com.ems.repository.DepartmentRepository;
import com.ems.service.interfaces.DepartmentService;
import com.ems.service.interfaces.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final SystemLogService systemLogService;

    @Override
    public DepartmentResponse createDepartment(DepartmentRequest request) {

        if (departmentRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException(
                    "Department name already exists.");
        }

        if (departmentRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException(
                    "Department code already exists.");
        }

        Department department =
                DepartmentMapper.toEntity(request);

        Department savedDepartment =
                departmentRepository.save(department);

        systemLogService.saveLog(
                "Administrator",
                "ADMIN",
                "CREATE DEPARTMENT",
                "Created department: " + savedDepartment.getName());

        return DepartmentMapper.toResponse(savedDepartment);
    }

    @Override
    public List<DepartmentResponse> getAllDepartments() {

        return departmentRepository.findAll()
                .stream()
                .map(DepartmentMapper::toResponse)
                .toList();
    }

    @Override
    public DepartmentResponse getDepartmentById(Long id) {

        Department department =
                departmentRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Department not found with id: " + id));

        return DepartmentMapper.toResponse(department);
    }

    @Override
    public DepartmentResponse updateDepartment(
            Long id,
            DepartmentRequest request) {

        Department department =
                departmentRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Department not found with id: " + id));

        department.setName(request.getName());
        department.setCode(request.getCode());

        Department updatedDepartment =
                departmentRepository.save(department);

        systemLogService.saveLog(
                "Administrator",
                "ADMIN",
                "UPDATE DEPARTMENT",
                "Updated department: " + updatedDepartment.getName());

        return DepartmentMapper.toResponse(updatedDepartment);
    }

    @Override
    public void deleteDepartment(Long id) {

        Department department =
                departmentRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Department not found with id: " + id));

        String departmentName = department.getName();

        departmentRepository.delete(department);

        systemLogService.saveLog(
                "Administrator",
                "ADMIN",
                "DELETE DEPARTMENT",
                "Deleted department: " + departmentName);
    }
}