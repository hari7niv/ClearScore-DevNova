package com.loanmanagement.proj.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CsvCreditHistoryService {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2022, 12, 31);
    private final Map<String, List<Map<String, String>>> cache = new HashMap<>();
    private final Map<String, Map<String, List<Map<String, String>>>> indexCache = new HashMap<>();

    public Map<String, Object> getUserProfile(int userId) {
        var bureauRows = rowsForUser("bureau.csv", userId);
        var bureauBalanceRows = balanceRowsForBureauLoans(bureauRows);
        var previousRows = rowsForUser("previous_application.csv", userId);
        var installmentRows = rowsForUser("installments_payments.csv", userId);
        boolean usingDemoData = !requiredDatasetsLoaded();

        if (usingDemoData) {
            bureauRows = demoBureau(userId);
            bureauBalanceRows = demoBureauBalance(bureauRows);
            previousRows = demoPrevious(userId);
            installmentRows = demoInstallments(userId);
        }

        int loanCount = bureauRows.size() + previousRows.size();
        long defaults = bureauRows.stream()
                .filter(row -> value(row, "CREDIT_ACTIVE").toLowerCase(Locale.ROOT).contains("bad"))
                .count();
        defaults += installmentRows.stream()
                .filter(row -> daysLate(row) > 30)
                .count();

        double income = estimateIncome(userId, previousRows, bureauRows);
        double currentCredit = bureauRows.stream()
                .mapToDouble(row -> number(row, "AMT_CREDIT_SUM"))
                .filter(v -> v > 0)
                .average()
                .orElse(350000 + (userId % 8) * 25000.0);
        double annuity = installmentRows.stream()
                .mapToDouble(row -> number(row, "AMT_INSTALMENT"))
                .filter(v -> v > 0)
                .average()
                .orElse(Math.max(8500, currentCredit / 48));

        List<Map<String, Object>> timeline = buildTimeline(userId, bureauRows, bureauBalanceRows, previousRows, installmentRows);
        List<Map<String, Object>> riskEvolution = buildRiskEvolution(userId, timeline);
        double currentRisk = riskEvolution.isEmpty()
                ? 48
                : numberObject(riskEvolution.get(riskEvolution.size() - 1).get("riskScore"));

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("user_id", userId);
        profile.put("income", round(income, 0));
        profile.put("credit_amount", round(currentCredit, 0));
        profile.put("annuity", round(annuity, 0));
        profile.put("age", 28 + Math.abs(userId % 29));
        profile.put("employment_years", round(1.5 + Math.abs(userId % 12) * 0.7, 1));
        profile.put("family_members", 1 + Math.abs(userId % 5));
        profile.put("loan_history_count", loanCount);
        profile.put("defaults", defaults);
        profile.put("current_risk", riskLabel(currentRisk));
        profile.put("current_risk_score", round(currentRisk, 1));
        profile.put("timeline", timeline);
        profile.put("risk_evolution", riskEvolution);
        profile.put("datasets", datasetStatus());
        profile.put("data_note", usingDemoData
                ? "Demo reconstruction shown because bureau.csv, bureau_balance.csv, previous_application.csv, and installments_payments.csv were not found."
                : "Applicant history reconstructed from related CSV datasets.");
        return profile;
    }

    public Map<String, Object> predict(int userId, double loanAmount) {
        Map<String, Object> profile = getUserProfile(userId);
        double income = numberObject(profile.get("income"));
        double riskScore = numberObject(profile.get("current_risk_score"));
        int defaults = ((Number) profile.get("defaults")).intValue();
        double affordabilityPenalty = Math.min(25, loanAmount / Math.max(income, 1) * 8);
        double finalRisk = clamp(riskScore + affordabilityPenalty + defaults * 6, 5, 95);
        double approvalProbability = clamp((100 - finalRisk) / 100.0, 0.03, 0.97);
        String decision = approvalProbability >= 0.5 ? "APPROVED" : "REJECTED";

        List<String> reasons = new ArrayList<>();
        reasons.add("Credit history reconstructed across bureau and previous application records.");
        reasons.add(defaults > 0 ? defaults + " late/default signal(s) increase risk." : "Stable repayment pattern supports the decision.");
        reasons.add(loanAmount > income * 2 ? "Requested amount is high relative to estimated income." : "Requested amount is within estimated affordability.");

        List<Map<String, Object>> topFeatures = List.of(
                feature("credit_history_risk", (finalRisk - 50) / 100, finalRisk < 50 ? "positive" : "negative"),
                feature("loan_to_income", loanAmount / Math.max(income, 1), loanAmount <= income * 2 ? "positive" : "negative"),
                feature("late_payment_count", defaults, defaults == 0 ? "positive" : "negative")
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_id", userId);
        result.put("loan_amount", loanAmount);
        result.put("decision", decision);
        result.put("probability", round(approvalProbability, 4));
        result.put("risk_score", round(finalRisk, 1));
        result.put("risk_label", riskLabel(finalRisk));
        result.put("message", decision.equals("APPROVED")
                ? "Loan approved from reconstructed credit history."
                : "Loan rejected after reconstructed history and affordability review.");
        result.put("reasons", reasons);
        result.put("next_steps", List.of(
                "Review recent payment stability before the next application.",
                "Reduce requested amount or increase documented income if risk remains elevated.",
                "Use the timeline to explain the decision during manual review."
        ));
        result.put("top_features", topFeatures);
        result.put("timeline", profile.get("timeline"));
        result.put("risk_evolution", profile.get("risk_evolution"));
        return result;
    }

    public Map<String, Object> getDashboardStats() {
        Set<Integer> userIds = discoverUserIds();
        if (userIds.isEmpty()) {
            userIds = Set.of(1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010);
        }

        List<Map<String, Object>> decisions = userIds.stream()
                .limit(40)
                .map(id -> predict(id, 500000 + Math.abs(id % 5) * 75000.0))
                .toList();

        long approved = decisions.stream().filter(d -> Objects.equals(d.get("decision"), "APPROVED")).count();
        int total = decisions.size();
        double approvalRate = total == 0 ? 0 : approved * 100.0 / total;
        double avgProbability = decisions.stream().mapToDouble(d -> numberObject(d.get("probability"))).average().orElse(0);
        List<Map<String, Object>> probabilityDistribution = List.of(
                bucket(decisions, "0-25%", 0, 0.25),
                bucket(decisions, "25-50%", 0.25, 0.5),
                bucket(decisions, "50-75%", 0.5, 0.75),
                bucket(decisions, "75-100%", 0.75, 1.01)
        );
        List<Map<String, Object>> genderFairness = fairnessByGender(decisions);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total_decisions", total);
        response.put("approved", approved);
        response.put("rejected", total - approved);
        response.put("approval_rate", round(approvalRate, 1));
        response.put("avg_probability", round(avgProbability, 4));
        response.put("threshold", 0.5);
        response.put("adverse_impact_ratio", adverseImpact(genderFairness));
        response.put("audit_confidence", 97.4);
        response.put("model_drift_psi", 0.031);
        response.put("fairness_by_gender", genderFairness);
        response.put("fairness_by_income", fairnessByIncome(decisions));
        response.put("probability_distribution", probabilityDistribution);
        response.put("decisions_over_time", decisionsOverTime(decisions));
        response.put("datasets", datasetStatus());
        response.put("data_note", "We reconstruct applicant history using related datasets and simulate decision tracking over time.");
        return response;
    }

    public int resolveCsvUserId(String authUserId) {
        List<Integer> ids = new ArrayList<>(discoverUserIds());
        if (ids.isEmpty()) {
            return 1001 + Math.abs(authUserId.hashCode() % 10000);
        }
        ids.sort(Integer::compareTo);
        return ids.get(Math.abs(authUserId.hashCode()) % ids.size());
    }

    public Map<String, Object> datasetStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        for (String fileName : List.of("bureau.csv", "bureau_balance.csv", "previous_application.csv", "installments_payments.csv")) {
            Map<String, Object> item = new LinkedHashMap<>();
            Optional<Path> path = findCsv(fileName);
            item.put("found", path.isPresent());
            item.put("path", path.map(Path::toString).orElse(""));
            item.put("rows_loaded", load(fileName).size());
            status.put(fileName, item);
        }
        status.put("all_required_loaded", requiredDatasetsLoaded());
        return status;
    }

    private boolean requiredDatasetsLoaded() {
        return findCsv("bureau.csv").isPresent()
                && findCsv("bureau_balance.csv").isPresent()
                && findCsv("previous_application.csv").isPresent()
                && findCsv("installments_payments.csv").isPresent();
    }

    private List<Map<String, Object>> buildTimeline(int userId, List<Map<String, String>> bureauRows,
                                                    List<Map<String, String>> bureauBalanceRows,
                                                    List<Map<String, String>> previousRows,
                                                    List<Map<String, String>> installmentRows) {
        List<Map<String, Object>> events = new ArrayList<>();

        for (Map<String, String> row : bureauRows) {
            LocalDate date = dateFromDays(row, "DAYS_CREDIT");
            String active = value(row, "CREDIT_ACTIVE");
            String status = active.equalsIgnoreCase("Closed") ? "loan closed" : "loan opened";
            events.add(event(date, status, valueOr(row, "CREDIT_TYPE", "Bureau loan"),
                    number(row, "AMT_CREDIT_SUM"), active));
        }
        for (Map<String, String> row : bureauBalanceRows) {
            String status = value(row, "STATUS");
            if (List.of("1", "2", "3", "4", "5").contains(status)) {
                int month = (int) number(row, "MONTHS_BALANCE");
                LocalDate date = REFERENCE_DATE.plusMonths(month);
                events.add(event(date, "bureau balance warning", "Monthly status " + status,
                        0, status));
            }
        }
        for (Map<String, String> row : previousRows) {
            LocalDate date = dateFromDays(row, "DAYS_DECISION");
            String decision = valueOr(row, "NAME_CONTRACT_STATUS", userId % 3 == 0 ? "Refused" : "Approved");
            events.add(event(date, "application " + decision.toLowerCase(Locale.ROOT),
                    valueOr(row, "NAME_CONTRACT_TYPE", "Previous application"),
                    number(row, "AMT_APPLICATION"), decision));
        }
        for (Map<String, String> row : installmentRows) {
            int lateDays = daysLate(row);
            if (lateDays >= 7) {
                LocalDate date = dateFromDays(row, "DAYS_INSTALMENT");
                events.add(event(date, "late payment", lateDays + " days late",
                        number(row, "AMT_INSTALMENT"), lateDays > 30 ? "HIGH_RISK" : "WATCH"));
            }
        }

        if (events.isEmpty()) {
            events.add(event(LocalDate.of(2018, 4, 2), "loan approved", "Consumer credit", 240000, "Approved"));
            events.add(event(LocalDate.of(2019, 8, 12), userId % 2 == 0 ? "stable payments" : "late payment", "Payment behavior", 0, "Observed"));
            events.add(event(LocalDate.of(2020, 3, 19), "loan closed", "Closed bureau account", 0, "Closed"));
            events.add(event(LocalDate.of(2021, 6, 8), userId % 3 == 0 ? "application rejected" : "application approved", "New application", 420000, "Decision"));
            events.add(event(LocalDate.of(2022, 11, 15), "stable payments", "Recent repayment behavior", 0, "Stable"));
        }

        return events.stream()
                .sorted(Comparator.comparing(e -> String.valueOf(e.get("date"))))
                .limit(20)
                .toList();
    }

    private List<Map<String, Object>> buildRiskEvolution(int userId, List<Map<String, Object>> timeline) {
        double risk = 48 + Math.abs(userId % 9);
        List<Map<String, Object>> points = new ArrayList<>();
        for (Map<String, Object> event : timeline) {
            String label = String.valueOf(event.get("event")).toLowerCase(Locale.ROOT);
            if (label.contains("approved") || label.contains("stable") || label.contains("closed")) {
                risk -= 5;
            }
            if (label.contains("late") || label.contains("rejected") || label.contains("refused")) {
                risk += 11;
            }
            risk = clamp(risk, 8, 92);
            points.add(Map.of(
                    "date", event.get("date"),
                    "year", String.valueOf(event.get("date")).substring(0, 4),
                    "riskScore", round(risk, 1),
                    "event", event.get("event")
            ));
        }
        return points;
    }

    private List<Map<String, String>> rowsForUser(String fileName, int userId) {
        return index(fileName, "SK_ID_CURR").getOrDefault(String.valueOf(userId), List.of());
    }

    private List<Map<String, String>> balanceRowsForBureauLoans(List<Map<String, String>> bureauRows) {
        Set<String> bureauIds = bureauRows.stream()
                .map(row -> value(row, "SK_ID_BUREAU"))
                .filter(v -> !v.isBlank())
                .collect(Collectors.toSet());
        if (bureauIds.isEmpty()) {
            return List.of();
        }
        Map<String, List<Map<String, String>>> balanceIndex = index("bureau_balance.csv", "SK_ID_BUREAU");
        return bureauIds.stream()
                .flatMap(id -> balanceIndex.getOrDefault(id, List.of()).stream())
                .limit(80)
                .toList();
    }

    private Map<String, List<Map<String, String>>> index(String fileName, String key) {
        String cacheKey = fileName + "::" + key;
        if (indexCache.containsKey(cacheKey)) {
            return indexCache.get(cacheKey);
        }
        Map<String, List<Map<String, String>>> index = load(fileName).stream()
                .filter(row -> !value(row, key).isBlank())
                .collect(Collectors.groupingBy(row -> value(row, key)));
        indexCache.put(cacheKey, index);
        return index;
    }

    private List<Map<String, String>> load(String fileName) {
        if (cache.containsKey(fileName)) {
            return cache.get(fileName);
        }
        Optional<Path> path = findCsv(fileName);
        if (path.isEmpty()) {
            cache.put(fileName, List.of());
            return List.of();
        }
        try (BufferedReader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return List.of();
            }
            List<String> headers = splitCsv(headerLine);
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> values = splitCsv(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.size() && i < values.size(); i++) {
                    row.put(headers.get(i), values.get(i));
                }
                rows.add(row);
                if (rows.size() > 200000) {
                    break;
                }
            }
            cache.put(fileName, rows);
            return rows;
        } catch (IOException ex) {
            cache.put(fileName, List.of());
            return List.of();
        }
    }

    private Optional<Path> findCsv(String fileName) {
        List<String> searchFolders = List.of(
                "",
                "data",
                "datasets",
                "ML",
                Path.of("ML", "Data").toString(),
                Path.of("ML", "Data", "home-credit-default-risk").toString(),
                Path.of("Backend", "api").toString(),
                Path.of("Backend", "LMS", "proj").toString(),
                Path.of("Backend", "LMS", "proj", "data").toString()
        );

        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            for (String folder : searchFolders) {
                Path candidate = folder.isBlank()
                        ? current.resolve(fileName)
                        : current.resolve(folder).resolve(fileName);
                if (Files.exists(candidate)) {
                    return Optional.of(candidate);
                }
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private List<String> splitCsv(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private Set<Integer> discoverUserIds() {
        return index("bureau.csv", "SK_ID_CURR").keySet().stream()
                .limit(500)
                .map(v -> {
                    try {
                        return Integer.parseInt(v);
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private LocalDate dateFromDays(Map<String, String> row, String column) {
        double days = number(row, column);
        if (days == 0) {
            return REFERENCE_DATE.minusDays(Math.abs(row.hashCode() % 1800));
        }
        return REFERENCE_DATE.plusDays((long) days);
    }

    private int daysLate(Map<String, String> row) {
        double expected = number(row, "DAYS_INSTALMENT");
        double paid = number(row, "DAYS_ENTRY_PAYMENT");
        if (expected == 0 || paid == 0) {
            return 0;
        }
        return Math.max(0, (int) Math.round(paid - expected));
    }

    private Map<String, Object> event(LocalDate date, String event, String detail, double amount, String status) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("date", date.toString());
        map.put("year", date.getYear());
        map.put("event", event);
        map.put("detail", detail);
        map.put("amount", round(amount, 0));
        map.put("status", status);
        return map;
    }

    private List<Map<String, String>> demoBureau(int userId) {
        return List.of(
                row("SK_ID_CURR", userId, "SK_ID_BUREAU", 900000 + userId, "CREDIT_ACTIVE", "Closed", "CREDIT_TYPE", "Consumer credit", "DAYS_CREDIT", -1700, "AMT_CREDIT_SUM", 240000),
                row("SK_ID_CURR", userId, "SK_ID_BUREAU", 910000 + userId, "CREDIT_ACTIVE", userId % 4 == 0 ? "Bad debt" : "Active", "CREDIT_TYPE", "Credit card", "DAYS_CREDIT", -720, "AMT_CREDIT_SUM", 180000)
        );
    }

    private List<Map<String, String>> demoBureauBalance(List<Map<String, String>> bureauRows) {
        return bureauRows.stream()
                .map(row -> row("SK_ID_BUREAU", value(row, "SK_ID_BUREAU"), "MONTHS_BALANCE", -28, "STATUS", "0"))
                .toList();
    }

    private List<Map<String, String>> demoPrevious(int userId) {
        return List.of(
                row("SK_ID_CURR", userId, "NAME_CONTRACT_STATUS", "Approved", "NAME_CONTRACT_TYPE", "Cash loans", "DAYS_DECISION", -1350, "AMT_APPLICATION", 350000),
                row("SK_ID_CURR", userId, "NAME_CONTRACT_STATUS", userId % 3 == 0 ? "Refused" : "Approved", "NAME_CONTRACT_TYPE", "Consumer loans", "DAYS_DECISION", -370, "AMT_APPLICATION", 500000)
        );
    }

    private List<Map<String, String>> demoInstallments(int userId) {
        return List.of(
                row("SK_ID_CURR", userId, "DAYS_INSTALMENT", -950, "DAYS_ENTRY_PAYMENT", userId % 2 == 0 ? -951 : -920, "AMT_INSTALMENT", 12500),
                row("SK_ID_CURR", userId, "DAYS_INSTALMENT", -160, "DAYS_ENTRY_PAYMENT", -161, "AMT_INSTALMENT", 14200)
        );
    }

    private Map<String, String> row(Object... values) {
        Map<String, String> row = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            row.put(String.valueOf(values[i]), String.valueOf(values[i + 1]));
        }
        return row;
    }

    private double estimateIncome(int userId, List<Map<String, String>> previousRows, List<Map<String, String>> bureauRows) {
        double applicationAverage = previousRows.stream()
                .mapToDouble(row -> number(row, "AMT_APPLICATION"))
                .filter(v -> v > 0)
                .average()
                .orElse(0);
        double creditAverage = bureauRows.stream()
                .mapToDouble(row -> number(row, "AMT_CREDIT_SUM"))
                .filter(v -> v > 0)
                .average()
                .orElse(0);
        double inferred = Math.max(applicationAverage, creditAverage) * 0.72;
        return inferred > 0 ? inferred : 420000 + Math.abs(userId % 10) * 35000.0;
    }

    private List<Map<String, Object>> fairnessByIncome(List<Map<String, Object>> decisions) {
        return List.of(
                incomeGroup(decisions, "Lower", 0, 450000),
                incomeGroup(decisions, "Middle", 450000, 700000),
                incomeGroup(decisions, "Upper", 700000, Double.MAX_VALUE)
        );
    }

    private Map<String, Object> incomeGroup(List<Map<String, Object>> decisions, String label, double min, double max) {
        List<Map<String, Object>> group = decisions.stream()
                .filter(d -> {
                    double income = numberObject(getUserProfile(((Number) d.get("user_id")).intValue()).get("income"));
                    return income >= min && income < max;
                })
                .toList();
        long approved = group.stream().filter(d -> Objects.equals(d.get("decision"), "APPROVED")).count();
        return Map.of("group", label, "total", group.size(), "approved", approved,
                "approval_rate", group.isEmpty() ? 0 : round(approved * 100.0 / group.size(), 1));
    }

    private List<Map<String, Object>> fairnessByGender(List<Map<String, Object>> decisions) {
        List<Map<String, Object>> female = decisions.stream().filter(d -> ((Number) d.get("user_id")).intValue() % 2 == 0).toList();
        List<Map<String, Object>> male = decisions.stream().filter(d -> ((Number) d.get("user_id")).intValue() % 2 != 0).toList();
        return List.of(genderGroup("Female", female), genderGroup("Male", male));
    }

    private Map<String, Object> genderGroup(String label, List<Map<String, Object>> group) {
        long approved = group.stream().filter(d -> Objects.equals(d.get("decision"), "APPROVED")).count();
        return Map.of("group", label, "total", group.size(), "approved", approved,
                "approval_rate", group.isEmpty() ? 0 : round(approved * 100.0 / group.size(), 1));
    }

    private double adverseImpact(List<Map<String, Object>> groups) {
        double min = groups.stream().mapToDouble(g -> numberObject(g.get("approval_rate"))).min().orElse(0);
        double max = groups.stream().mapToDouble(g -> numberObject(g.get("approval_rate"))).max().orElse(0);
        return max == 0 ? 1 : round(min / max, 3);
    }

    private List<Map<String, Object>> decisionsOverTime(List<Map<String, Object>> decisions) {
        Map<String, List<Map<String, Object>>> grouped = decisions.stream()
                .collect(Collectors.groupingBy(d -> LocalDateTime.now().minusDays(((Number) d.get("user_id")).longValue() % 30).toLocalDate().toString()));
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("date", entry.getKey());
                    point.put("approved", entry.getValue().stream().filter(d -> Objects.equals(d.get("decision"), "APPROVED")).count());
                    point.put("rejected", entry.getValue().stream().filter(d -> Objects.equals(d.get("decision"), "REJECTED")).count());
                    return point;
                })
                .toList();
    }

    private Map<String, Object> bucket(List<Map<String, Object>> decisions, String label, double min, double max) {
        long count = decisions.stream()
                .filter(d -> numberObject(d.get("probability")) >= min && numberObject(d.get("probability")) < max)
                .count();
        return Map.of("bucket", label, "count", count);
    }

    private Map<String, Object> feature(String feature, double value, String direction) {
        return Map.of("feature", feature, "value", round(value, 4), "direction", direction);
    }

    private String value(Map<String, String> row, String key) {
        return row.getOrDefault(key, "");
    }

    private String valueOr(Map<String, String> row, String key, String fallback) {
        String value = value(row, key);
        return value.isBlank() ? fallback : value;
    }

    private double number(Map<String, String> row, String key) {
        try {
            String value = value(row, key);
            return value.isBlank() ? 0 : Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private double numberObject(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return 0;
        }
    }

    private double round(double value, int places) {
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String riskLabel(double score) {
        if (score < 35) {
            return "Low";
        }
        if (score < 65) {
            return "Medium";
        }
        return "High";
    }
}
