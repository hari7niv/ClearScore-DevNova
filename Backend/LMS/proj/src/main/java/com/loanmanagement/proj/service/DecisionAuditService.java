package com.loanmanagement.proj.service;

import com.loanmanagement.proj.entity.DecisionAudit;
import com.loanmanagement.proj.repository.DecisionAuditRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DecisionAuditService {

    private final DecisionAuditRepository decisionAuditRepository;

    public DecisionAuditService(DecisionAuditRepository decisionAuditRepository) {
        this.decisionAuditRepository = decisionAuditRepository;
    }

    public DecisionAudit recordCsvDecision(String authUserId, int csvUserId, double loanAmount, Map<String, Object> result) {
        DecisionAudit audit = new DecisionAudit();
        audit.setUserId(authUserId);
        audit.setCsvUserId(csvUserId);
        audit.setLoanAmount(BigDecimal.valueOf(loanAmount));
        audit.setDecision(String.valueOf(result.getOrDefault("decision", "REVIEW")));
        audit.setProbability(decimal(result.get("probability")));
        audit.setRiskScore(decimal(result.get("risk_score")));
        audit.setRiskLabel(String.valueOf(result.getOrDefault("risk_label", "Medium")));
        audit.setTimestamp(LocalDateTime.now());
        audit.setReasonsJson(toJson(result.getOrDefault("reasons", List.of())));
        audit.setTimelineSnapshotJson(toJson(result.getOrDefault("timeline", List.of())));
        return decisionAuditRepository.save(audit);
    }

    public List<Map<String, Object>> getAllDecisionAudits() {
        return decisionAuditRepository.findAllByOrderByTimestampDesc().stream()
                .map(this::toMap)
                .toList();
    }

    public List<Map<String, Object>> getUserDecisionAudits(String userId) {
        return decisionAuditRepository.findByUserIdOrderByTimestampDesc(userId).stream()
                .map(this::toMap)
                .toList();
    }

    private Map<String, Object> toMap(DecisionAudit audit) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("auditId", audit.getAuditId());
        entry.put("userId", audit.getUserId());
        entry.put("csvUserId", audit.getCsvUserId());
        entry.put("loanAmount", audit.getLoanAmount());
        entry.put("decision", audit.getDecision());
        entry.put("probability", audit.getProbability());
        entry.put("riskScore", audit.getRiskScore());
        entry.put("riskLabel", audit.getRiskLabel());
        entry.put("timestamp", audit.getTimestamp());
        entry.put("reasons", audit.getReasonsJson());
        entry.put("timelineSnapshot", audit.getTimelineSnapshotJson());
        entry.put("source", "decision_audit_table");
        return entry;
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                parts.add(toJson(String.valueOf(entry.getKey())) + ":" + toJson(entry.getValue()));
            }
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> parts = new ArrayList<>();
            for (Object item : iterable) {
                parts.add(toJson(item));
            }
            return "[" + String.join(",", parts) + "]";
        }
        return "\"" + String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
