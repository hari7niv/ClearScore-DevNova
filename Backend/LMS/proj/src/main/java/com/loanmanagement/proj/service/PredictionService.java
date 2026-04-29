package com.loanmanagement.proj.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.loanmanagement.proj.dto.LoanRequestDTO;

@Service
public class PredictionService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final CsvCreditHistoryService csvCreditHistoryService;

    @Value("${model.service.url:http://localhost:5000}")
    private String modelServiceUrl;

    @Value("${csv.predictor.url:http://localhost:8000}")
    private String csvPredictorUrl;

    public PredictionService(CsvCreditHistoryService csvCreditHistoryService) {
        this.csvCreditHistoryService = csvCreditHistoryService;
    }

    public Map<String, Object> predict(LoanRequestDTO request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new HashMap<>();
            payload.put("user_id", request.getUserId());
            payload.put("income", request.getIncome());
            payload.put("credit_amount", request.getCreditAmount());
            payload.put("loan_term", request.getLoanTerm());
            payload.put("existing_debt", request.getExistingDebt());
            payload.put("credit_score", request.getCreditScore());
            payload.put("employment_years", request.getEmploymentYears());
            payload.put("age", request.getAge());
            payload.put("purpose", request.getPurpose());
            payload.put("region", request.getRegion());
            payload.put("gender", request.getGender());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    modelServiceUrl + "/predict",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new RuntimeException("Empty prediction response from ML service");
            }
            return body;
        } catch (Exception ex) {
            throw new RuntimeException("Prediction service unavailable: " + ex.getMessage(), ex);
        }
    }

    public Map<String, Object> predictFromCsv(int userId, double loanAmount) {
        return csvCreditHistoryService.predict(userId, loanAmount);
    }

    public Map<String, Object> getUserProfile(int csvId) {
        return csvCreditHistoryService.getUserProfile(csvId);
    }

    public int resolveCsvUserId(String authUserId) {
        return csvCreditHistoryService.resolveCsvUserId(authUserId);
    }

    public List<String> getPlainLanguageReasons(Map<String, BigDecimal> shapValues, String decision) {
        return shapValues.entrySet().stream()
                .sorted((a, b) -> Double.compare(Math.abs(b.getValue().doubleValue()), Math.abs(a.getValue().doubleValue())))
                .limit(3)
                .map(entry -> {
                    String direction = entry.getValue().doubleValue() >= 0 ? "supports" : "pushes against";
                    return entry.getKey().replace("_", " ") + " " + direction + " the decision";
                })
                .toList();
    }

    public List<String> getActionableSteps(Map<String, BigDecimal> shapValues, String decision) {
        return List.of(
                "Lower your debt-to-income ratio before reapplying.",
                "Improve credit score and maintain stable employment.",
                "Request a smaller amount or longer tenure to reduce risk."
        );
    }
}
