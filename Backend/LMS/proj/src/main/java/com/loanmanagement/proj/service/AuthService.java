package com.loanmanagement.proj.service;

import com.loanmanagement.proj.dto.AuthRequestDTO;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final Map<String, Account> accountsByEmail = new ConcurrentHashMap<>();
    private final JwtTokenService jwtTokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    public AuthResult register(AuthRequestDTO request) {
        if (accountsByEmail.containsKey(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        String userId = UUID.randomUUID().toString();
        String salt = randomSalt();
        String passwordHash = hashPassword(request.getPassword(), salt);
        Role role = resolveRole(request.getEmail(), request.getRole());
        Account account = new Account(userId, request.getName(), request.getEmail(), salt, passwordHash, role);
        accountsByEmail.put(request.getEmail(), account);
        String token = jwtTokenService.createToken(userId, request.getEmail(), request.getName(), role.name());
        return new AuthResult(userId, request.getName(), request.getEmail(), token, role.name());
    }

    public AuthResult login(AuthRequestDTO request) {
        Account account = Optional.ofNullable(accountsByEmail.get(request.getEmail()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        String passwordHash = hashPassword(request.getPassword(), account.salt);
        if (!passwordHash.equals(account.passwordHash)) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        String token = jwtTokenService.createToken(account.userId, account.email, account.name, account.role.name());
        return new AuthResult(account.userId, account.name, account.email, token, account.role.name());
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

    private record Account(String userId, String name, String email, String salt, String passwordHash, Role role) {}

    public record AuthResult(String userId, String name, String email, String accessToken, String role) {}
}