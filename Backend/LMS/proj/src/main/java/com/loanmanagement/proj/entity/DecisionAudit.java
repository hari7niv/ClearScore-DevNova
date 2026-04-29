package com.loanmanagement.proj.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "decision_audit")
@Data
public class DecisionAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;

    private String userId;
    private Integer csvUserId;
    private BigDecimal loanAmount;
    private String decision;
    private BigDecimal probability;
    private BigDecimal riskScore;
    private String riskLabel;
    private LocalDateTime timestamp;

    @Column(columnDefinition = "TEXT")
    private String reasonsJson;

    @Column(columnDefinition = "TEXT")
    private String timelineSnapshotJson;
}
