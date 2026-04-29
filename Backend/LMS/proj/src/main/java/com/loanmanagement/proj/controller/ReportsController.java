package com.loanmanagement.proj.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loanmanagement.proj.entity.FairnessMetric;
import com.loanmanagement.proj.service.CsvCreditHistoryService;
import com.loanmanagement.proj.service.FairnessService;

@RestController
@RequestMapping("/api/reports")

public class ReportsController {

    private final FairnessService fairnessService;
    private final CsvCreditHistoryService csvCreditHistoryService;

    public ReportsController(FairnessService fairnessService,
                             CsvCreditHistoryService csvCreditHistoryService) {
        this.fairnessService = fairnessService;
        this.csvCreditHistoryService = csvCreditHistoryService;
    }

    @GetMapping("/fairness")
    public Map<String, Object> getFairnessReport() {
        return fairnessService.getLatestFairnessReport();
    }

    @GetMapping("/fairness/calculate")
    public Map<String, Object> calculateFairness(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusDays(90);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        return fairnessService.calculateFairnessMetrics(start, end);
    }

    @GetMapping("/fairness/history")
    public List<FairnessMetric> getFairnessHistory(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String attribute) {
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusDays(90);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        return fairnessService.getHistoricFairness(start, end, attribute);
    }

    @GetMapping("/csv-stats")
    public Map getCsvStats() {
        return csvCreditHistoryService.getDashboardStats();
    }
}
