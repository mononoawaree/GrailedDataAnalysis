# Derive season and year from the date
# Encode the 11 structured features with the saved category maps ← you skipped this one
# Image → CLIP → 512-dim vector → pca.transform()
# Title → vectorizer.transform()
# Concatenate all four blocks in the saved column order ← and this one
# booster.predict() → log price
# np.exp() → dollars
import polars as pl
import sklearn
from sklearn.feature_extraction.text import TfidfVectorizer
from datetime import datetime, timezone, timedelta

from scripts.embedd_one import embedd_one
from src.features import build_features, build_features_for_one

schema = pl.read_parquet_schema("../data/parquets/sold_listings_20260824.parquet")

PDT = timezone(timedelta(hours=-7))

test_row = {
    "title": "YY 5019 Round Frame Black Sunglasses",
    "department": "menswear",
    "source": "Grailed",
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
    "sold_at": datetime(2026, 3, 22, 5, 2, 41, 623000, tzinfo=PDT),
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
    "currency": "USD",
    "local_image_path": None,
    "image_download_status": None,
    "image_download_attempted_at": None,
    "original_price": None,
    "discount_pct": None,
    "raw_payload": None,
    "scraped_at": datetime(2026, 4, 29, 21, 45, 51, 473389, tzinfo=PDT),
    "price_history": None,
    "designer_ids": [7183],
    "designer_names": ["Yohji Yamamoto"],
    "id": 3583191,
    "photo_key": " " * 16,
}

def predict_one(features: dict, model_txt: str | None, pca: sklearn.decomposition.IncrementalPCA | None, vectorizer: TfidfVectorizer | None) -> int:
    features_dict = {k: v for k, v in features.items() if k in schema}
    lf = pl.LazyFrame(features_dict)
    embedd_one(lf)
    return 0

predict_one(test_row, None, None, None)