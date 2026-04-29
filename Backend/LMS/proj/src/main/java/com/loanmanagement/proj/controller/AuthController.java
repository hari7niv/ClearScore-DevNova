package com.loanmanagement.proj.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loanmanagement.proj.dto.AuthRequestDTO;
import com.loanmanagement.proj.dto.AuthResponseDTO;
import com.loanmanagement.proj.service.AuthService;

@RestController
@RequestMapping("/api/auth")

public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@RequestBody AuthRequestDTO request) {
        var result = authService.register(request);
        return ResponseEntity.ok(new AuthResponseDTO(result.userId(), result.name(), result.email(), result.accessToken(), result.role()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody AuthRequestDTO request) {
        var result = authService.login(request);
        return ResponseEntity.ok(new AuthResponseDTO(result.userId(), result.name(), result.email(), result.accessToken(), result.role()));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(@RequestAttribute("authUserId") String userId,
                                                  @RequestAttribute("authEmail") String email) {
        return ResponseEntity.ok(Map.of("userId", userId, "email", email));
    }
}