package com.loanmanagement.proj.dto;

import lombok.Data;

@Data
public class AuthRequestDTO {
    private String name;
    private String email;
    private String password;
    private String role;
}