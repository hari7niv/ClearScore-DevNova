package com.loanmanagement.proj.repository;

import com.loanmanagement.proj.entity.FairnessMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FairnessMetricRepository extends JpaRepository<FairnessMetric, Long> {

    List<FairnessMetric> findByCalculationDateBetween(LocalDate startDate, LocalDate endDate);

    List<FairnessMetric> findByProtectedAttribute(String protectedAttribute);

    Optional<FairnessMetric> findByCalculationDateAndProtectedAttributeAndAttributeValue(
            LocalDate calculationDate, String protectedAttribute, String attributeValue);

    @Query("SELECT f FROM FairnessMetric f WHERE f.calculationDate = :date ORDER BY f.disparityRatio ASC")
    List<FairnessMetric> findByCalculationDateOrderByDisparity(@Param("date") LocalDate date);

    @Query("SELECT DISTINCT f.calculationDate FROM FairnessMetric f ORDER BY f.calculationDate DESC")
    List<LocalDate> findAllCalculationDates();
}