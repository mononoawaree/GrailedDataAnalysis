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

from scripts.embedd_one import embedd_one
from src.features import build_features, build_features_for_one, FEATURES

schema = pl.read_parquet_schema("../data/parquets/sold_listings_20260901.parquet")


def predict_one(features: dict, pca: sklearn.decomposition.IncrementalPCA, vectorizer: TfidfVectorizer, booster: lgb.Booster, mapping: dict[str, dict[str, int]]) -> float:
    features_dict = {k: v for k, v in features.items() if k in schema}
    lf = pl.LazyFrame([features_dict])
    url = lf.select(pl.col("cover_photo_url")).collect().item()
    emb = embedd_one(url)
    lf = lf.with_columns(
        pl.Series("fashion_clip_embeds", [emb], dtype=pl.Array(pl.Float32, 512)) # array[f32, 512]
    )
    lf = build_features_for_one(lf)
    print(lf.collect())
    emb_array = lf.select(pl.col("fashion_clip_embeds")).collect().get_column("fashion_clip_embeds").to_numpy()
    title_array = lf.select(pl.col("title")).collect().get_column("title").to_numpy()
    X_emb = pca.transform(emb_array)
    X_title = vectorizer.transform(title_array)
    X_one = hstack([X_title, X_emb])
    f_train = lf.select(FEATURES).collect()
    f_train = f_train.with_columns(
        pl.col(c).cast(pl.String).replace_strict(mapping[c], default=None, return_dtype=pl.Int64)
        for c in mapping
    )
    f_train_array = f_train.to_numpy()
    X_one_full = hstack([csr_matrix(f_train_array), X_one]).tocsr()
    pred = booster.predict(X_one_full)
    return float(np.exp(pred[0]))