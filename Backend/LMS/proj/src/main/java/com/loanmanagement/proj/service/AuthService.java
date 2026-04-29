package com.loanmanagement.proj.service;

import com.loanmanagement.proj.dto.AuthRequestDTO;
import com.loanmanagement.proj.entity.UserAccount;
import com.loanmanagement.proj.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final JwtTokenService jwtTokenService;
    private final CsvCreditHistoryService csvCreditHistoryService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserAccountRepository userAccountRepository,
                       JwtTokenService jwtTokenService,
                       CsvCreditHistoryService csvCreditHistoryService) {
        this.userAccountRepository = userAccountRepository;
        this.jwtTokenService = jwtTokenService;
        this.csvCreditHistoryService = csvCreditHistoryService;
    }

    public AuthResult register(AuthRequestDTO request) {
        String email = normalizeEmail(request.getEmail());
        if (email.isBlank() || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Email and password are required");
        }
        if (userAccountRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        String userId = UUID.randomUUID().toString();
        String salt = randomSalt();
        String passwordHash = hashPassword(request.getPassword(), salt);
        Role role = resolveRole(email, request.getRole());
        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setCsvUserId(csvCreditHistoryService.resolveCsvUserId(userId));
        account.setName(request.getName());
        account.setEmail(email);
        account.setPasswordSalt(salt);
        account.setPasswordHash(passwordHash);
        account.setRole(role.name());
        account.setCreatedAt(java.time.LocalDateTime.now());
        userAccountRepository.save(account);
        String token = jwtTokenService.createToken(userId, email, request.getName(), role.name());
        return new AuthResult(userId, request.getName(), email, token, role.name());
    }

    public AuthResult login(AuthRequestDTO request) {
        String email = normalizeEmail(request.getEmail());
        UserAccount account = userAccountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        String passwordHash = hashPassword(request.getPassword(), account.getPasswordSalt());
        if (!passwordHash.equals(account.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        String token = jwtTokenService.createToken(account.getUserId(), account.getEmail(), account.getName(), account.getRole());
        return new AuthResult(account.getUserId(), account.getName(), account.getEmail(), token, account.getRole());
    }

    public String resolveUserId(String bearerToken) {
        String token = bearerToken != null && bearerToken.startsWith("Bearer ") ? bearerToken.substring(7) : bearerToken;
        Map<String, Object> payload = jwtTokenService.verifyToken(token);
        return String.valueOf(payload.get("sub"));
    }

    private String randomSalt() {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (Exception ex) {
            throw new RuntimeException("Password hashing failed", ex);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private enum Role {
        USER,
        ADMIN
    }

    private Role resolveRole(String email, String requestedRole) {
        if (requestedRole != null && !requestedRole.isBlank()) {
            try {
                return Role.valueOf(requestedRole.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return Role.USER;
            }
        }
        String normalized = email == null ? "" : email.toLowerCase();
        if (normalized.contains("admin") || normalized.contains("compliance")) {
            return Role.ADMIN;
        }
        return Role.USER;
    }

    public record AuthResult(String userId, String name, String email, String accessToken, String role) {}
}
