package com.loanmanagement.proj.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fairness_metric")
@Data
public class FairnessMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long metricId;

    private LocalDate calculationDate;

    private String protectedAttribute;  // e.g., "GENDER", "AGE_GROUP", "REGION"

    private String attributeValue;      // e.g., "FEMALE", "25-35", "NORTH"

    private Integer totalApplications;

    private Integer approvedCount;

    private Integer rejectedCount;

    private BigDecimal approvalRate;     // approvedCount / totalApplications

    private BigDecimal disparityRatio;   // approvalRate / mostFavoredGroupRate

    private Boolean isFair;              // true if disparityRatio >= 0.8 (4/5ths rule)

    private Integer sampleSize;
}