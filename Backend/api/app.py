from __future__ import annotations

import os
import uuid
from datetime import datetime
from typing import List

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from model_utils import load_artifacts, predict_loan

app = FastAPI(title='ClearScore ML Service', version='1.0.0')

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class PredictRequest(BaseModel):
    user_id: str
    income: float
    credit_amount: float
    loan_term: int = 60
    existing_debt: float = 0
    credit_score: int = 650
    employment_years: float = 0
    age: int = 35
    purpose: str = 'Personal'
    region: str = 'URBAN'
    gender: str = 'OTHER'
    education_level: str = 'Higher education'
    housing_status: str = 'Rented'
    owns_car: bool = False
    owns_realty: bool = False
    family_members: int = 1
    dependents: int = 0
    hard_inquiries_last_year: int = 0
    region_rating: int = 2


class ExplainRequest(BaseModel):
    decision: str = Field(min_length=2, max_length=32)
    probability: float
    reasons: List[str] = Field(min_length=1, max_length=8)
    question: str | None = Field(default=None, max_length=500)
    history: List[dict] = Field(default_factory=list, max_length=20)
    loan_amount: float | None = None
    annual_income: float | None = None
    risk_score: float | None = None
    risk_label: str | None = None
    user_context: dict | None = None


class ExplainResponse(BaseModel):
    explanation: str


def _normalize_probability(raw: float) -> float:
    # Accept either 0..1 or 0..100 and normalize to percentage.
    if raw <= 1:
        return max(0.0, min(raw * 100.0, 100.0))
    return max(0.0, min(raw, 100.0))


def _sanitize_reason(reason: str) -> str:
    text = reason.strip().replace('_', ' ')
    # Avoid leaking implementation-level terms in explanations.
    replacements = {
        'shap': 'model explanation signal',
        'ext source': 'credit history indicator',
        'dti': 'debt-to-income signal',
        'amt credit': 'loan amount signal',
        'days id publish': 'credit timeline signal',
    }
    lowered = text.lower()
    for raw, safe in replacements.items():
        if raw in lowered:
            lowered = lowered.replace(raw, safe)
    return lowered[:180]


def _map_reason_to_friendly(reason: str) -> str:
    """Turn a technical/shorthand reason into a short, user-friendly phrase."""
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
    # default: tidy text and make it a readable sentence
    cleaned = reason.replace('_', ' ').strip()
    if cleaned.endswith('.'):
        cleaned = cleaned[:-1]
    return cleaned[0].upper() + cleaned[1:]


async def _call_gemini(prompt: str) -> str:
    api_key = os.getenv('GEMINI_API_KEY', '').strip()
    if not api_key:
        return 'I can explain this decision using provided reasons, but live AI mode is not configured on the server yet.'

    model = os.getenv('GEMINI_MODEL', 'gemini-1.5-flash')
    endpoint = f'https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent'
    payload = {
        'contents': [{'parts': [{'text': prompt}]}],
        'generationConfig': {
            'temperature': 0.2,
            'maxOutputTokens': 240,
        },
    }

    try:
        async with httpx.AsyncClient(timeout=20.0) as client:
            response = await client.post(endpoint, params={'key': api_key}, json=payload)
    except (httpx.TimeoutException, httpx.RequestError):
        return 'I could not reach the AI service right now. Based on your decision reasons, review repayment capacity and recent credit behavior before the next application.'

    if response.status_code >= 400:
        return 'I could not complete a live AI explanation right now. Please use the listed reasons to review your next steps.'

    data = response.json()
    text = (
        data.get('candidates', [{}])[0]
        .get('content', {})
        .get('parts', [{}])[0]
        .get('text', '')
        .strip()
    )
    return text or 'I can summarize this result using your provided reasons, but the AI response came back empty.'


def _build_local_explanation(decision: str, probability_pct: float, reasons: list[str], question: str | None, history: list[dict] | None, user_context: dict | None) -> str:
    q = (question or '').strip().lower()
    history = history or []

    # Simple conversational shortcuts
    if any(token in q for token in ['hi', 'hello', 'hey']):
        return "Hi — I'm here to help explain this result and suggest clear next steps. What would you like to know first?"

    if any(token in q for token in ['ok', 'okay', 'thanks', 'thank', 'got it']):
        return "You're welcome — if you'd like, I can turn these steps into a short 30-day plan. Want that?"

    # Direct improvement question
    if any(token in q for token in ['improve', 'improvement', 'better', 'increase', 'approved', 'approval']):
        if decision == 'REJECTED':
            return (
                "I know a few practical ways to improve your chances. Two quick wins:"
                "\n1) Ask for a smaller loan amount — this directly improves affordability."
                "\n2) Keep all payments on time for the next 2–3 months and reduce any high credit balances."
            )
        return "You're already in a good spot. Keep payments on time and avoid taking on large new debts before disbursal."

    # Max loan / eligibility question
    if any(token in q for token in ['max', 'maximum', 'more loan', 'more amount', 'how much', 'eligible', 'limit', 'increase loan']):
        if user_context:
            est = user_context.get('decisionSummary', {}).get('estimatedMaxLoan')
            if est:
                return f'A cautious maximum estimate is around ₹{float(est):,.0f}. This is a guideline based on your income and current profile.'
        return 'I can estimate a safe range, but I need your income and the amount you asked for.'

    # Build a friendly, multi-part explanation using the provided user-facing reasons.
    lines = []
    lines.append(f"Your application was {decision.lower()} — confidence is about {probability_pct:.0f}%.")

    if reasons:
        lines.append("Here are the main points I used to explain the outcome:")
        for r in reasons:
            # each `r` should already be user-friendly
            lines.append(f"- {r}")

    # Tailored next steps
    if decision == 'REJECTED':
        lines.append("Short next steps to improve your chances:")
        lines.append("1. Consider requesting a smaller loan amount to improve affordability.")
        lines.append("2. Keep repayments on time and reduce large credit balances over the next 2–3 months.")
    else:
        lines.append("Short next steps to keep this on track:")
        lines.append("1. Keep up on-time payments.")
        lines.append("2. Avoid taking on large new credit until disbursal.")

    # If user asked a question, append a direct answer prompt
    if q:
        lines.append("If you'd like, ask me to explain any single point in more detail or say 'how can I improve' to get a step-by-step plan.")

    return "\n".join(lines)


def _estimate_loan_range(loan_amount: float | None, annual_income: float | None, probability_pct: float, decision: str, user_context: dict | None) -> str:
    if annual_income is None or annual_income <= 0:
        return 'I can’t estimate a maximum loan amount without income details.'

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
        f'A cautious estimate is around ₹{lower:,.0f} to ₹{upper:,.0f}. '
        f'This is not a guarantee, but it’s a more realistic range based on your income and current approval confidence.'
    )


@app.get('/health')
def health():
    model, explainer, preproc = load_artifacts()
    return {
        'status': 'ok',
        'model_loaded': model is not None,
        'explainer_loaded': explainer is not None,
        'feature_count': len(preproc.get('feature_names', [])),
    }


@app.post('/predict')
def predict(payload: PredictRequest):
    try:
        bundle = predict_loan(payload.model_dump())
        prediction_id = str(uuid.uuid4())
        return {
            'prediction_id': prediction_id,
            'user_id': payload.user_id,
            'decision': bundle.decision,
            'risk_label': bundle.risk_label,
            'probability': bundle.probability,
            'risk_score': bundle.risk_score,
            'threshold': bundle.threshold,
            'affordability_ratio': float(payload.income / max(payload.credit_amount, 1)),
            'debt_to_income_ratio': float(payload.existing_debt / max(payload.income, 1)),
            'approval_reasoning': bundle.approval_reasoning,
            'recommendation': bundle.recommendation,
            'next_steps': bundle.next_steps,
            'chart_data': bundle.chart_data,
            'top_features': bundle.top_features,
            'timestamp': datetime.utcnow().isoformat(),
        }
    except Exception as ex:
        raise HTTPException(status_code=500, detail=f'Prediction failed: {ex}')


@app.post('/ai/explain', response_model=ExplainResponse)
async def explain_decision(payload: ExplainRequest):
    decision = payload.decision.upper().strip()
    if decision not in {'APPROVED', 'REJECTED'}:
        raise HTTPException(status_code=400, detail='decision must be APPROVED or REJECTED')

    probability_pct = _normalize_probability(payload.probability)
    safe_reasons = [_sanitize_reason(reason) for reason in payload.reasons if reason.strip()]
    if not safe_reasons:
        raise HTTPException(status_code=400, detail='At least one valid reason is required')

    friendly_reasons = [_map_reason_to_friendly(r) for r in safe_reasons]

    prompt = (
        'You are a friendly fintech assistant that speaks like a helpful customer support agent.\n'
        'Task: Explain the decision clearly and conversationally for a non-technical user.\n'
        'Rules:\n'
        "- Don't use technical model terms, internal codes, or jargon.\n"
        "- Don't mention or infer sensitive attributes (gender, religion, ethnicity, etc.).\n"
        "- Use ONLY the facts and reasons provided; do not invent new data.\n"
        "- Be empathetic, use 'you' and short, clear sentences, and offer 1-3 concrete next steps.\n"
        "- Ask a short clarifying question if the user follow-up is ambiguous.\n"
        f'Decision: {decision}\n'
        f'Probability: {probability_pct:.1f}%\n'
        f'Reasons (raw): {"; ".join(safe_reasons)}\n'
        f'Reasons (user-friendly): {"; ".join(friendly_reasons)}\n'
    )

    if decision == 'REJECTED':
        prompt += 'Include 2 actionable next steps to improve approval chances.\n'

    if payload.question:
        prompt += f'User follow-up question: {payload.question.strip()}\n'
        prompt += 'Answer the follow-up while still following all rules above; be conversational and ask a clarifying question if needed.\n'

    if payload.history:
        prompt += 'Conversation context (latest first):\n'
        for item in payload.history[-6:]:
            role = str(item.get('role', 'user'))
            text = str(item.get('text', '')).strip()
            if not text:
                continue
            prompt += f'- {role}: {text[:240]}\n'

    if payload.annual_income is not None:
        prompt += f'Applicant income: {payload.annual_income}\n'
    if payload.loan_amount is not None:
        prompt += f'Current requested loan amount: {payload.loan_amount}\n'
    if payload.risk_score is not None:
        prompt += f'Current risk score: {payload.risk_score}\n'
    if payload.risk_label:
        prompt += f'Current risk label: {payload.risk_label}\n'
    if payload.user_context:
        prompt += f'User context snapshot: {payload.user_context}\n'

    explanation = await _call_gemini(prompt)
    if 'AI service' in explanation or 'not configured' in explanation or 'empty' in explanation:
        # pass friendly reasons into local fallback so it uses user-facing wording
        explanation = _build_local_explanation(decision, probability_pct, friendly_reasons, payload.question, payload.history, payload.user_context)

    q = (payload.question or '').strip().lower()
    if any(token in q for token in ['max', 'maximum', 'more loan', 'more amount', 'how much', 'eligible', 'limit', 'increase loan']):
        explanation = _estimate_loan_range(payload.loan_amount, payload.annual_income, probability_pct, decision, payload.user_context)
    return {'explanation': explanation}
