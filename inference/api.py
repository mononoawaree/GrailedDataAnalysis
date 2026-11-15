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
    state["booster"] = lgb.Booster(model_file=r"D:\GrailedML\TestModel/model.txt")
    state["pca"] = joblib.load(r"D:\GrailedML\TestModel/pca.joblib")
    state["vectorizer"] = joblib.load(r"D:\GrailedML\TestModel/vectorizer.joblib")
    state["cats"] = json.load(open(r"D:\GrailedML\TestModel/cat_mapping.json"))
    yield

app = FastAPI(lifespan=lifespan)

class Listing(BaseModel):
    title: str
    department: str
    category: str
    category_path: str
    color: str | None = None
    condition: str
    size: str | None = None
    created_at: datetime
    cover_photo_url: str
    location: str | None = None
    photo_count: int = 0
    measurement_count: int = 0
    designer_names: list[str]

@app.post("/predict")
def predict(listing: Listing):
    try:
        price = predict_one(listing.model_dump(), pca=state["pca"], vectorizer=state["vectorizer"],
                            booster=state["booster"], mapping=state["cats"])
    except RuntimeError as e:
        raise HTTPException(status_code=422, detail=str(e))
    return {"predicted_price": round(price, 2)}