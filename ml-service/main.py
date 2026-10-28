from contextlib import asynccontextmanager
from pathlib import Path
from typing import Literal
import pickle
import joblib

import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field


# define the location of the pretrained model files
MODEL_DIR = Path(__file__).resolve().parent / "model"
MODEL_PATH = MODEL_DIR / "annual_logistic_model.pkl"
SCORING_PATH = MODEL_DIR / "scoring_info.pkl"


# define the six features in the exact order expected by the model
MODEL_FEATURES = [
    "bin_return on assets",
    "bin_net income margin",
    "bin_fixed asset turnover",
    "bin_total debt / total capital (%)",
    "bin_ebitda / interest expense",
    "bin_total debt / ebitda",
]


# map the api fields to the financial ratios used by the model
FEATURE_MAPPING = {
    "returnOnAssets": "return on assets",
    "profitMargin": "net income margin",
    "fixedAssetTurnover": "fixed asset turnover",
    "debtToTotalCapitalPercent": "total debt / total capital (%)",
    "interestCoverage": "ebitda / interest expense",
    "totalDebtToEbitda": "total debt / ebitda",
}


# define the financial information received from spring boot
class FinancialProfileRequest(BaseModel):
    returnOnAssets: float = Field(..., allow_inf_nan=False)
    profitMargin: float = Field(..., allow_inf_nan=False)
    fixedAssetTurnover: float = Field(..., allow_inf_nan=False)
    debtToTotalCapitalPercent: float = Field(..., allow_inf_nan=False)
    interestCoverage: float = Field(..., allow_inf_nan=False)
    totalDebtToEbitda: float = Field(..., allow_inf_nan=False)


# define the prediction returned to spring boot
class RiskPredictionResponse(BaseModel):
    probability: float
    riskLevel: Literal["LOW_RISK", "MEDIUM_RISK", "HIGH_RISK"]
    modelVersion: str


# keep the loaded model available for predictions
model_state = {}


# load the pretrained model once when the api starts
@asynccontextmanager
async def lifespan(app: FastAPI):
    # only load these pickle files after accepting their security risks
    model = joblib.load(MODEL_PATH)

    with open(SCORING_PATH, "rb") as file:
        scoring_info = pickle.load(file)

    # check that the model expects our six features
    actual_features = list(model.feature_names_in_)

    if actual_features != MODEL_FEATURES:
        raise RuntimeError(
            f"model feature mismatch: {actual_features}"
        )

    # check that scoring information exists for every financial ratio
    for feature in FEATURE_MAPPING.values():
        if feature not in scoring_info:
            raise RuntimeError(
                f"missing scoring information for {feature}"
            )

    model_state["model"] = model
    model_state["scoring_info"] = scoring_info

    yield

    model_state.clear()


app = FastAPI(
    title="LimitGuard ML Risk Service",
    lifespan=lifespan,
)


# convert one financial ratio into its trained scoring value
def calculate_binned_score(value, feature_name, scoring_info):
    feature_scoring = scoring_info[feature_name]

    intervals = feature_scoring["intervals"]
    rates = feature_scoring["rates"]

    for interval, rate in zip(intervals, rates):
        if interval == "Missing":
            continue

        low, high = interval

        if low < value <= high:
            return float(rate)

    # reject values that do not belong to a known interval
    raise ValueError(
        f"value for {feature_name} is outside the model's scoring ranges"
    )


# convert the six financial ratios into the model's input format
def preprocess_financial_profile(profile: FinancialProfileRequest):
    scoring_info = model_state["scoring_info"]

    input_values = profile.model_dump()
    scored_features = {}

    for api_field, model_field in FEATURE_MAPPING.items():
        value = input_values[api_field]

        score = calculate_binned_score(
            value,
            model_field,
            scoring_info,
        )

        scored_features[f"bin_{model_field}"] = score

    # preserve the feature order used during training
    return pd.DataFrame(
        [[scored_features[name] for name in MODEL_FEATURES]],
        columns=MODEL_FEATURES,
    )


# check whether the python service is running
@app.get("/health")
def health():
    return {
        "status": "UP",
        "modelLoaded": "model" in model_state,
    }


# calculate an experimental financial risk prediction
@app.post("/predict", response_model=RiskPredictionResponse)
def predict(profile: FinancialProfileRequest):
    try:
        model = model_state["model"]

        features = preprocess_financial_profile(profile)

        # obtain the probability for the positive model class
        probability = float(
            model.predict_proba(features)[0, 1]
        )

        # apply the categories defined by the original model wrapper
        if probability >= 0.7:
            risk_level = "HIGH_RISK"
        elif probability >= 0.4:
            risk_level = "MEDIUM_RISK"
        else:
            risk_level = "LOW_RISK"

        return RiskPredictionResponse(
            probability=probability,
            riskLevel=risk_level,
            modelVersion="financial-risk-model-sklearn-1.6.1",
        )

    except (ValueError, KeyError) as error:
        raise HTTPException(
            status_code=422,
            detail=str(error),
        )
