import polars as pl
import polars.selectors as cs

pl.Config.set_tbl_rows(-1)
pl.Config.set_fmt_str_lengths(200)
FEATURES = ['department', 'category', 'path', 'color', 'condition', 'size', 'location', 'photo_count', 'measurement_count', 'primary_designer', 'created_month', 'season', 'year']
TARGET = 'log_sold_price'
# NOT IN FEATURE LIST - title (not in v1, need a comparison point later),
# category_path/size/path_size - all derived from other columns, styles - reasonable to include for training only since
# 2025-07-01 or 2025-08-01  (see notebook), country_of_origin - same situation as with styles feature,
DENYLIST = ["Designer", "Luxury", "Rare", "Streetwear", "Stickers", "Made In Usa",
            "Band T Shirt", "Soccer Jersey", "Archival Clothing", "Vintage",
            "Japanese Brand", "Other", "Band Tees", "MLB", "NFL", "Hat Club", "Rap Tees", "Jean", "Hype Beast"]
SEASON_YEAR = r'(?i)\b(?P<season>ss|s/s|fw|f/w|aw|a/w|spring[/\s-]?summer|fall[/\s-]?winter|autumn[/\s-]?winter)[/\s-]?(?P<year>(?:19|20)\d{2}|\d{2})\b'
YEAR_ONLY = r'\b(?P<yearonly>19[89]\d|20[0-2]\d)\b'
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
    #Create new column with log sold_price that has dtype - Float64
    df = df.with_columns(
        pl.col('title').str.to_lowercase(),
        pl.col('created_at').dt.month().alias('created_month'),
        pl.col('sold_price').log().alias('log_sold_price')
    ).with_columns(
        pl.col('category_path').str.extract(r'\.(.+)', 1).alias('path'),
        pl.col('title').str.extract_groups(SEASON_YEAR).struct.field('season', 'year'),
        pl.col('title').str.extract_groups(YEAR_ONLY).struct.field('yearonly')
    ).with_columns(
        pl.coalesce(pl.col('year'), pl.col('yearonly')).alias('year')
    )
    return df

def cast_data(df: pl.LazyFrame) -> pl.LazyFrame:
    df = df.with_columns(
        cs.by_name('department', 'category', 'path', 'color', 'condition', 'size', 'location', 'primary_designer').cast(pl.Categorical),
        cs.by_name('photo_count', 'measurement_count', 'created_month', 'year').cast(pl.Int32)
    ).with_columns(
        pl.col('season').str.replace_all('/', '')
    ).with_columns(
        pl.col('season').str.replace_all('aw', 'fw')
    ).with_columns(
        pl.when(pl.col('season').str.contains('spring'))
        .then(pl.lit('ss'))
        .when(pl.col('season').str.contains('fall') | pl.col('season').str.contains('autumn'))
        .then(pl.lit('fw')).otherwise(pl.col('season')).alias('season'),

        pl.when(pl.col('year') < 100).then(
            pl.when(pl.col('year') >= 30).then(
               pl.col('year') + 1900
            ).otherwise(pl.col('year') + 2000)
        ).otherwise(pl.col('year')).alias('year')
    ).with_columns(
        pl.col('season').cast(pl.Categorical)
    )
    return df

def main():
    df = pl.read_parquet("../data/parquets/sold_listings_20260830.parquet").lazy()
    df = build_features(df)
    #print(df.select(pl.col('season'), pl.col('year'), pl.col('title'), pl.col('primary_designer'))filter(pl.col('year').is_not_null()).sort(pl.col('year'), descending=True).collect())
if __name__ == '__main__':
    main()