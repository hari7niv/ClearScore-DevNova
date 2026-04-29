package com.loanmanagement.proj.entity;

/**
 * @author admin
 **/
import jakarta.persistence.*;
        import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_trail")
@Data
public class AuditTrail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;

    @ManyToOne
    @JoinColumn(name = "application_id")
    private LoanApplication application;

    @ManyToOne
    @JoinColumn(name = "decision_id")
    private CreditDecision decision;

    private String actionType;
    private LocalDateTime timestamp;
    private String previousHash;
    private String currentHash;

    @Column(columnDefinition = "TEXT")
    private String decisionSnapshot;

    private String ipAddress;
    private String userId;
}