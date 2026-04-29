package com.loanmanagement.proj.repository;

import com.loanmanagement.proj.entity.DecisionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DecisionAuditRepository extends JpaRepository<DecisionAudit, Long> {
    List<DecisionAudit> findAllByOrderByTimestampDesc();
    List<DecisionAudit> findByUserIdOrderByTimestampDesc(String userId);
}
