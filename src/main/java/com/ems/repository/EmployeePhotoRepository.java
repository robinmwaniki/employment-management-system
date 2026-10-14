package com.ems.repository;

import com.ems.entity.EmployeePhoto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeePhotoRepository
        extends JpaRepository<EmployeePhoto, Long> {

}
