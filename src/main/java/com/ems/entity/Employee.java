package com.ems.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "employees")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Employee extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 50) private String firstName;
    @Column(nullable = false, length = 50) private String lastName;
    @Column(nullable = false, unique = true) private String email;
    @Column(nullable = false, unique = true) private String phoneNumber;
    @Column(nullable = false) private BigDecimal salary;
    @Column(nullable = false) private LocalDate hireDate;
    @Column(name = "profile_photo") private String profilePhoto;
    @Column(name = "kra_pin", length = 11) private String kraPin;
    @Column(name = "nssf_number", length = 50) private String nssfNumber;
    @Column(name = "sha_number", length = 50) private String shaNumber;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;
    @Version @Column(nullable = false) private long version;
}
