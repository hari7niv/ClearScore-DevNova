package com.loanmanagement.proj.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PredictionHistoryDTO {
    private String applicationId;
    private LocalDateTime appliedAt;
    private String decision;
    private BigDecimal probability;
    private BigDecimal loanAmount;
    private BigDecimal income;
    private String recommendation;
}