import pickle
import pandas as pd
import numpy as np
from fastapi import FastAPI

# main.py

import pickle
import pandas as pd
import numpy as np
from fastapi import FastAPI

# ===============================
# LOAD MODEL + EXPLAINER + META
# ===============================

with open("saved_models/credit_model_latest.pkl", "rb") as f:
    model = pickle.load(f)

with open("saved_models/shap_explainer_latest.pkl", "rb") as f:
    explainer = pickle.load(f)

with open("saved_models/preprocessing_info_latest.pkl", "rb") as f:
    meta = pickle.load(f)

FEATURE_NAMES = meta["feature_names"]
THRESHOLD = meta["optimal_threshold"]

# ===============================
# LOAD DATA (FOR SAMPLE PROFILE)
# ===============================

df = pd.read_csv("C:\\Users\\Hari\\Desktop\\Projects\\Fullstack\\ClearScore-DevNova\\ML\\Data\\home-credit-default-risk\\application_train.csv")

# same preprocessing you used
df = df.dropna(thresh=len(df)*0.5, axis=1)
df = df.fillna(df.median(numeric_only=True))
df = pd.get_dummies(df, drop_first=True)

X = df.drop("TARGET", axis=1)

# lock one sample for simulation
# CURRENT_SAMPLE = X.sample(1).iloc[1].copy()

feature_map = {
    "AMT_CREDIT": "Loan amount",
    "AMT_INCOME_TOTAL": "Applicant income",
    "DAYS_EMPLOYED": "Employment stability",
    "DAYS_BIRTH": "Age and financial maturity",
    "AMT_ANNUITY": "Loan repayment burden",
    "AMT_GOODS_PRICE": "Asset value of purchase"
}

# ===============================
# HELPER FUNCTIONS
# ===============================
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
    
    # modify loan amount
    if "AMT_CREDIT" in sample_copy:
        sample_copy["AMT_CREDIT"] = loan_amount

    df_input = pd.DataFrame([sample_copy])
    df_input = align_features(df_input)

    prob = model.predict_proba(df_input)[0][1]
    decision = "APPROVED" if prob >= THRESHOLD else "REJECTED"

    shap_vals = explainer.shap_values(df_input)[0]

    return prob, decision, shap_vals, df_input

def generate_explanation(shap_vals, df_input):
    feature_names = df_input.columns
    
    banned_features = ["EXT_SOURCE_1", "EXT_SOURCE_2", "EXT_SOURCE_3", "CODE_GENDER", "CODE_GENDER_M"]
    
    top_idx = np.argsort(np.abs(shap_vals))[-5:]
    
    reasons = []
    for i in reversed(top_idx):
        feature = feature_names[i]
        
        # skip sensitive features
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
# ===============================
# FASTAPI APP
# ===============================

app = FastAPI()

@app.get("/")
def home():
    return {"message": "Credit Risk Simulator API running"}

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
