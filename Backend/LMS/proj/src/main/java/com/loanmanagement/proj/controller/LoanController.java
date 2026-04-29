package com.loanmanagement.proj.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loanmanagement.proj.dto.ApplicantExplanationDTO;
import com.loanmanagement.proj.dto.DecisionResponseDTO;
import com.loanmanagement.proj.dto.LoanRequestDTO;
import com.loanmanagement.proj.dto.PredictionHistoryDTO;
import com.loanmanagement.proj.entity.AuditTrail;
import com.loanmanagement.proj.entity.CreditDecision;
import com.loanmanagement.proj.entity.LoanApplicant;
import com.loanmanagement.proj.entity.LoanApplication;
import com.loanmanagement.proj.entity.ShapExplanation;
import com.loanmanagement.proj.repository.CreditDecisionRepository;
import com.loanmanagement.proj.repository.LoanApplicantRepository;
import com.loanmanagement.proj.repository.LoanApplicationRepository;
import com.loanmanagement.proj.repository.ShapExplanationRepository;
import com.loanmanagement.proj.service.AuditService;
import com.loanmanagement.proj.service.DecisionAuditService;
import com.loanmanagement.proj.service.PredictionService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/loan")

public class LoanController {

    private final LoanApplicantRepository applicantRepository;
    private final LoanApplicationRepository applicationRepository;
    private final CreditDecisionRepository decisionRepository;
    private final ShapExplanationRepository shapRepository;
    private final AuditService auditService;
    private final DecisionAuditService decisionAuditService;
    private final PredictionService predictionService;

    public LoanController(LoanApplicantRepository applicantRepository,
                          LoanApplicationRepository applicationRepository,
                          CreditDecisionRepository decisionRepository,
                          ShapExplanationRepository shapRepository,
                          AuditService auditService,
                          DecisionAuditService decisionAuditService,
                          PredictionService predictionService) {
        this.applicantRepository = applicantRepository;
        this.applicationRepository = applicationRepository;
        this.decisionRepository = decisionRepository;
        this.shapRepository = shapRepository;
        this.auditService = auditService;
        this.decisionAuditService = decisionAuditService;
        this.predictionService = predictionService;
    }

    // Fetch user profile data from CSV (auto from JWT userId + 1000)
    @GetMapping("/csv-profile")
    public Map<String, Object> getCsvProfile(HttpServletRequest httpRequest) {
        String authUserId = (String) httpRequest.getAttribute("authUserId");
        if (authUserId == null || authUserId.isBlank()) {
            throw new RuntimeException("Unauthorized — no user ID in token");
        }

        int csvId;
        try {
            csvId = predictionService.resolveCsvUserId(authUserId);
        } catch (NumberFormatException e) {
            csvId = predictionService.resolveCsvUserId(authUserId);
        }

        // Call ML service to get user info from CSV
        return predictionService.getUserProfile(csvId);
    }

    // CSV-BASED PREDICTION: JWT userId + 1000 → CSV lookup → ML prediction
    @PostMapping("/predict")
    public Map<String, Object> predictFromCsv(@RequestBody Map<String, Object> request,
                                               HttpServletRequest httpRequest) {
        // Get userId from JWT token
        String authUserId = (String) httpRequest.getAttribute("authUserId");
        if (authUserId == null || authUserId.isBlank()) {
            throw new RuntimeException("Unauthorized — no user ID in token");
        }

        // Map to CSV row: JWT userId + 1000
        int csvId;
        try {
            csvId = predictionService.resolveCsvUserId(authUserId);
        } catch (NumberFormatException e) {
            csvId = predictionService.resolveCsvUserId(authUserId);
        }

        double loanAmount;
        try {
            loanAmount = Double.parseDouble(String.valueOf(request.get("loanAmount")));
        } catch (Exception e) {
            throw new IllegalArgumentException("loanAmount must be a valid number");
        }

        if (loanAmount <= 0) {
            throw new IllegalArgumentException("loanAmount must be greater than 0");
        }

        // Call the ML service which reads from CSV
        Map<String, Object> mlResult = predictionService.predictFromCsv(csvId, loanAmount);
        var auditEntry = decisionAuditService.recordCsvDecision(authUserId, csvId, loanAmount, mlResult);
        mlResult.put("authUserId", authUserId);
        mlResult.put("csvMappedId", csvId);
        mlResult.put("auditId", auditEntry.getAuditId());
        return mlResult;
    }

    // MAIN ENDPOINT - Apply for loan
    @PostMapping("/apply")
    public DecisionResponseDTO applyForLoan(@RequestBody LoanRequestDTO request,
                                            HttpServletRequest httpRequest) {

        DecisionResponseDTO response = new DecisionResponseDTO();

        // 1. Create or find applicant
        String authenticatedUserId = (String) httpRequest.getAttribute("authUserId");
        String userId = request.getUserId() != null && !request.getUserId().isBlank()
            ? request.getUserId()
            : authenticatedUserId;

        LoanApplicant applicant = applicantRepository.findByUserId(userId)
            .orElseGet(() -> applicantRepository.findByEmail(request.getEmail())
                .orElse(new LoanApplicant()));

        applicant.setUserId(userId);
        applicant.setName(request.getName());
        applicant.setEmail(request.getEmail());
        applicant.setPhone(request.getPhone());
        applicant.setGender(LoanApplicant.Gender.valueOf(request.getGender()));
        applicant.setRegion(request.getRegion());
        applicant.setCreatedAt(LocalDateTime.now());
        LoanApplicant savedApplicant = applicantRepository.save(applicant);

        // 2. Create application
        LoanApplication application = new LoanApplication();
        application.setApplicant(savedApplicant);
        application.setIncome(request.getIncome());
        application.setCreditAmount(request.getCreditAmount());
        application.setLoanTerm(request.getLoanTerm());
        application.setExistingDebt(request.getExistingDebt());
        application.setCreditScore(request.getCreditScore());
        application.setEmploymentYears(request.getEmploymentYears());
        application.setPurpose(request.getPurpose());
        application.setAppliedAt(LocalDateTime.now());
        LoanApplication savedApplication = applicationRepository.save(application);

        // 3. Get prediction from model
        Map<String, Object> prediction = predictionService.predict(request);
        String decision = (String) prediction.get("decision");
        BigDecimal probability = new BigDecimal(String.valueOf(prediction.get("probability")));
        BigDecimal riskScore = prediction.get("risk_score") != null ? new BigDecimal(String.valueOf(prediction.get("risk_score"))) : BigDecimal.ZERO;
        String recommendation = (String) prediction.getOrDefault("recommendation", "Review the result and follow next steps.");
        List<String> nextSteps = (List<String>) prediction.getOrDefault("next_steps", List.of());
        Map<String, Object> chartData = (Map<String, Object>) prediction.getOrDefault("chart_data", Map.of());
        List<Map<String, Object>> topFeaturesList = (List<Map<String, Object>>) prediction.getOrDefault("top_features", List.of());
        List<Map<String, Object>> reasoningList = (List<Map<String, Object>>) prediction.getOrDefault("approval_reasoning", List.of());
        String modelVersion = "pickle-xgboost-v1";

        Map<String, BigDecimal> shapValues = new LinkedHashMap<>();
        for (Map<String, Object> item : topFeaturesList) {
            shapValues.put(String.valueOf(item.get("feature")), new BigDecimal(String.valueOf(item.get("value"))));
        }

        // 4. Save decision
        CreditDecision creditDecision = new CreditDecision();
        creditDecision.setApplication(savedApplication);
        creditDecision.setDecision(CreditDecision.Decision.valueOf(decision));
        creditDecision.setProbability(probability);
        creditDecision.setDecisionThreshold(new BigDecimal("0.5"));
        creditDecision.setModelVersion(modelVersion);
        creditDecision.setDecisionAt(LocalDateTime.now());
        creditDecision.setResponseTimeMs(100);
        CreditDecision savedDecision = decisionRepository.save(creditDecision);

        // 5. Save SHAP explanations
        int rank = 1;
        for (Map.Entry<String, BigDecimal> entry : shapValues.entrySet()) {
            ShapExplanation shap = new ShapExplanation();
            shap.setDecision(savedDecision);
            shap.setFeatureName(entry.getKey());
            shap.setShapValue(entry.getValue());

            double absValue = Math.abs(entry.getValue().doubleValue());
            if (absValue > 0.2) {
                shap.setImpactLevel(ShapExplanation.ImpactLevel.HIGH);
            } else if (absValue > 0.1) {
                shap.setImpactLevel(ShapExplanation.ImpactLevel.MEDIUM);
            } else {
                shap.setImpactLevel(ShapExplanation.ImpactLevel.LOW);
            }
            shap.setRankOrder(rank++);
            shapRepository.save(shap);
        }

        // 6. Create audit trail with hash chain
        String snapshot = String.format(
                "{\"applicationId\":%d,\"applicant\":\"%s\",\"income\":%f,\"creditAmount\":%f,\"decision\":\"%s\",\"probability\":%f,\"shapValues\":%s}",
                savedApplication.getApplicationId(),
                savedApplicant.getName(),
                savedApplication.getIncome(),
                savedApplication.getCreditAmount(),
                decision,
                probability,
                shapValues.toString()
        );

        AuditTrail audit = auditService.logAction(
                savedApplication,
                savedDecision,
                "LOAN_APPLICATION",
                snapshot,
                httpRequest.getRemoteAddr(),
                userId
        );

        // 7. Build response
        response.setApplicationId(String.valueOf(savedApplication.getApplicationId()));
        response.setUserId(userId);
        response.setDecision(decision);
        response.setProbability(probability);
        response.setRiskScore(riskScore);
        response.setShapValues(shapValues);
        response.setAuditId(String.valueOf(audit.getAuditId()));
        response.setModelVersion(modelVersion);
        response.setDecisionTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        response.setRecommendation(recommendation);
        response.setNextSteps(nextSteps);
        response.setChartData(chartData);
        response.setRiskLabel(String.valueOf(prediction.getOrDefault("risk_label", "Moderate")));

        // Add plain language explanations for applicant
        response.setReasonsForDecision(predictionService.getPlainLanguageReasons(shapValues, decision));
        response.setActionableSteps(predictionService.getActionableSteps(shapValues, decision));

        if (decision.equals("APPROVED")) {
            response.setMessage("Congratulations! Your loan has been pre-approved.");
        } else {
            response.setMessage("We regret to inform you that your application could not be approved at this time.");
        }

        return response;
    }



    @GetMapping("/history/me")
    public List<Map<String, Object>> getMyHistory(HttpServletRequest request) {
        String userId = (String) request.getAttribute("authUserId");
        if (userId == null || userId.isBlank()) {
            throw new RuntimeException("Unauthorized");
        }

        List<Map<String, Object>> history = new ArrayList<>(decisionAuditService.getUserDecisionAudits(userId));
        history.addAll(applicationRepository.findByApplicant_UserIdOrderByAppliedAtDesc(userId)
                .stream()
                .map(app -> {
                    CreditDecision decision = decisionRepository.findByApplication_ApplicationId(app.getApplicationId()).orElse(null);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("applicationId", String.valueOf(app.getApplicationId()));
                    item.put("appliedAt", app.getAppliedAt());
                    item.put("decision", decision != null ? decision.getDecision().name() : "PENDING");
                    item.put("probability", decision != null ? decision.getProbability() : BigDecimal.ZERO);
                    item.put("loanAmount", app.getCreditAmount());
                    item.put("income", app.getIncome());
                    item.put("recommendation", decision != null ? "Use the recommendation from your latest prediction" : "No decision yet");
                    item.put("source", "loan_application_table");
                    return item;
                })
                .toList());
        return history;
    }

    // Get decision by application ID (for compliance officer)
    @GetMapping("/decision/{applicationId}")
    public Map<String, Object> getDecisionByApplicationId(@PathVariable Long applicationId) {
        Map<String, Object> response = new HashMap<>();

        LoanApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        CreditDecision decision = decisionRepository.findByApplication_ApplicationId(applicationId)
                .orElseThrow(() -> new RuntimeException("Decision not found"));

        List<ShapExplanation> shapExplanations = shapRepository.findByDecision_DecisionIdOrderByShapValueDesc(decision.getDecisionId());

        response.put("application", application);
        response.put("decision", decision);
        response.put("shapExplanations", shapExplanations);
        response.put("auditLogs", auditService.getAuditLogsForApplication(applicationId));

        return response;
    }

    // Get applicant view (plain language)
    @GetMapping("/applicant-view/{applicationId}")
    public ApplicantExplanationDTO getApplicantView(@PathVariable Long applicationId) {
        ApplicantExplanationDTO response = new ApplicantExplanationDTO();

        CreditDecision decision = decisionRepository.findByApplication_ApplicationId(applicationId)
                .orElseThrow(() -> new RuntimeException("Decision not found"));

        List<ShapExplanation> shapExplanations = shapRepository.findByDecision_DecisionIdOrderByShapValueDesc(decision.getDecisionId());

        response.setDecision(decision.getDecision().toString());

        // Build plain language explanation
        StringBuilder reason = new StringBuilder();
        List<String> topFactors = new ArrayList<>();

        for (ShapExplanation shap : shapExplanations.stream().limit(3).toList()) {
            String factor = shap.getFeatureName().replace("_", " ");
            double impact = shap.getShapValue().doubleValue();

            if (decision.getDecision() == CreditDecision.Decision.REJECTED && impact > 0) {
                topFactors.add(factor + " (negative impact)");
            } else if (decision.getDecision() == CreditDecision.Decision.APPROVED && impact < 0) {
                topFactors.add(factor + " (positive impact)");
            }
        }

        if (decision.getDecision() == CreditDecision.Decision.REJECTED) {
            reason.append("Your application was not approved because ");
            reason.append(String.join(", ", topFactors));
            reason.append(" did not meet our criteria.");
            response.setAppealProcess("You can appeal this decision within 30 days by contacting our support team.");
        } else {
            reason.append("Your application was approved based on your strong ");
            reason.append(String.join(", ", topFactors));
            response.setAppealProcess("No appeal needed. Please proceed with loan agreement.");
        }

        response.setPlainLanguageReason(reason.toString());
        response.setTopFactors(topFactors);
        response.setActionableAdvice(predictionService.getActionableSteps(
                convertShapToMap(shapExplanations),
                decision.getDecision().toString()
        ));

        return response;
    }

    // Get compliance officer view (with SHAP and audit)
    @GetMapping("/compliance-view/{applicationId}")
    public Map<String, Object> getComplianceView(@PathVariable Long applicationId) {
        Map<String, Object> response = new HashMap<>();

        LoanApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        CreditDecision decision = decisionRepository.findByApplication_ApplicationId(applicationId)
                .orElseThrow(() -> new RuntimeException("Decision not found"));

        List<ShapExplanation> shapExplanations = shapRepository.findByDecision_DecisionIdOrderByShapValueDesc(decision.getDecisionId());
        List<AuditTrail> auditLogs = auditService.getAuditLogsForApplication(applicationId);

        response.put("applicationId", applicationId);
        response.put("applicantName", application.getApplicant().getName());
        response.put("applicantEmail", application.getApplicant().getEmail());
        response.put("decision", decision.getDecision());
        response.put("probability", decision.getProbability());
        response.put("modelVersion", decision.getModelVersion());
        response.put("decisionTime", decision.getDecisionAt());

        // SHAP values for compliance
        Map<String, Object> shapMap = new HashMap<>();
        for (ShapExplanation shap : shapExplanations) {
            shapMap.put(shap.getFeatureName(), shap.getShapValue());
        }
        response.put("shapValues", shapMap);

        // Audit trail info
        response.put("auditLogs", auditLogs);

        // Check if any anomaly detected
        boolean hasAnomaly = auditLogs.stream().anyMatch(a ->
                a.getActionType().contains("ANOMALY") ||
                        (a.getDecisionSnapshot() != null && a.getDecisionSnapshot().contains("flag"))
        );
        response.put("anomalyDetected", hasAnomaly);

        return response;
    }

    // Get regulator view (aggregate fairness)
    @GetMapping("/regulator-view")
    public Map<String, Object> getRegulatorView() {
        Map<String, Object> response = new HashMap<>();

        // Aggregate statistics
        long totalApplications = applicationRepository.count();
        long approvedCount = decisionRepository.findAll().stream()
                .filter(d -> d.getDecision() == CreditDecision.Decision.APPROVED)
                .count();
        long rejectedCount = totalApplications - approvedCount;

        double overallApprovalRate = totalApplications > 0 ?
                (double) approvedCount / totalApplications : 0;

        // Fairness metrics by gender (simplified - in real app, query from FairnessMetric table)
        Map<String, Object> genderFairness = new HashMap<>();
        genderFairness.put("male_approval_rate", 0.72);
        genderFairness.put("female_approval_rate", 0.68);
        genderFairness.put("disparity_ratio", 0.94);
        genderFairness.put("passes_4_5ths_rule", true);

        Map<String, Object> ageFairness = new HashMap<>();
        ageFairness.put("under_30_approval_rate", 0.62);
        ageFairness.put("age_30_50_approval_rate", 0.78);
        ageFairness.put("over_50_approval_rate", 0.71);
        ageFairness.put("disparity_ratio", 0.79);
        ageFairness.put("passes_4_5ths_rule", false);

        response.put("reportDate", LocalDateTime.now());
        response.put("totalApplications", totalApplications);
        response.put("approvedCount", approvedCount);
        response.put("rejectedCount", rejectedCount);
        response.put("overallApprovalRate", overallApprovalRate);
        response.put("fairnessByGender", genderFairness);
        response.put("fairnessByAge", ageFairness);
        response.put("complianceStatus", "Under Review");
        response.put("regulationStandard", "4/5ths Rule (Disparate Impact Ratio >= 0.8)");

        return response;
    }

    // Verify audit chain integrity
    @GetMapping("/verify-audit")
    public Map<String, Object> verifyAuditChain() {
        Map<String, Object> response = new HashMap<>();
        boolean isValid = auditService.verifyChainIntegrity();
        response.put("chainValid", isValid);
        response.put("message", isValid ? "Audit trail is intact and tamper-free" : "WARNING: Audit trail has been tampered with!");
        response.put("verificationTime", LocalDateTime.now());
        return response;
    }

    // Helper method
    private Map<String, BigDecimal> convertShapToMap(List<ShapExplanation> shapList) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (ShapExplanation shap : shapList) {
            map.put(shap.getFeatureName(), shap.getShapValue());
        }
        return map;
    }
}
