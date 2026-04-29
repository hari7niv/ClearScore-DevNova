package com.loanmanagement.proj.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loanmanagement.proj.entity.LoanApplicant;

public interface LoanApplicantRepository extends JpaRepository<LoanApplicant, Long> {
    Optional<LoanApplicant> findByEmail(String email);
    Optional<LoanApplicant> findByUserId(String userId);
}