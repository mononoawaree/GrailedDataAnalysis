import polars as pl
from src.features import build_features

TRAIN_END = pl.lit('2026-02-01').str.to_datetime(time_zone='UTC')
TEST_END = pl.lit('2026-03-01').str.to_datetime(time_zone='UTC')

def split(df: pl.LazyFrame) -> tuple[pl.LazyFrame, pl.LazyFrame]:
    df_train = df.filter(pl.col('sold_at') < TRAIN_END)
    df_test = df.filter((pl.col('sold_at') >= TRAIN_END) & (pl.col('sold_at') < TEST_END))
    return df_train, df_test

def main():
    df = pl.read_parquet("../data/parquets/sold_listings_20260830.parquet").lazy()
    df = build_features(df)
    train, test = split(df)
    print(test.select(pl.all()).collect().height)
    print(train.select(pl.all()).collect().height)
if __name__ == "__main__":
    main()