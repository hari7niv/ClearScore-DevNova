API microservice for serving the ML model locally (FastAPI)

Quick start:

1. Create virtualenv

```bash
python -m venv .venv
source .venv/bin/activate  # or .\venv\Scripts\activate on Windows
pip install -r requirements.txt
```

2. Start the server

```bash
uvicorn app:app --reload --port 8000
```

Endpoints:
- POST /auth/login {username,password} -> {access_token}
- POST /predict (Bearer token) -> {probability, decision, group_rates}
- GET /readiness -> model/explainer presence

Make sure `saved_models/` exists at repository root (created by your notebook).
