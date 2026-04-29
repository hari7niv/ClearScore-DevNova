package com.loanmanagement.proj.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    @Value("${app.jwt.secret:dev-secret-change-me}")
    private String secret;

    public String createToken(String userId, String email, String name, String role) {
        try {
            String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            String payloadJson = String.format(
                    "{\"sub\":\"%s\",\"email\":\"%s\",\"name\":\"%s\",\"role\":\"%s\",\"iat\":%d,\"exp\":%d}",
                    escape(userId), escape(email), escape(name), escape(role),
                    Instant.now().getEpochSecond(),
                    Instant.now().plusSeconds(60L * 60L * 24L).getEpochSecond()
            );

            String encodedHeader = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = base64Url(sign(signingInput));
            return signingInput + "." + signature;
        } catch (Exception ex) {
            throw new RuntimeException("Unable to create token", ex);
        }
    }

    public Map<String, Object> verifyToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid token format");
            }

            String signingInput = parts[0] + "." + parts[1];
            String expectedSignature = base64Url(sign(signingInput));
            if (!expectedSignature.equals(parts[2])) {
                throw new IllegalArgumentException("Token signature mismatch");
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", extractString(payloadJson, "sub"));
            payload.put("email", extractString(payloadJson, "email"));
            payload.put("name", extractString(payloadJson, "name"));
            payload.put("role", extractString(payloadJson, "role"));
            payload.put("iat", extractNumber(payloadJson, "iat"));
            payload.put("exp", extractNumber(payloadJson, "exp"));

            long exp = (long) payload.get("exp");
            if (exp < Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("Token expired");
            }
            return payload;
        } catch (Exception ex) {
            throw new RuntimeException("Invalid token", ex);
        }
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extractString(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private long extractNumber(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0L;
    }
}
