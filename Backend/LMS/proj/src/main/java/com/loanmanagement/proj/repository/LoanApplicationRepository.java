package com.loanmanagement.proj.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loanmanagement.proj.entity.LoanApplication;

/**
 * @author admin
 **/
public interface LoanApplicationRepository extends JpaRepository<LoanApplication,Long> {

	java.util.List<LoanApplication> findByApplicant_UserIdOrderByAppliedAtDesc(String userId);

}
