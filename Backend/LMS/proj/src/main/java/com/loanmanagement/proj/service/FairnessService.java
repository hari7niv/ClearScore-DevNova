package com.loanmanagement.proj.service;

import com.loanmanagement.proj.entity.CreditDecision;
import com.loanmanagement.proj.entity.FairnessMetric;
import com.loanmanagement.proj.entity.LoanApplication;
import com.loanmanagement.proj.entity.LoanApplicant;
import com.loanmanagement.proj.repository.CreditDecisionRepository;
import com.loanmanagement.proj.repository.FairnessMetricRepository;
import com.loanmanagement.proj.repository.LoanApplicationRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FairnessService {

    private final LoanApplicationRepository applicationRepository;
    private final CreditDecisionRepository decisionRepository;
    private final FairnessMetricRepository fairnessMetricRepository;

    public FairnessService(LoanApplicationRepository applicationRepository,
                           CreditDecisionRepository decisionRepository,
                           FairnessMetricRepository fairnessMetricRepository) {
        this.applicationRepository = applicationRepository;
        this.decisionRepository = decisionRepository;
        this.fairnessMetricRepository = fairnessMetricRepository;
    }

    public Map<String, Object> calculateFairnessMetrics(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> results = new HashMap<>();
        List<LoanApplication> applications = loadApplications(startDate, endDate);

        Map<String, GroupStats> genderStats = buildGroupStats(applications, this::genderKey);
        Map<String, GroupStats> incomeStats = buildGroupStats(applications, this::incomeKey);
        Map<String, GroupStats> regionStats = buildGroupStats(applications, this::regionKey);

        results.put("gender", buildGroupResponse("GENDER", genderStats));
        results.put("income", buildGroupResponse("INCOME", incomeStats));
        results.put("region", buildGroupResponse("REGION", regionStats));
        results.put("summary", buildSummary(genderStats, incomeStats, regionStats));
        results.put("calculationDate", LocalDate.now());

        return results;
    }

    public boolean checkAdherenceTo4FifthsRule(String attribute, String value) {
        Map<String, Object> report = getLatestFairnessReport();
        Object ratio = report.get("adverseImpactRatio");
        if (ratio instanceof BigDecimal decimal) {
            return decimal.compareTo(new BigDecimal("0.8")) >= 0;
        }
        return true;
    }

    public Map<String, Object> getLatestFairnessReport() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(90);
        List<LoanApplication> applications = loadApplications(startDate, endDate);

        Map<String, GroupStats> genderStats = buildGroupStats(applications, this::genderKey);
        Map<String, GroupStats> incomeStats = buildGroupStats(applications, this::incomeKey);

        BigDecimal adverseImpactRatio = calculateAdverseImpactRatio(genderStats);
        BigDecimal disparateImpactIndex = calculateAdverseImpactRatio(incomeStats);

        Map<String, Object> report = new HashMap<>();
        report.put("adverseImpactRatio", adverseImpactRatio);
        report.put("disparateImpactIndex", disparateImpactIndex);
        report.put("modelDriftPsi", calculateOverallPsi(applications));
        report.put("auditConfidence", averageConfidence(applications));
        report.put("approvalByGender", buildApprovalSeries(genderStats));
        report.put("approvalByIncome", buildApprovalSeries(incomeStats));
        report.put("psiSeries", buildPsiSeries(applications));
        report.put("crossSegment", buildCrossSegment(incomeStats));
        report.put("generatedAt", LocalDateTime.now());
        return report;
    }

    public List<FairnessMetric> getHistoricFairness(LocalDate startDate, LocalDate endDate, String attribute) {
        if (attribute != null && !attribute.isBlank()) {
            return fairnessMetricRepository.findByProtectedAttribute(attribute);
        }
        return fairnessMetricRepository.findByCalculationDateBetween(startDate, endDate);
    }

    private List<LoanApplication> loadApplications(LocalDate startDate, LocalDate endDate) {
        return applicationRepository.findAll().stream()
                .filter(app -> app.getAppliedAt() != null)
                .filter(app -> !app.getAppliedAt().toLocalDate().isBefore(startDate))
                .filter(app -> !app.getAppliedAt().toLocalDate().isAfter(endDate))
                .toList();
    }

    private Map<String, GroupStats> buildGroupStats(List<LoanApplication> applications,
                                                    java.util.function.Function<LoanApplication, String> keyFn) {
        Map<String, GroupStats> stats = new LinkedHashMap<>();
        for (LoanApplication app : applications) {
            String key = keyFn.apply(app);
            stats.putIfAbsent(key, new GroupStats(key));
            GroupStats group = stats.get(key);
            group.total++;
            CreditDecision decision = decisionRepository.findByApplication_ApplicationId(app.getApplicationId()).orElse(null);
            if (decision != null && decision.getDecision() == CreditDecision.Decision.APPROVED) {
                group.approved++;
            }
            group.creditScores.add(app.getCreditScore() != null ? app.getCreditScore() : 0);
        }
        return stats;
    }

    private Map<String, Object> buildGroupResponse(String attribute, Map<String, GroupStats> stats) {
        Map<String, Object> response = new HashMap<>();
        response.put("attribute", attribute);
        Map<String, Object> groups = new LinkedHashMap<>();

        BigDecimal maxRate = maxApprovalRate(stats);
        for (GroupStats group : stats.values()) {
            BigDecimal approvalRate = group.approvalRate();
            BigDecimal disparity = maxRate.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : approvalRate.divide(maxRate, 4, RoundingMode.HALF_UP);
            boolean isFair = disparity.compareTo(new BigDecimal("0.8")) >= 0;

            Map<String, Object> details = new HashMap<>();
            details.put("approvalRate", approvalRate);
            details.put("disparityRatio", disparity);
            details.put("isFair", isFair);
            details.put("totalApplications", group.total);
            groups.put(group.key, details);

            saveFairnessMetric(LocalDate.now(), attribute, group.key, group.total, group.approved, approvalRate, disparity, isFair);
        }

        response.put("groups", groups);
        response.put("overallFair", maxRate.compareTo(BigDecimal.ZERO) == 0 ||
                groups.values().stream().allMatch(item -> Boolean.TRUE.equals(((Map<?, ?>) item).get("isFair"))));
        return response;
    }

    private Map<String, Object> buildSummary(Map<String, GroupStats> genderStats,
                                             Map<String, GroupStats> incomeStats,
                                             Map<String, GroupStats> regionStats) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("genderFair", calculateAdverseImpactRatio(genderStats).compareTo(new BigDecimal("0.8")) >= 0);
        summary.put("incomeFair", calculateAdverseImpactRatio(incomeStats).compareTo(new BigDecimal("0.8")) >= 0);
        summary.put("regionFair", calculateAdverseImpactRatio(regionStats).compareTo(new BigDecimal("0.8")) >= 0);
        summary.put("allFair", Boolean.TRUE.equals(summary.get("genderFair"))
                && Boolean.TRUE.equals(summary.get("incomeFair"))
                && Boolean.TRUE.equals(summary.get("regionFair")));
        return summary;
    }

    private String genderKey(LoanApplication app) {
        LoanApplicant applicant = app.getApplicant();
        if (applicant == null || applicant.getGender() == null) {
            return "UNKNOWN";
        }
        return applicant.getGender().name();
    }

    private String regionKey(LoanApplication app) {
        LoanApplicant applicant = app.getApplicant();
        if (applicant == null || applicant.getRegion() == null || applicant.getRegion().isBlank()) {
            return "UNSPECIFIED";
        }
        return applicant.getRegion().toUpperCase();
    }

    private String incomeKey(LoanApplication app) {
        if (app.getIncome() == null) {
            return "UNKNOWN";
        }
        BigDecimal income = app.getIncome();
        if (income.compareTo(new BigDecimal("35000")) <= 0) {
            return "$0-$35k";
        }
        if (income.compareTo(new BigDecimal("75000")) <= 0) {
            return "$35k-$75k";
        }
        if (income.compareTo(new BigDecimal("150000")) <= 0) {
            return "$75k-$150k";
        }
        return "$150k+";
    }

    private BigDecimal maxApprovalRate(Map<String, GroupStats> stats) {
        return stats.values().stream()
                .map(GroupStats::approvalRate)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal calculateAdverseImpactRatio(Map<String, GroupStats> stats) {
        if (stats.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal max = maxApprovalRate(stats);
        BigDecimal min = stats.values().stream()
                .map(GroupStats::approvalRate)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        if (max.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return min.divide(max, 3, RoundingMode.HALF_UP);
    }

    private List<Map<String, Object>> buildApprovalSeries(Map<String, GroupStats> stats) {
        List<Map<String, Object>> series = new ArrayList<>();
        for (GroupStats group : stats.values()) {
            Map<String, Object> item = new HashMap<>();
            item.put("segment", group.key);
            item.put("approvalRate", group.approvalRate().multiply(new BigDecimal("100")));
            series.add(item);
        }
        return series;
    }

    private List<Map<String, Object>> buildCrossSegment(Map<String, GroupStats> stats) {
        List<Map<String, Object>> table = new ArrayList<>();
        for (GroupStats group : stats.values()) {
            Map<String, Object> row = new HashMap<>();
            row.put("segment", group.key);
            row.put("approvalRate", group.approvalRate().multiply(new BigDecimal("100")));
            row.put("avgCreditScore", average(group.creditScores));
            row.put("delinquencyForecast", group.approvalRate().multiply(new BigDecimal("0.04")));
            row.put("regulatoryStatus", group.approvalRate().compareTo(new BigDecimal("0.7")) >= 0 ? "APPROVED" : "MANUAL_REVIEW");
            table.add(row);
        }
        return table;
    }

    private BigDecimal average(List<Integer> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Integer value : values) {
            sum = sum.add(new BigDecimal(value));
        }
        return sum.divide(new BigDecimal(values.size()), 0, RoundingMode.HALF_UP);
    }

    private BigDecimal averageConfidence(List<LoanApplication> applications) {
        if (applications.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (LoanApplication app : applications) {
            Optional<CreditDecision> decision = decisionRepository.findByApplication_ApplicationId(app.getApplicationId());
            if (decision.isPresent() && decision.get().getProbability() != null) {
                total = total.add(decision.get().getProbability());
                count++;
            }
        }
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(new BigDecimal(count), 3, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    private BigDecimal calculateOverallPsi(List<LoanApplication> applications) {
        if (applications.size() < 5) {
            return BigDecimal.ZERO;
        }
        List<Integer> scores = applications.stream()
                .map(app -> app.getCreditScore() == null ? 0 : app.getCreditScore())
                .toList();
        Map<String, Integer> baseline = scoreBuckets(scores.subList(0, scores.size() / 2));
        Map<String, Integer> actual = scoreBuckets(scores.subList(scores.size() / 2, scores.size()));
        return calculatePsi(baseline, actual);
    }

    private List<Map<String, Object>> buildPsiSeries(List<LoanApplication> applications) {
        List<Map<String, Object>> series = new ArrayList<>();
        if (applications.size() < 10) {
            return series;
        }
        int chunk = Math.max(2, applications.size() / 6);
        Map<String, Integer> baseline = scoreBuckets(applications.subList(0, chunk).stream()
                .map(app -> app.getCreditScore() == null ? 0 : app.getCreditScore())
                .toList());
        for (int index = 0; index < applications.size(); index += chunk) {
            int end = Math.min(applications.size(), index + chunk);
            List<Integer> windowScores = applications.subList(index, end).stream()
                    .map(app -> app.getCreditScore() == null ? 0 : app.getCreditScore())
                    .toList();
            BigDecimal psi = calculatePsi(baseline, scoreBuckets(windowScores));
            Map<String, Object> point = new HashMap<>();
            point.put("label", "W" + (index / chunk + 1));
            point.put("psi", psi);
            series.add(point);
        }
        return series;
    }

    private Map<String, Integer> scoreBuckets(List<Integer> scores) {
        Map<String, Integer> buckets = new LinkedHashMap<>();
        buckets.put("0-600", 0);
        buckets.put("600-700", 0);
        buckets.put("700-800", 0);
        buckets.put("800+", 0);
        for (Integer score : scores) {
            if (score < 600) {
                buckets.put("0-600", buckets.get("0-600") + 1);
            } else if (score < 700) {
                buckets.put("600-700", buckets.get("600-700") + 1);
            } else if (score < 800) {
                buckets.put("700-800", buckets.get("700-800") + 1);
            } else {
                buckets.put("800+", buckets.get("800+") + 1);
            }
        }
        return buckets;
    }

    private BigDecimal calculatePsi(Map<String, Integer> baseline, Map<String, Integer> actual) {
        double psi = 0.0;
        int baselineTotal = baseline.values().stream().mapToInt(Integer::intValue).sum();
        int actualTotal = actual.values().stream().mapToInt(Integer::intValue).sum();
        if (baselineTotal == 0 || actualTotal == 0) {
            return BigDecimal.ZERO;
        }
        for (String bucket : baseline.keySet()) {
            double expected = Math.max(0.0001, baseline.get(bucket) / (double) baselineTotal);
            double actualRate = Math.max(0.0001, actual.getOrDefault(bucket, 0) / (double) actualTotal);
            psi += (actualRate - expected) * Math.log(actualRate / expected);
        }
        return new BigDecimal(psi).setScale(3, RoundingMode.HALF_UP);
    }

    private void saveFairnessMetric(LocalDate date, String attribute, String value,
                                    int total, int approved, BigDecimal rate,
                                    BigDecimal disparity, boolean isFair) {
        Optional<FairnessMetric> existing = fairnessMetricRepository
                .findByCalculationDateAndProtectedAttributeAndAttributeValue(date, attribute, value);

        FairnessMetric metric = existing.orElse(new FairnessMetric());
        metric.setCalculationDate(date);
        metric.setProtectedAttribute(attribute);
        metric.setAttributeValue(value);
        metric.setTotalApplications(total);
        metric.setApprovedCount(approved);
        metric.setRejectedCount(Math.max(0, total - approved));
        metric.setApprovalRate(rate);
        metric.setDisparityRatio(disparity);
        metric.setIsFair(isFair);
        metric.setSampleSize(total);
        fairnessMetricRepository.save(metric);
    }

    private static class GroupStats {
        private final String key;
        private int total;
        private int approved;
        private final List<Integer> creditScores = new ArrayList<>();

        private GroupStats(String key) {
            this.key = key;
        }

        private BigDecimal approvalRate() {
            if (total == 0) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(approved)
                    .divide(new BigDecimal(total), 3, RoundingMode.HALF_UP);
        }
    }
}
