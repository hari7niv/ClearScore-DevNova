package com.loanmanagement.proj.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loanmanagement.proj.entity.AuditTrail;
import com.loanmanagement.proj.entity.CreditDecision;
import com.loanmanagement.proj.entity.LoanApplication;
import com.loanmanagement.proj.entity.ShapExplanation;
import com.loanmanagement.proj.repository.AuditTrailRepository;
import com.loanmanagement.proj.repository.CreditDecisionRepository;
import com.loanmanagement.proj.repository.LoanApplicationRepository;
import com.loanmanagement.proj.repository.ShapExplanationRepository;
import com.loanmanagement.proj.service.AuditService;

@RestController
@RequestMapping("/api/audit")

public class AuditController {

    private final AuditTrailRepository auditRepository;
    private final LoanApplicationRepository applicationRepository;
    private final CreditDecisionRepository decisionRepository;
    private final ShapExplanationRepository shapRepository;
    private final AuditService auditService;

    public AuditController(AuditTrailRepository auditRepository,
                           LoanApplicationRepository applicationRepository,
                           CreditDecisionRepository decisionRepository,
                           ShapExplanationRepository shapRepository,
                           AuditService auditService) {
        this.auditRepository = auditRepository;
        this.applicationRepository = applicationRepository;
        this.decisionRepository = decisionRepository;
        this.shapRepository = shapRepository;
        this.auditService = auditService;
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> getAllAuditLogs() {
        List<AuditTrail> audits = auditRepository.findAllByOrderByTimestampDesc();
        return audits.stream().map(audit -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("auditId", audit.getAuditId());
            entry.put("actionType", audit.getActionType());
            entry.put("timestamp", audit.getTimestamp());
            entry.put("userId", audit.getUserId());
            entry.put("ipAddress", audit.getIpAddress());

            if (audit.getApplication() != null) {
                LoanApplication app = audit.getApplication();
                entry.put("applicationId", app.getApplicationId());
                entry.put("income", app.getIncome());
                entry.put("creditAmount", app.getCreditAmount());
                entry.put("creditScore", app.getCreditScore());
                if (app.getApplicant() != null) {
                    entry.put("applicantName", app.getApplicant().getName());
                    entry.put("gender", app.getApplicant().getGender());
                }
            }

            if (audit.getDecision() != null) {
                CreditDecision decision = audit.getDecision();
                entry.put("decision", decision.getDecision().name());
                entry.put("probability", decision.getProbability());
                entry.put("modelVersion", decision.getModelVersion());
            }

            return entry;
        }).toList();
    }

    @GetMapping("/logs/{applicationId}")
    public Map<String, Object> getAuditForApplication(@PathVariable Long applicationId) {
        Map<String, Object> response = new HashMap<>();

        LoanApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        CreditDecision decision = decisionRepository.findByApplication_ApplicationId(applicationId)
                .orElse(null);

        List<AuditTrail> auditLogs = auditService.getAuditLogsForApplication(applicationId);

        response.put("application", application);
        response.put("decision", decision);
        response.put("auditLogs", auditLogs);

        if (decision != null) {
            List<ShapExplanation> shapExplanations = shapRepository
                    .findByDecision_DecisionIdOrderByShapValueDesc(decision.getDecisionId());
            response.put("shapExplanations", shapExplanations);
        }

        return response;
    }

    @GetMapping("/verify")
    public Map<String, Object> verifyAuditChain() {
        Map<String, Object> response = new HashMap<>();
        boolean isValid = auditService.verifyChainIntegrity();
        response.put("chainValid", isValid);
        response.put("message", isValid
                ? "Audit trail is intact and tamper-free"
                : "WARNING: Audit trail integrity compromised");
        return response;
    }

    @GetMapping("/stats")
    public Map<String, Object> getAuditStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalApplications = applicationRepository.count();
        List<CreditDecision> allDecisions = decisionRepository.findAll();
        long approvedCount = allDecisions.stream()
                .filter(d -> d.getDecision() == CreditDecision.Decision.APPROVED)
                .count();

        double approvalRate = totalApplications > 0
                ? (double) approvedCount / totalApplications * 100
                : 0;

        double avgScore = applicationRepository.findAll().stream()
                .filter(app -> app.getCreditScore() != null)
                .mapToInt(LoanApplication::getCreditScore)
                .average()
                .orElse(0);

        BigDecimal totalVolume = applicationRepository.findAll().stream()
                .filter(app -> app.getCreditAmount() != null)
                .map(LoanApplication::getCreditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        stats.put("totalApplications", totalApplications);
        stats.put("approvedCount", approvedCount);
        stats.put("rejectedCount", totalApplications - approvedCount);
        stats.put("approvalRate", new BigDecimal(approvalRate).setScale(1, RoundingMode.HALF_UP));
        stats.put("avgCreditScore", Math.round(avgScore));
        stats.put("totalVolume", totalVolume);
        stats.put("auditCount", auditRepository.count());

        return stats;
    }
}
