from __future__ import annotations

import uuid
from datetime import datetime
from typing import Dict, List

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

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
