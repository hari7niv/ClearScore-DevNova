from __future__ import annotations

import json
import os
import pickle
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Dict, List, Tuple

import numpy as np

BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SAVED_DIR = os.path.join(BASE_DIR, 'saved_models')
MODEL_FILE = os.path.join(SAVED_DIR, 'credit_model_latest.pkl')
EXPLAINER_FILE = os.path.join(SAVED_DIR, 'shap_explainer_latest.pkl')
PREPROC_FILE = os.path.join(SAVED_DIR, 'preprocessing_info_latest.pkl')

_model = None
_explainer = None
_preproc = None


@dataclass
class PredictionBundle:
    probability: float
    decision: str
    risk_score: float
    risk_label: str
    threshold: float
    recommendation: str
    next_steps: List[str]
    chart_data: Dict[str, Any]
    top_features: List[Dict[str, Any]]
    approval_reasoning: List[Dict[str, Any]]



def load_artifacts() -> Tuple[Any, Any, Dict[str, Any]]:
    global _model, _explainer, _preproc
    if _model is None:
        with open(MODEL_FILE, 'rb') as f:
            _model = pickle.load(f)
    if _explainer is None:
        try:
            with open(EXPLAINER_FILE, 'rb') as f:
                _explainer = pickle.load(f)
        except Exception:
            _explainer = None
    if _preproc is None:
        try:
            with open(PREPROC_FILE, 'rb') as f:
                _preproc = pickle.load(f)
        except Exception:
            _preproc = {}
    return _model, _explainer, _preproc



def _base_feature_row(feature_names: List[str]) -> Dict[str, float]:
    return {name: 0.0 for name in feature_names}



def build_feature_row(payload: Dict[str, Any], feature_names: List[str]) -> np.ndarray:
    row = _base_feature_row(feature_names)

    user_id = str(payload.get('user_id', '0'))
    loan_amount = float(payload.get('credit_amount', payload.get('loan_amount', 0)))
    annual_income = float(payload.get('income', payload.get('annual_income', 1)))
    credit_score = float(payload.get('credit_score', 650))
    loan_term = float(payload.get('loan_term', payload.get('loan_term_months', 60)))
    existing_debt = float(payload.get('existing_debt', payload.get('monthly_debt', 0)))
    employment_years = float(payload.get('employment_years', 0))
    age = float(payload.get('age', payload.get('age_years', 35)))
    purpose = str(payload.get('purpose', 'Personal'))
    region = str(payload.get('region', 'URBAN'))
    gender = str(payload.get('gender', 'OTHER')).upper()
    education = str(payload.get('education_level', 'Higher education'))
    housing = str(payload.get('housing_status', 'Rented'))
    owns_car = bool(payload.get('owns_car', False))
    owns_realty = bool(payload.get('owns_realty', False))
    family_members = int(payload.get('family_members', 1))
    children = int(payload.get('dependents', payload.get('children', 0)))
    inquiries = int(payload.get('hard_inquiries_last_year', 0))

    row['SK_ID_CURR'] = float(abs(hash(user_id)) % 1000000)
    row['CNT_CHILDREN'] = float(children)
    row['AMT_INCOME_TOTAL'] = annual_income
    row['AMT_CREDIT'] = loan_amount
    row['AMT_ANNUITY'] = loan_amount / max(loan_term, 1.0)
    row['AMT_GOODS_PRICE'] = loan_amount
    row['DAYS_BIRTH'] = -age * 365.0
    row['DAYS_EMPLOYED'] = -employment_years * 365.0
    row['CNT_FAM_MEMBERS'] = float(family_members)
    row['AMT_REQ_CREDIT_BUREAU_YEAR'] = float(inquiries)
    row['FLAG_OWN_CAR_Y'] = 1.0 if owns_car else 0.0
    row['FLAG_OWN_REALTY_Y'] = 1.0 if owns_realty else 0.0
    row['CODE_GENDER_M'] = 1.0 if gender == 'MALE' else 0.0
    row['CODE_GENDER_F'] = 1.0 if gender == 'FEMALE' else 0.0
    row['CODE_GENDER_XNA'] = 1.0 if gender not in {'MALE', 'FEMALE'} else 0.0
    row['NAME_EDUCATION_TYPE_Higher education'] = 1.0 if 'higher' in education.lower() else 0.0
    row['NAME_HOUSING_TYPE_Rented apartment'] = 1.0 if 'rent' in housing.lower() else 0.0
    row['REGION_RATING_CLIENT'] = float(4 - min(max(int(payload.get('region_rating', 2)), 1), 3))
    row['REGION_RATING_CLIENT_W_CITY'] = float(4 - min(max(int(payload.get('region_rating', 2)), 1), 3))

    # Purpose features used by the original encoded model, if present.
    purpose_feature = f'NAME_CONTRACT_TYPE_{purpose}'
    if purpose_feature in row:
        row[purpose_feature] = 1.0

    for key in list(row.keys()):
        if key.startswith('NAME_TYPE_SUITE_') and payload.get('purpose'):
            pass

    return np.array([[row.get(name, 0.0) for name in feature_names]], dtype=float)



def predict_loan(payload: Dict[str, Any]) -> PredictionBundle:
    model, explainer, preproc = load_artifacts()
    feature_names = preproc.get('feature_names', [])
    threshold = float(preproc.get('optimal_threshold', preproc.get('threshold', 0.3)))
    X = build_feature_row(payload, feature_names)

    probability = float(model.predict_proba(X)[0][1])
    decision = 'APPROVED' if probability >= threshold else 'REJECTED'
    risk_score = float(round((1 - probability) * 100, 2))
    risk_label = 'Low' if probability >= 0.7 else 'Moderate' if probability >= 0.4 else 'High'
    affordability_ratio = float(round(float(payload.get('income', payload.get('annual_income', 1))) / max(float(payload.get('credit_amount', payload.get('loan_amount', 1))), 1.0), 4))
    debt_to_income_ratio = float(round(float(payload.get('existing_debt', payload.get('monthly_debt', 0))) / max(float(payload.get('income', payload.get('annual_income', 1))), 1.0), 4))

    recommendation = 'Eligible for next stage review' if decision == 'APPROVED' else 'Reduce debt-to-income ratio and reapply later'
    next_steps = [
        'Review the top risk factors below',
        'Compare requested loan size against income',
        'Improve credit score and reduce existing debt before reapplying',
    ]

    top_features: List[Dict[str, Any]] = []
    approval_reasoning: List[Dict[str, Any]] = []
    chart_data = {
        'labels': ['Approval Probability', 'Risk Score'],
        'values': [round(probability * 100, 2), risk_score],
    }

    if explainer is not None:
        shap_values = explainer.shap_values(X)
        values = shap_values[0] if isinstance(shap_values, list) else shap_values[0]
        feature_shap_pairs = sorted(zip(feature_names, values), key=lambda item: abs(float(item[1])), reverse=True)[:5]
        for feature, value in feature_shap_pairs:
            direction = 'positive' if float(value) >= 0 else 'negative'
            top_features.append({'feature': feature, 'value': float(value), 'direction': direction})
            approval_reasoning.append({
                'feature': feature,
                'impact': float(value),
                'direction': direction,
                'message': f'{feature} has {direction} influence on the decision'
            })
    else:
        top_features = [{'feature': 'model_probability', 'value': probability, 'direction': 'positive' if decision == 'APPROVED' else 'negative'}]
        approval_reasoning = [{'feature': 'model_probability', 'impact': probability, 'direction': 'positive' if decision == 'APPROVED' else 'negative', 'message': 'Probability computed from XGBoost model'}]

    return PredictionBundle(
        probability=probability,
        decision=decision,
        risk_score=risk_score,
        risk_label=risk_label,
        threshold=threshold,
        recommendation=recommendation,
        next_steps=next_steps,
        chart_data=chart_data,
        top_features=top_features,
        approval_reasoning=approval_reasoning,
    )
