package com.loanmanagement.proj.entity;

/**
 * @author admin
 **/
import jakarta.persistence.*;
        import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "shap_explanation")
@Data
public class ShapExplanation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long shapId;

    @ManyToOne
    @JoinColumn(name = "decision_id")
    private CreditDecision decision;

    private String featureName;
    private BigDecimal shapValue;

    @Enumerated(EnumType.STRING)
    private ImpactLevel impactLevel;

    private Integer rankOrder;

    public enum ImpactLevel {
        HIGH, MEDIUM, LOW
    }
}