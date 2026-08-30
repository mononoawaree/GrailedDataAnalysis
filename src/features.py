import polars as pl
import polars.selectors as cs

FEATURES = ['department', 'category', 'path', 'color', 'condition', 'size', 'location', 'photo_count', 'measurement_count', 'primary_designer', 'created_month']
TARGET = 'sold_price'
# NOT IN FEATURE LIST - title (not in v1, need a comparison point later),
# category_path/size/path_size - all derived from other columns, styles - reasonable to include for training only since
# 2025-07-01 or 2025-08-01  (see notebook), country_of_origin - same situation as with styles feature,
DENYLIST = ["Designer", "Luxury", "Rare", "Streetwear", "Stickers", "Made In Usa",
            "Band T Shirt", "Soccer Jersey", "Archival Clothing", "Vintage",
            "Japanese Brand", "Other", "Band Tees", "MLB", "NFL", "Hat Club", "Rap Tees", "Jean", "Hype Beast"]

def build_features(df : pl.LazyFrame) -> pl.LazyFrame:
    df = transform_data(df)
    df = cast_data(df)
    return df

def transform_data(df: pl.LazyFrame) -> pl.LazyFrame:
    #collapse designer_names list and map designers from denylist to 'unknown'
    # -> got 1 string for designers_names in primary_designer :3
    DENY_NORM = {d.lower().strip() for d in DENYLIST}
    df = df.with_columns(
        pl.col('designer_names')
        .list.eval(
            pl.element().filter(
                pl.element().str.to_lowercase().str.strip_chars().is_in(DENY_NORM).not_()
            )
        )
        .alias('primary_designer')
    )
    df = df.with_columns(
        pl.col('primary_designer').list.first().fill_null('unknown')
    )
    #Create separate column for path from category_path column -> to use it as a feature :)
    #Derive created_month from created_at
    df = df.with_columns(
        pl.col('category_path').str.extract(r'\.(.+)', 1).alias('path'),
        pl.col('created_at').dt.month().alias('created_month')
    )
    return df

def cast_data(df: pl.LazyFrame) -> pl.LazyFrame:
    df = df.with_columns(
        cs.by_name('department', 'category', 'path', 'color', 'condition', 'size', 'location', 'primary_designer').cast(pl.Categorical),
        cs.by_name('photo_count', 'measurement_count', 'created_month').cast(pl.Int32),
    )
    return df

def main():
    df = pl.read_parquet("../data/parquets/sold_listings_20260830.parquet").lazy()
    df = build_features(df)
    print(df.select(pl.col('primary_designer')).filter(pl.col('primary_designer') == 'unknown').collect().height)
if __name__ == '__main__':
    main()