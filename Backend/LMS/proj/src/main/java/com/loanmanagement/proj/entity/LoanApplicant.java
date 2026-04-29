package com.loanmanagement.proj.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "loan_applicant")
@Data
public class LoanApplicant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long applicantId;

    @Column(unique = true)
    private String userId;

    private String name;
    private String email;
    private String phone;
    private LocalDateTime dob;
    private LocalDateTime createdAt;

    private String region;        // ADD THIS - for geographic fairness

    private String incomeBand;    // ADD THIS - for income fairness

    @Enumerated(EnumType.STRING)
    private Gender gender;

    public enum Gender {
        MALE, FEMALE, OTHER
    }
}