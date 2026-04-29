package com.loanmanagement.proj.repository;

/**
 * @author admin
 **/
import com.loanmanagement.proj.entity.CreditDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CreditDecisionRepository extends JpaRepository<CreditDecision, Long> {
    Optional<CreditDecision> findByApplication_ApplicationId(Long applicationId);
}
