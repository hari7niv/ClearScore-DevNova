package com.loanmanagement.proj.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_accounts")
@Data
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    private Integer csvUserId;

    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    private String passwordSalt;
    private String passwordHash;
    private String role;
    private LocalDateTime createdAt;
}
