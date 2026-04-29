package com.loanmanagement.proj.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class DecisionResponseDTO {
    private String userId;
    private String applicationId;
    private String decision;  // APPROVE or REJECT
    private BigDecimal probability;
    private BigDecimal riskScore;
    private String message;

    private String recommendation;
    private java.util.List<String> nextSteps;
    private java.util.Map<String, Object> chartData;
    private String riskLabel;

    // For Applicant (plain language)
    private List<String> reasonsForDecision;
    private List<String> actionableSteps;

    // For Compliance Officer
    private Map<String, BigDecimal> shapValues;
    private String auditId;
    private String modelVersion;

    // Timestamp
    private String decisionTime;
}