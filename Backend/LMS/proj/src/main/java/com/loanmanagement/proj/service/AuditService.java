package com.loanmanagement.proj.service;

import com.loanmanagement.proj.entity.AuditTrail;
import com.loanmanagement.proj.entity.CreditDecision;
import com.loanmanagement.proj.entity.LoanApplication;
import com.loanmanagement.proj.repository.AuditTrailRepository;
import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {

    private final AuditTrailRepository auditRepository;
    private String lastHash;

    public AuditService(AuditTrailRepository auditRepository) {
        this.auditRepository = auditRepository;
        // Initialize last hash from database or use genesis block
        this.lastHash = auditRepository.findTopByOrderByAuditIdDesc()
                .map(AuditTrail::getCurrentHash)
                .orElse(calculateHash("GENESIS_BLOCK"));
    }

    // Create a new audit log entry with hash chaining
    public AuditTrail logAction(LoanApplication application,
                                CreditDecision decision,
                                String actionType,
                                String snapshot,
                                String ipAddress,
                                String userId) {

        AuditTrail audit = new AuditTrail();
        audit.setApplication(application);
        audit.setDecision(decision);
        audit.setActionType(actionType);
        audit.setTimestamp(LocalDateTime.now());
        audit.setPreviousHash(lastHash);
        audit.setDecisionSnapshot(snapshot);
        audit.setIpAddress(ipAddress);
        audit.setUserId(userId);

        // Calculate current hash: snapshot + previousHash + timestamp
        String dataToHash = snapshot + lastHash + LocalDateTime.now().toString();
        String currentHash = calculateHash(dataToHash);
        audit.setCurrentHash(currentHash);

        AuditTrail saved = auditRepository.save(audit);
        lastHash = currentHash;

        return saved;
    }

    // Verify entire audit chain integrity
    public boolean verifyChainIntegrity() {
        List<AuditTrail> audits = auditRepository.findAllOrderByAuditIdAsc();

        if (audits.isEmpty()) {
            return true;
        }

        String previousHash = calculateHash("GENESIS_BLOCK");

        for (AuditTrail audit : audits) {
            // Recalculate hash
            String dataToHash = audit.getDecisionSnapshot() + audit.getPreviousHash() + audit.getTimestamp();
            String calculatedHash = calculateHash(dataToHash);

            // Check if current hash matches
            if (!calculatedHash.equals(audit.getCurrentHash())) {
                System.out.println("Hash mismatch at audit ID: " + audit.getAuditId());
                return false;
            }

            // Check if previous hash matches chain
            if (!audit.getPreviousHash().equals(previousHash)) {
                System.out.println("Chain broken at audit ID: " + audit.getAuditId());
                return false;
            }

            previousHash = audit.getCurrentHash();
        }

        return true;
    }

    // Get all audit logs for an application
    public List<AuditTrail> getAuditLogsForApplication(Long applicationId) {
        return auditRepository.findByApplication_ApplicationIdOrderByTimestampDesc(applicationId);
    }

    // SHA-256 Hashing
    private String calculateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hash calculation failed", e);
        }
    }
}