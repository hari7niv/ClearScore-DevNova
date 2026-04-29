package com.loanmanagement.proj.repository;

import com.loanmanagement.proj.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
    Optional<UserAccount> findByUserId(String userId);
    boolean existsByEmail(String email);
}
