package com.ems.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "company_profile")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompanyProfile extends BaseEntity {
    @Id private Integer id;
    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;
    @Column(name = "kra_pin", length = 11) private String kraPin;
    @Column(name = "nssf_employer_number", length = 50) private String nssfEmployerNumber;
    @Column(name = "sha_employer_number", length = 50) private String shaEmployerNumber;
    @Column(length = 300) private String address;
    @Column(name = "phone_number", length = 30) private String phoneNumber;
    @Column(length = 255) private String email;
    @Version @Column(nullable = false) private long version;
}
