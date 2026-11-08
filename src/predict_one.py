# Derive season and year from the date
# Encode the 11 structured features with the saved category maps ← you skipped this one
# Image → CLIP → 512-dim vector → pca.transform()
# Title → vectorizer.transform()
# Concatenate all four blocks in the saved column order ← and this one
# booster.predict() → log price
# np.exp() → dollars
import math

import numpy as np
import polars as pl
import sklearn
import lightgbm as lgb
from scipy.sparse import hstack, csr_matrix
from sklearn.feature_extraction.text import TfidfVectorizer
from datetime import datetime, timezone, timedelta

from scripts.embedd_one import embedd_one
from src.features import build_features, build_features_for_one, FEATURES

schema = pl.read_parquet_schema("../data/parquets/sold_listings_20260901.parquet")

PDT = timezone(timedelta(hours=-7))
booster = lgb.Booster(model_file='file')

test_row = {
    "title": "YY 5019 Round Frame Black Sunglasses",
    "department": "menswear",
    "category": "accessories",
    "category_path": "accessories.sunglasses",
    "category_size": "accessories.one_size",
    "category_path_size": "accessories.sunglasses.one_size",
    "color": "black",
    "condition": "is_new",
    "size": "one size",
    "styles": "avant-garde",
    "country_of_origin": "IT",
    "price": 100,
    "sold_price": 100,
    "sold_shipping_price": 20,
    "sold": True,
    "dropped": False,
    "created_at": datetime(2026, 3, 18, 14, 49, 55, 38000, tzinfo=PDT),
    "price_updated_at": datetime(2026, 3, 18, 14, 49, 55, 38000, tzinfo=PDT),
    "cover_photo_url": "https://media-assets.grailed.com/prd/listing/temp/bba2acf1890c47fdabb2ea8edc2a236c",
    "location": "United Kingdom",
    "seller_id": 18167,
    "seller_total_transactions": 4286,
    "seller_trusted": True,
    "seller_rating_average": 4.973104056437389,
    "seller_rating_count": 1890,
    "followers_count": 0,
    "heat_score": 4479.274791666667,
    "photo_count": 17,
    "measurement_count": 0,
    "external_id": 94069523,
    "designer_names": ["Yohji Yamamoto"],
}

def predict_one(features: dict, pca: sklearn.decomposition.IncrementalPCA, vectorizer: TfidfVectorizer) -> float:
    features_dict = {k: v for k, v in features.items() if k in schema}
    lf = pl.LazyFrame([features_dict])
    url = lf.select(pl.col("cover_photo_url")).collect().item()
    emb = embedd_one(url)
    lf = lf.with_columns(
        pl.Series("fashion_clip_embeds", [emb], dtype=pl.Array(pl.Float32, 512)) # array[f32, 512]
    )
    lf = build_features_for_one(lf)
    emb_array = lf.select(pl.col("fashion_clip_embeds")).collect().get_column("fashion_clip_embeds").to_numpy()
    title_array = lf.select(pl.col("title")).collect().get_column("title").to_numpy()
    X_emb = pca.transform(emb_array)
    X_title = vectorizer.transform(title_array)
    X_one = hstack([X_title, X_emb])
    f_train = lf.select(FEATURES).collect()
    f_train_array = f_train.with_columns(pl.col(pl.Categorical).to_physical()).to_numpy()
    X_one_full = hstack([csr_matrix(f_train_array), X_one]).tocsr()
    pred = booster.predict(X_one_full)
    return float(np.exp(pred[0]))