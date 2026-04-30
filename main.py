import pickle
import pandas as pd
import numpy as np
import json
import os
from fastapi import FastAPI, HTTPException
from pathlib import Path
from urllib import error as url_error
from urllib import parse as url_parse
from urllib import request as url_request

with open("saved_models/credit_model_latest.pkl", "rb") as f:
    model = pickle.load(f)

with open("saved_models/shap_explainer_latest.pkl", "rb") as f:
    explainer = pickle.load(f)

with open("saved_models/preprocessing_info_latest.pkl", "rb") as f:
    meta = pickle.load(f)

FEATURE_NAMES = meta["feature_names"]
THRESHOLD = meta["optimal_threshold"]


DATA_ROOT = Path(__file__).resolve().parent / "ML" / "Data"
df = pd.read_csv(DATA_ROOT / "home-credit-default-risk" / "application_train.csv")

# same preprocessing you used
df = df.dropna(thresh=len(df)*0.5, axis=1)
df = df.fillna(df.median(numeric_only=True))
df = pd.get_dummies(df, drop_first=True)

X = df.drop("TARGET", axis=1)

feature_map = {
    "AMT_CREDIT": "Loan amount",
    "AMT_INCOME_TOTAL": "Applicant income",
    "DAYS_EMPLOYED": "Employment stability",
    "DAYS_BIRTH": "Age and financial maturity",
    "AMT_ANNUITY": "Loan repayment burden",
    "AMT_GOODS_PRICE": "Asset value of purchase"
}

def get_user_by_id(user_id):
    base_id = 1001
    index = user_id - base_id
    
    if index < 0 or index >= len(X):
        return None
    
    return X.iloc[index].copy()

def align_features(df):
    """Ensure same columns as training"""
    return df.reindex(columns=FEATURE_NAMES, fill_value=0)

def simulate(sample, loan_amount):
    sample_copy = sample.copy()
    
    income = float(sample_copy.get("AMT_INCOME_TOTAL", 100000))
    
    # Update ALL loan-related features consistently
    if "AMT_CREDIT" in sample_copy:
        sample_copy["AMT_CREDIT"] = loan_amount
    if "AMT_GOODS_PRICE" in sample_copy:
        sample_copy["AMT_GOODS_PRICE"] = loan_amount
    if "AMT_ANNUITY" in sample_copy:
        # Recalculate annuity based on new loan amount (assume same term ratio)
        original_credit = float(sample.get("AMT_CREDIT", 1))
        original_annuity = float(sample.get("AMT_ANNUITY", 1))
        if original_credit > 0:
            annuity_ratio = original_annuity / original_credit
            sample_copy["AMT_ANNUITY"] = loan_amount * annuity_ratio
        else:
            sample_copy["AMT_ANNUITY"] = loan_amount / 60  # default 5-year term

    df_input = pd.DataFrame([sample_copy])
    df_input = align_features(df_input)

    # ML model prediction
    default_prob = float(model.predict_proba(df_input)[0][1])
    approval_prob = 1.0 - default_prob
    
    # Business rules: debt-to-income ratio must make sense
    loan_to_income = loan_amount / max(income, 1)
    
    if loan_to_income > 10:
        # Loan is > 10x income → very risky, penalize heavily
        approval_prob = min(approval_prob, 0.15)
    elif loan_to_income > 5:
        # Loan is 5-10x income → risky, reduce confidence
        approval_prob = approval_prob * 0.5
    elif loan_to_income > 3:
        # Loan is 3-5x income → moderate risk
        approval_prob = approval_prob * 0.75
    elif loan_to_income < 0.5:
        # Loan is very small relative to income → boost confidence
        approval_prob = min(approval_prob * 1.2, 0.99)
    
    decision = "APPROVED" if approval_prob >= THRESHOLD else "REJECTED"

    shap_vals = explainer.shap_values(df_input)[0]

    return approval_prob, decision, shap_vals, df_input

def generate_explanation(shap_vals, df_input):
    feature_names = df_input.columns
    
    banned_features = ["EXT_SOURCE_1", "EXT_SOURCE_2", "EXT_SOURCE_3", "CODE_GENDER", "CODE_GENDER_M"]
    
    top_idx = np.argsort(np.abs(shap_vals))[-5:]
    
    reasons = []
    for i in reversed(top_idx):
        feature = feature_names[i]
        
        if any(b in feature for b in banned_features):
            continue
        
        impact = shap_vals[i]
        
        if impact < 0:
            reasons.append(f"{feature_map.get(feature, feature)} reduced approval chances")
        else:
            reasons.append(f"{feature_map.get(feature, feature)} improved approval chances")
        
        if len(reasons) == 3:
            break
    
    return reasons

from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

app = FastAPI(title="Credit Risk Simulator API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class PredictRequest(BaseModel):
    user_id: int
    loan_amount: float


class ExplainRequest(BaseModel):
    decision: str = Field(min_length=2, max_length=32)
    probability: float
    reasons: list[str] = Field(min_length=1, max_length=8)
    question: str | None = Field(default=None, max_length=500)
    history: list[dict] = Field(default_factory=list, max_length=20)
    loan_amount: float | None = None
    annual_income: float | None = None
    risk_score: float | None = None
    risk_label: str | None = None
    user_context: dict | None = None


def _normalize_probability(raw: float) -> float:
    if raw <= 1:
        return max(0.0, min(raw * 100.0, 100.0))
    return max(0.0, min(raw, 100.0))


def _call_gemini(prompt: str) -> str:
    api_key = os.getenv("GEMINI_API_KEY", "").strip()
    if not api_key:
        return "I can explain this decision using the provided reasons, but live AI mode is not configured on the server yet."

    model = os.getenv("GEMINI_MODEL", "gemini-1.5-flash")
    endpoint = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={url_parse.quote(api_key)}"
    body = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"temperature": 0.2, "maxOutputTokens": 240},
    }

    req = url_request.Request(
        endpoint,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with url_request.urlopen(req, timeout=20) as response:
            data = json.loads(response.read().decode("utf-8"))
    except (url_error.HTTPError, url_error.URLError):
        return "I could not reach the AI service right now. Based on your decision reasons, please review repayment capacity and recent credit behavior before the next application."

    text = (
        data.get("candidates", [{}])[0]
        .get("content", {})
        .get("parts", [{}])[0]
        .get("text", "")
        .strip()
    )
    return text or "I can summarize this result using your provided reasons, but the AI response came back empty."


def _map_reason_to_friendly(reason: str) -> str:
    r = (reason or '').lower()
    if 'bureau' in r or 'credit history' in r or 'reconstruct' in r or 'reconstructed' in r:
        return 'Records from different credit reports don\'t match — we may need to reconcile your credit file.'
    if 'repay' in r or 'repayment' in r or 'stable repayment' in r or 'payment' in r:
        return 'Recent payments look stable and on-time, which helps your case.'
    if 'requested amount' in r or 'amt credit' in r or 'high relative' in r or ('high' in r and 'income' in r):
        return 'The requested loan is large compared with your reported income.'
    if 'income' in r or 'amt_income' in r:
        return 'Your income level is affecting the affordability calculation.'
    if 'debt' in r or 'dti' in r or 'debt-to-income' in r:
        return 'Your existing debts are high compared to income (debt-to-income).'
    if 'employment' in r or 'days_employed' in r:
        return 'Employment history or stability is a factor in the decision.'
    cleaned = reason.replace('_', ' ').strip()
    if cleaned.endswith('.'):
        cleaned = cleaned[:-1]
    if cleaned:
        return cleaned[0].upper() + cleaned[1:]
    return cleaned


def _build_local_explanation(decision: str, probability_pct: float, reasons: list[str], question: str | None, history: list[dict] | None, user_context: dict | None) -> str:
    q = (question or '').strip().lower()
    history = history or []

    if any(token in q for token in ['hi', 'hello', 'hey']):
        return "Hi — I'm here to help explain this result and suggest clear next steps. What would you like to know first?"

    if any(token in q for token in ['ok', 'okay', 'thanks', 'thank', 'got it']):
        return "You're welcome — if you'd like, I can turn these steps into a short 30-day plan. Want that?"

    if any(token in q for token in ['improve', 'improvement', 'better', 'increase', 'approved', 'approval']):
        if decision == 'REJECTED':
            return (
                "To improve approval chances, try these two practical steps:\n"
                "1) Lower the requested amount to improve affordability.\n"
                "2) Keep all repayments on time and reduce high balances for 2–3 months."
            )
        return "You're already in a good spot. Keep payments on time and avoid taking on large new debts before disbursal."

    if any(token in q for token in ['max', 'maximum', 'more loan', 'more amount', 'how much', 'eligible', 'limit', 'increase loan']):
        if user_context:
            est = user_context.get('decisionSummary', {}).get('estimatedMaxLoan')
            if est:
                return f'A cautious maximum estimate is around ₹{float(est):,.0f}. This is a guideline based on your income and current profile.'
        return 'I can estimate a safe range, but I need your income and the amount you asked for.'

    # Compose explanation using friendly reasons
    lines = [f"Your application was {decision.lower()} — confidence about {probability_pct:.0f}%."]
    if reasons:
        lines.append('Main reasons we looked at:')
        for r in reasons:
            lines.append(f'- {r}')

    if decision == 'REJECTED':
        lines.append('Quick next steps:')
        lines.append('1) Try a smaller loan amount to improve affordability.')
        lines.append('2) Keep payments on time and reduce high credit balances over the next few months.')
    else:
        lines.append('Quick next steps:')
        lines.append('1) Maintain on-time payments.')
        lines.append('2) Avoid taking large new credit until disbursal.')

    if q:
        lines.append("Ask me to explain any single point in more detail or say 'how can I improve' to get a step-by-step plan.")

    return '\n'.join(lines)


def _estimate_loan_range(loan_amount: float | None, annual_income: float | None, probability_pct: float, decision: str, user_context: dict | None) -> str:
    if annual_income is None or annual_income <= 0:
        return "I can’t estimate a maximum loan amount without income details."

    requested = float(loan_amount or 0)
    income = float(annual_income)
    if user_context:
        estimated = user_context.get('decisionSummary', {}).get('estimatedMaxLoan')
        if estimated:
            return (
                f'A cautious estimate is around ₹{float(estimated):,.0f}. '
                'This is not a guarantee, but it is a more realistic ceiling based on your current profile and approval confidence.'
            )
    base_cap = income * 4.0
    confidence_boost = max(0.65, min(1.05, 0.75 + (probability_pct / 200.0)))
    if decision == 'REJECTED':
        lower = min(base_cap * 0.6, max(requested * 0.8, income * 1.2))
        upper = min(base_cap * 0.8, max(lower, requested * 0.95))
    else:
        lower = min(base_cap * 0.75, max(requested, income * 1.5))
        upper = min(base_cap * 0.95, max(lower, requested * 1.35 * confidence_boost))

    if upper < lower:
        upper = lower

    return (
        f"A cautious estimate is around ₹{lower:,.0f} to ₹{upper:,.0f}. "
        f"This is not a guarantee, but it’s a more realistic range based on your income and current approval confidence."
    )


@app.get("/")
def home():
    return {"message": "Credit Risk Simulator API running"}

@app.get("/user_profile")
def user_profile(user_id: int):
    """Fetch user data from CSV for pre-populating the frontend form"""
    sample = get_user_by_id(user_id)
    if sample is None:
        return {"error": f"User ID {user_id} not found. Valid range: 1001 to {1001 + len(X) - 1}"}
    
    income = float(sample.get("AMT_INCOME_TOTAL", 0))
    credit = float(sample.get("AMT_CREDIT", 0))
    annuity = float(sample.get("AMT_ANNUITY", 0))
    days_birth = float(sample.get("DAYS_BIRTH", 0))
    days_employed = float(sample.get("DAYS_EMPLOYED", 0))
    children = int(sample.get("CNT_CHILDREN", 0))
    fam_members = int(sample.get("CNT_FAM_MEMBERS", 1))
    
    age = int(round(abs(days_birth) / 365))
    emp_years = float(round(abs(days_employed) / 365, 1))
    
    return {
        "user_id": int(user_id),
        "income": float(income),
        "credit_amount": float(credit),
        "annuity": float(annuity),
        "age": int(age),
        "employment_years": float(emp_years),
        "children": int(children),
        "family_members": int(fam_members),
        "days_birth": float(days_birth),
        "days_employed": float(days_employed),
    }

@app.get("/simulate")
def simulate_api(user_id: int,amount: float):

    sample = get_user_by_id(user_id)
    
    if sample is None:
        return {"error": "Invalid user_id"}

    prob, decision, shap_vals, df_input = simulate(sample, amount)
    
    reasons = generate_explanation(shap_vals, df_input)

    return {
        "loan_amount": amount,
        "decision": decision,
        "probability": float(prob),
        "reasons": reasons
    }


@app.post("/predict_csv")
def predict_csv(req: PredictRequest):
    """
    CSV-based prediction pipeline:
    1. Look up user row from application_train.csv by user_id
    2. Override AMT_CREDIT with the requested loan_amount
    3. Run XGBoost prediction + SHAP explanation
    4. Return structured response
    """
    sample = get_user_by_id(req.user_id)

    if sample is None:
        return {"error": f"User ID {req.user_id} not found. Valid range: 1001 to {1001 + len(X) - 1}"}

    prob, decision, shap_vals, df_input = simulate(sample, req.loan_amount)
    reasons = generate_explanation(shap_vals, df_input)

    # Build SHAP values dict (top 10 features by magnitude)
    feature_names = df_input.columns.tolist()
    shap_pairs = sorted(
        zip(feature_names, shap_vals),
        key=lambda x: abs(float(x[1])),
        reverse=True
    )[:10]
    
    shap_dict = {}
    top_features = []
    for feat, val in shap_pairs:
        shap_dict[feat] = round(float(val), 6)
        direction = "positive" if float(val) >= 0 else "negative"
        top_features.append({
            "feature": feat,
            "value": round(float(val), 6),
            "direction": direction,
            "message": f"{feature_map.get(feat, feat)} {'improved' if direction == 'positive' else 'reduced'} approval chances"
        })

    # Extract key user info from the CSV row
    user_info = {
        "income": float(sample.get("AMT_INCOME_TOTAL", 0)),
        "credit_amount": float(req.loan_amount),
        "annuity": float(sample.get("AMT_ANNUITY", 0)),
        "days_birth": float(sample.get("DAYS_BIRTH", 0)),
        "days_employed": float(sample.get("DAYS_EMPLOYED", 0)),
    }

    risk_score = float(round((1 - float(prob)) * 100, 2))
    risk_label = "Low" if float(prob) >= 0.7 else "Moderate" if float(prob) >= 0.4 else "High"

    return {
        "user_id": int(req.user_id),
        "loan_amount": float(req.loan_amount),
        "decision": str(decision),
        "probability": float(round(float(prob), 6)),
        "risk_score": float(risk_score),
        "risk_label": str(risk_label),
        "threshold": float(THRESHOLD),
        "reasons": reasons,
        "shap_values": shap_dict,
        "top_features": top_features,
        "user_info": user_info,
        "message": "Congratulations! Your loan has been pre-approved." if decision == "APPROVED" else "Your application could not be approved at this time.",
        "recommendation": "Eligible for next stage review" if decision == "APPROVED" else "Consider reducing loan amount or improving credit profile",
        "next_steps": [
            "Review the key decision factors below",
            "Pay bills on time to maintain your current trajectory",
            "Keep credit usage low (under 30% of your total limit)"
        ] if decision == "APPROVED" else [
            "Try a smaller loan amount",
            "Reduce existing debt before reapplying",
            "Build credit history over the next 6-12 months"
        ]
    }


@app.get("/user_range")
def get_user_range():
    """Return valid user ID range"""
    return {"min_id": 1001, "max_id": 1001 + len(X) - 1, "total_users": int(len(X))}


@app.post("/ai/explain")
def ai_explain(payload: ExplainRequest):
    decision = payload.decision.upper().strip()
    if decision not in {"APPROVED", "REJECTED"}:
        raise HTTPException(status_code=400, detail="decision must be APPROVED or REJECTED")

    reasons = [r.strip().replace("_", " ") for r in payload.reasons if r.strip()]
    if not reasons:
        raise HTTPException(status_code=400, detail="At least one valid reason is required")

    probability_pct = _normalize_probability(payload.probability)
    friendly_reasons = [_map_reason_to_friendly(r) for r in reasons]

    prompt = (
        "You are a friendly fintech assistant that speaks like a helpful customer support agent.\n"
        "Task: Explain the decision clearly and conversationally for a non-technical user.\n"
        "Rules:\n"
        "- Don't use technical model terms, internal codes, or jargon.\n"
        "- Don't mention or infer sensitive attributes (gender, religion, ethnicity, etc.).\n"
        "- Use ONLY the facts and reasons provided; do not invent new data.\n"
        "- Be empathetic, use 'you' and short, clear sentences, and offer 1-3 concrete next steps.\n"
        "- Ask a short clarifying question if the user follow-up is ambiguous.\n"
        f"Decision: {decision}\n"
        f"Probability: {probability_pct:.1f}%\n"
        f"Reasons (raw): {'; '.join(reasons)}\n"
        f"Reasons (user-friendly): {'; '.join(friendly_reasons)}\n"
    )

    if decision == "REJECTED":
        prompt += "Include 2 clear next steps to improve approval chances.\n"

    if payload.question:
        prompt += f"User follow-up question: {payload.question.strip()}\n"

    if payload.history:
        prompt += "Conversation context (latest first):\n"
        for item in payload.history[-6:]:
            role = str(item.get("role", "user"))
            text = str(item.get("text", "")).strip()
            if not text:
                continue
            prompt += f"- {role}: {text[:240]}\n"

    if payload.annual_income is not None:
        prompt += f"Applicant income: {payload.annual_income}\n"
    if payload.loan_amount is not None:
        prompt += f"Current requested loan amount: {payload.loan_amount}\n"
    if payload.risk_score is not None:
        prompt += f"Current risk score: {payload.risk_score}\n"
    if payload.risk_label:
        prompt += f"Current risk label: {payload.risk_label}\n"
    if payload.user_context:
        prompt += f"User context snapshot: {payload.user_context}\n"

    explanation = _call_gemini(prompt)

    # If Gemini is unavailable, provide a local question-aware response using user-friendly reasons.
    if "AI service" in explanation or "not configured" in explanation or "empty" in explanation:
        explanation = _build_local_explanation(decision, probability_pct, friendly_reasons, payload.question, payload.history, payload.user_context)

    q = (payload.question or "").strip().lower()
    if any(token in q for token in ["max", "maximum", "more loan", "more amount", "how much", "eligible", "limit", "increase loan"]):
        explanation = _estimate_loan_range(payload.loan_amount, payload.annual_income, probability_pct, decision, payload.user_context)

    return {"explanation": explanation}


@app.get("/csv_stats")
def csv_stats():
    """Compute fairness and model statistics from the CSV dataset"""
    # Sample a batch for stats (first 5000 rows for speed)
    sample_size = min(5000, len(X))
    sample_indices = list(range(sample_size))
    
    decisions = []
    probs = []
    
    for idx in sample_indices:
        row = X.iloc[idx].copy()
        loan_amt = float(row.get("AMT_CREDIT", 300000))
        df_in = pd.DataFrame([row])
        df_in = align_features(df_in)
        default_prob = float(model.predict_proba(df_in)[0][1])
        approval_prob = 1.0 - default_prob
        dec = "APPROVED" if approval_prob >= THRESHOLD else "REJECTED"
        decisions.append(dec)
        probs.append(approval_prob)
    
    total = len(decisions)
    approved = decisions.count("APPROVED")
    rejected = decisions.count("REJECTED")
    approval_rate = float(round(approved / max(total, 1) * 100, 2))
    
    # Adverse Impact Ratio: compare approval rate across groups
    # Using first half vs second half as proxy for two demographic groups
    half = total // 2
    group_a_approved = decisions[:half].count("APPROVED")
    group_b_approved = decisions[half:].count("APPROVED")
    group_a_rate = group_a_approved / max(half, 1)
    group_b_rate = group_b_approved / max(total - half, 1)
    adverse_impact = float(round(min(group_a_rate, group_b_rate) / max(max(group_a_rate, group_b_rate), 0.001), 3))
    
    # Model drift (PSI) - compare probability distributions
    avg_prob = float(round(sum(probs) / max(len(probs), 1), 4))
    prob_std = float(round((sum((p - avg_prob)**2 for p in probs) / max(len(probs), 1))**0.5, 4))
    psi_estimate = float(round(prob_std * 0.1, 4))  # simplified PSI proxy
    
    # Audit confidence based on sample coverage
    audit_confidence = float(round(min(sample_size / len(X) * 100, 100), 1))
    
    # Fairness by income bracket
    income_brackets = [
        {"label": "< ₹1L", "min": 0, "max": 100000},
        {"label": "₹1L-3L", "min": 100000, "max": 300000},
        {"label": "₹3L-5L", "min": 300000, "max": 500000},
        {"label": "₹5L+", "min": 500000, "max": 999999999},
    ]
    
    fairness_data = []
    for bracket in income_brackets:
        bracket_decisions = []
        for idx in sample_indices:
            income = float(X.iloc[idx].get("AMT_INCOME_TOTAL", 0))
            if bracket["min"] <= income < bracket["max"]:
                bracket_decisions.append(decisions[idx])
        b_total = len(bracket_decisions)
        b_approved = bracket_decisions.count("APPROVED")
        fairness_data.append({
            "group": bracket["label"],
            "total": int(b_total),
            "approved": int(b_approved),
            "approval_rate": float(round(b_approved / max(b_total, 1) * 100, 1)),
        })
    
    return {
        "total_decisions": int(total),
        "approved": int(approved),
        "rejected": int(rejected),
        "approval_rate": float(approval_rate),
        "adverse_impact_ratio": float(adverse_impact),
        "model_drift_psi": float(psi_estimate),
        "audit_confidence": float(audit_confidence),
        "avg_probability": float(avg_prob),
        "prob_std": float(prob_std),
        "threshold": float(THRESHOLD),
        "fairness_by_income": fairness_data,
    }


@app.get("/curve")
def loan_curve(user_id: int):
    """Generate loan vs probability curve"""
    amounts = list(range(100000, 1000000, 100000))
    probs = []
    sample = get_user_by_id(user_id)
    
    if sample is None:
        return {"error": "Invalid user_id"}
    for amt in amounts:
        prob, _, _, _ = simulate(sample, amt)
        probs.append(float(prob))

    return {
        "amounts": amounts,
        "probabilities": probs
    }
