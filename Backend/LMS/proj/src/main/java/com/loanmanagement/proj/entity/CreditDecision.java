package com.loanmanagement.proj.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_decision")
@Data
public class CreditDecision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long decisionId;

    @OneToOne
    @JoinColumn(name = "application_id")
    private LoanApplication application;

    @Enumerated(EnumType.STRING)
    private Decision decision;

    private BigDecimal probability;
    private BigDecimal decisionThreshold;
    private String modelVersion;
    private LocalDateTime decisionAt;
    private Integer responseTimeMs;

    public enum Decision {
        APPROVED, REJECTED
    }
}