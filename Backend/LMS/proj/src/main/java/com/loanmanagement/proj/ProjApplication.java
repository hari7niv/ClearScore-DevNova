package com.loanmanagement.proj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProjApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProjApplication.class, args);
        System.out.println("🚀 Credit Scoring System Started!");
        System.out.println("📋 Available endpoints:");
        System.out.println("   POST /api/loan/apply");
        System.out.println("   GET  /api/loan/decision/{id}");
        System.out.println("   GET  /api/loan/verify-audit");
	}

}
