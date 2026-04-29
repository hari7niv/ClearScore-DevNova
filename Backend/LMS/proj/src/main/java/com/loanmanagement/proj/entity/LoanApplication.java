package com.loanmanagement.proj.entity;

import jakarta.persistence.*;
        import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author admin
 **/
@Entity
@Table(name = "loan_application")
@Data
public class LoanApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long applicationId;

    @ManyToOne
    @JoinColumn(name = "applicant_id")
    private LoanApplicant applicant;

    private BigDecimal income;
    private BigDecimal creditAmount;
    private Integer loanTerm;
    private BigDecimal existingDebt;
    private Integer creditScore;
    private BigDecimal employmentYears;
    private String purpose;
    private LocalDateTime appliedAt;
}