package com.loanmanagement.proj.repository;

/**
 * @author admin
 **/
import com.loanmanagement.proj.entity.AuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface AuditTrailRepository extends JpaRepository<AuditTrail, Long> {

    List<AuditTrail> findByApplication_ApplicationIdOrderByTimestampDesc(Long applicationId);

    List<AuditTrail> findAllByOrderByTimestampDesc();

    @Query("SELECT a FROM AuditTrail a ORDER BY a.auditId ASC")
    List<AuditTrail> findAllOrderByAuditIdAsc();

    Optional<AuditTrail> findTopByOrderByAuditIdDesc();
}