package com.ems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.YearMonth;

@Entity
@Table(name = "payroll")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payroll extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** Stored as text, "YYYY-MM". */
    @Convert(converter = YearMonthAttributeConverter.class)
    @Column(nullable = false, length = 7)
    private YearMonth payrollMonth;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal basicSalary;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal houseAllowance;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal transportAllowance;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal bonus;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deductions;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal tax;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal netSalary;

    // Defaults keep older builder call sites compatible with the V6 NOT NULL columns.
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal grossPay = BigDecimal.ZERO.setScale(2);
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxablePay = BigDecimal.ZERO.setScale(2);
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal nssf = BigDecimal.ZERO.setScale(2);
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal shif = BigDecimal.ZERO.setScale(2);
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal housingLevy = BigDecimal.ZERO.setScale(2);
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal employerNssf = BigDecimal.ZERO.setScale(2);
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal employerHousingLevy = BigDecimal.ZERO.setScale(2);
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pensionContribution = BigDecimal.ZERO.setScale(2);
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal personalRelief = BigDecimal.ZERO.setScale(2);
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal insuranceRelief = BigDecimal.ZERO.setScale(2);
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal insurancePremium = BigDecimal.ZERO.setScale(2);
    @Builder.Default
    @Column(nullable = false)
    private boolean residentTaxpayer = true;

    /** Rejects stale concurrent payroll edits. */
    @Version
    @Column(nullable = false)
    private long version;

    /** Explicit accessors used by payroll response mapping. */
    public BigDecimal getHouseAllowance() {
        return houseAllowance;
    }

    public BigDecimal getTransportAllowance() {
        return transportAllowance;
    }

    public BigDecimal getBonus() {
        return bonus;
    }
}
