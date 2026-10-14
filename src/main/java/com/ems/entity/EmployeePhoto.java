package com.ems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The profile photo of one employee. Kept in its own table so that employee
 * lists never load image bytes.
 */
@Entity
@Table(name = "employee_photos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeePhoto {

    /** Same value as the employee's id: at most one photo per employee. */
    @Id
    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
