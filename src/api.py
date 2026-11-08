import json
from contextlib import asynccontextmanager
from datetime import datetime

import joblib
import lightgbm as lgb
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from predict_one import predict_one   # also loads CLIP at import time, once

state = {}

@asynccontextmanager
async def lifespan(app: FastAPI):
    state["booster"] = lgb.Booster(model_file="artifacts/model.txt")
    state["pca"] = joblib.load("artifacts/pca.joblib")
    state["vectorizer"] = joblib.load("artifacts/tfidf.joblib")
    state["cats"] = json.load(open("artifacts/cats.json"))
    yield

app = FastAPI(lifespan=lifespan)

class Listing(BaseModel):
    title: str
    cover_photo_url: str
    department: str
    category: str
    category_path: str
    designer_names: list[str]
    condition: str
    created_at: datetime
    color: str | None = None
    size: str | None = None
    location: str | None = None
    photo_count: int = 0
    measurement_count: int = 0


@app.post("/predict")
def predict(listing: Listing):
    try:
        price = predict_one(listing.model_dump(), state["booster"], state["pca"],
                            state["vectorizer"], state["cats"])
    except RuntimeError as e:
        raise HTTPException(status_code=422, detail=str(e))
    return {"predicted_price": round(price, 2)}