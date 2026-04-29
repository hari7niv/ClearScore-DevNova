package com.loanmanagement.proj.dto;

import lombok.Data;
import java.util.List;

@Data
public class ApplicantExplanationDTO {
    private String decision;
    private String plainLanguageReason;
    private List<String> topFactors;
    private List<String> actionableAdvice;
    private String appealProcess;
}