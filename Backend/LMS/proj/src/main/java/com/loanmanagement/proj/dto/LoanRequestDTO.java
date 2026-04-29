package com.loanmanagement.proj.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class LoanRequestDTO {
    private String userId;
    private String name;
    private String email;
    private String phone;
    private String gender;  // MALE, FEMALE, OTHER
    private BigDecimal income;
    private BigDecimal creditAmount;
    private Integer loanTerm;
    private BigDecimal existingDebt;
    private Integer creditScore;
    private BigDecimal employmentYears;
    private Integer age;
    private String purpose;
    private String region;
}