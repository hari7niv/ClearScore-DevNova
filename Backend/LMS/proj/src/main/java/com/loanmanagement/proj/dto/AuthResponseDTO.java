package com.loanmanagement.proj.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponseDTO {
    private String userId;
    private String name;
    private String email;
    private String accessToken;
    private String role;
}