package com.loanmanagement.proj.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loanmanagement.proj.entity.AuditTrail;
import com.loanmanagement.proj.entity.CreditDecision;
import com.loanmanagement.proj.entity.FairnessMetric;
import com.loanmanagement.proj.entity.LoanApplicant;
import com.loanmanagement.proj.entity.LoanApplication;
import com.loanmanagement.proj.repository.AuditTrailRepository;
import com.loanmanagement.proj.repository.CreditDecisionRepository;
import com.loanmanagement.proj.repository.LoanApplicantRepository;
import com.loanmanagement.proj.repository.LoanApplicationRepository;
import com.loanmanagement.proj.service.AuditService;
import com.loanmanagement.proj.service.FairnessService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/test")
public class TestController {

    private final LoanApplicantRepository applicantRepository;
    private final LoanApplicationRepository applicationRepository;
    private final CreditDecisionRepository decisionRepository;
    private final AuditTrailRepository auditRepository;      // ADD THIS
    private final AuditService auditService;
    private final FairnessService fairnessService;

    // UPDATE CONSTRUCTOR with all repositories
    public TestController(LoanApplicantRepository applicantRepository,
                          LoanApplicationRepository applicationRepository,
                          CreditDecisionRepository decisionRepository,
                          AuditTrailRepository auditRepository,      // ADD THIS
                          AuditService auditService,
                          FairnessService fairnessService) {
        this.applicantRepository = applicantRepository;
        this.applicationRepository = applicationRepository;
        this.decisionRepository = decisionRepository;
        this.auditRepository = auditRepository;      // ADD THIS
        this.auditService = auditService;
        this.fairnessService = fairnessService;

    }

    @PostMapping("/applicant")
    public LoanApplicant createApplicant() {
        LoanApplicant applicant = new LoanApplicant();
        applicant.setName("Test User");
        applicant.setEmail("test@email.com");
        applicant.setGender(LoanApplicant.Gender.MALE);
        applicant.setCreatedAt(LocalDateTime.now());
        return applicantRepository.save(applicant);
    }

    @PostMapping("/application")
    public LoanApplication createApplication() {
        LoanApplicant applicant = applicantRepository.findAll().stream().findFirst().orElse(null);

        LoanApplication app = new LoanApplication();
        app.setApplicant(applicant);
        app.setIncome(new BigDecimal("50000"));
        app.setCreditAmount(new BigDecimal("200000"));
        app.setExistingDebt(new BigDecimal("30000"));
        app.setCreditScore(650);
        app.setEmploymentYears(new BigDecimal("5"));
        app.setAppliedAt(LocalDateTime.now());

        return applicationRepository.save(app);
    }

    @PostMapping("/decision")
    public CreditDecision createDecision() {
        LoanApplication application = applicationRepository.findAll().stream()
                .findFirst()
                .orElse(null);

        if (application == null) {
            throw new RuntimeException("No application found. Create an application first!");
        }

        CreditDecision decision = new CreditDecision();
        decision.setApplication(application);
        decision.setDecision(CreditDecision.Decision.APPROVED);
        decision.setProbability(new BigDecimal("0.75"));
        decision.setDecisionThreshold(new BigDecimal("0.5"));
        decision.setModelVersion("1.0");
        decision.setDecisionAt(LocalDateTime.now());
        decision.setResponseTimeMs(150);

        return decisionRepository.save(decision);
    }

    // Complete flow with audit logging
    @PostMapping("/full-flow")
    public Map<String, Object> fullLoanFlow(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        // 1. Create applicant
        LoanApplicant applicant = new LoanApplicant();
        applicant.setName("Flow Test User");
        applicant.setEmail("flow@test.com");
        applicant.setGender(LoanApplicant.Gender.MALE);
        applicant.setCreatedAt(LocalDateTime.now());
        LoanApplicant savedApplicant = applicantRepository.save(applicant);
        response.put("applicant", savedApplicant);

        // 2. Create application
        LoanApplication application = new LoanApplication();
        application.setApplicant(savedApplicant);
        application.setIncome(new BigDecimal("75000"));
        application.setCreditAmount(new BigDecimal("150000"));
        application.setExistingDebt(new BigDecimal("20000"));
        application.setCreditScore(720);
        application.setEmploymentYears(new BigDecimal("8"));
        application.setAppliedAt(LocalDateTime.now());
        LoanApplication savedApplication = applicationRepository.save(application);
        response.put("application", savedApplication);

        // 3. Create decision
        CreditDecision decision = new CreditDecision();
        decision.setApplication(savedApplication);
        decision.setDecision(CreditDecision.Decision.APPROVED);
        decision.setProbability(new BigDecimal("0.85"));
        decision.setDecisionThreshold(new BigDecimal("0.5"));
        decision.setModelVersion("1.0");
        decision.setDecisionAt(LocalDateTime.now());
        decision.setResponseTimeMs(120);
        CreditDecision savedDecision = decisionRepository.save(decision);
        response.put("decision", savedDecision);

        // 4. Create audit log with hash chain
        String snapshot = String.format(
                "{\"applicant\":\"%s\",\"income\":%f,\"creditAmount\":%f,\"decision\":\"%s\",\"probability\":%f}",
                savedApplicant.getName(),
                savedApplication.getIncome(),
                savedApplication.getCreditAmount(),
                savedDecision.getDecision(),
                savedDecision.getProbability()
        );

        AuditTrail audit = auditService.logAction(
                savedApplication,
                savedDecision,
                "FULL_FLOW_TEST",
                snapshot,
                request.getRemoteAddr(),
                "SYSTEM"
        );
        response.put("audit", audit);
        response.put("message", "Full flow completed with audit logging!");

        return response;
    }

    // Verify audit chain integrity
    @GetMapping("/verify-chain")
    public Map<String, Object> verifyChain() {
        Map<String, Object> response = new HashMap<>();
        boolean isValid = auditService.verifyChainIntegrity();
        response.put("chainValid", isValid);
        response.put("message", isValid ? "✅ Audit trail is VALID and tamper-free" : "❌ WARNING: Audit trail has been TAMPERED!");
        return response;
    }

    // Get audit logs for an application
    @GetMapping("/audit/{applicationId}")
    public List<AuditTrail> getAuditLogs(@PathVariable Long applicationId) {
        return auditService.getAuditLogsForApplication(applicationId);
    }

    // Get all audit logs
    @GetMapping("/audit/all")
    public List<AuditTrail> getAllAuditLogs() {
        return auditRepository.findAllOrderByAuditIdAsc();
    }

    @GetMapping("/decision/{applicationId}")
    public CreditDecision getDecisionByApplication(@PathVariable Long applicationId) {
        return decisionRepository.findByApplication_ApplicationId(applicationId)
                .orElseThrow(() -> new RuntimeException("No decision found for application: " + applicationId));
    }

    @GetMapping("/all")
    public String getAllData() {
        long applicantCount = applicantRepository.count();
        long applicationCount = applicationRepository.count();
        long decisionCount = decisionRepository.count();
        long auditCount = auditRepository.count();

        return String.format("Applicants: %d, Applications: %d, Decisions: %d, Audit Logs: %d",
                applicantCount, applicationCount, decisionCount, auditCount);
    }

    @GetMapping("/fairness/calculate")
    public Map<String, Object> calculateFairness() {
        LocalDate startDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now();
        return fairnessService.calculateFairnessMetrics(startDate, endDate);
    }

    @GetMapping("/fairness/report")
    public Map<String, Object> getFairnessReport() {
        return fairnessService.getLatestFairnessReport();
    }

    // NEW: Get historic fairness by attribute
    @GetMapping("/fairness/history")
    public List<FairnessMetric> getFairnessHistory(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String attribute) {
        return fairnessService.getHistoricFairness(
                LocalDate.parse(startDate),
                LocalDate.parse(endDate),
                attribute
        );
    }

    @GetMapping("/ping")
    public String ping() {
        return "Server is running!";
    }
}