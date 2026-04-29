from __future__ import annotations

from datetime import datetime
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field, conint, confloat


class LoginPayload(BaseModel):
    username: str = Field(min_length=3, max_length=50)
    password: str = Field(min_length=4, max_length=100)


class LoanInput(BaseModel):
    user_id: str = Field(min_length=1, max_length=64)
    loan_amount: confloat(gt=0)  # type: ignore[valid-type]
    annual_income: confloat(gt=0)  # type: ignore[valid-type]
    credit_score: conint(ge=300, le=850) = 650
    loan_term_months: conint(ge=3, le=360) = 60
    monthly_debt: confloat(ge=0) = 0
    employment_years: confloat(ge=0) = 0
    age_years: conint(ge=18, le=100) = 35
    family_members: conint(ge=1, le=12) = 1
    dependents: conint(ge=0, le=10) = 0
    monthly_expenses: confloat(ge=0) = 0
    purpose: str = Field(default='Personal')
    employment_status: str = Field(default='Working')
    education_level: str = Field(default='Higher education')
    housing_status: str = Field(default='Rented')
    owns_car: bool = False
    owns_realty: bool = False
    hard_inquiries_last_year: conint(ge=0, le=20) = 0
    region_rating: conint(ge=1, le=3) = 2


class LoginResponse(BaseModel):
    access_token: str
    token_type: str = 'bearer'
    user_id: str


class ReasonItem(BaseModel):
    feature: str
    impact: float
    direction: str
    message: str


class PredictionResponse(BaseModel):
    prediction_id: str
    user_id: str
    decision: str
    risk_label: str
    probability: float
    risk_score: float
    threshold: float
    affordability_ratio: float
    debt_to_income_ratio: float
    approval_reasoning: List[ReasonItem]
    recommendation: str
    next_steps: List[str]
    chart_data: Dict[str, Any]
    top_features: List[Dict[str, Any]]
    timestamp: datetime


class HistoryItem(BaseModel):
    prediction_id: str
    timestamp: datetime
    decision: str
    probability: float
    risk_score: float
    loan_amount: float
    annual_income: float
    recommendation: str


class HistoryResponse(BaseModel):
    user_id: str
    items: List[HistoryItem]
