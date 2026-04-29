package com.loanmanagement.proj.repository;

import com.loanmanagement.proj.entity.ShapExplanation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * @author admin
 **/
public interface ShapExplanationRepository extends JpaRepository<ShapExplanation,Long> {
    List<ShapExplanation>findByDecision_DecisionIdOrderByShapValueDesc(Long desiconId);
}
