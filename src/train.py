import polars as pl
from lightgbm import LGBMRegressor
from src.features import build_features, FEATURES, TARGET, cast_data
from src.evaluate import evaluate

TRAIN_END = pl.lit('2026-02-01').str.to_datetime(time_zone='UTC')
TEST_END = pl.lit('2026-03-01').str.to_datetime(time_zone='UTC')

def split(df: pl.LazyFrame) -> tuple[pl.LazyFrame, pl.LazyFrame]:
    df_train = df.filter(pl.col('sold_at') < TRAIN_END)
    df_test = df.filter((pl.col('sold_at') >= TRAIN_END) & (pl.col('sold_at') < TEST_END))
    return df_train, df_test

def train_base_lightgbm(train: pl.LazyFrame, test: pl.LazyFrame):
    f = train.select(FEATURES).collect()
    t = train.select(TARGET).collect()
    #get ndarray to work with lightgbm
    f_array = f.to_pandas()
    t_array = t.to_pandas()
    regressor = LGBMRegressor(importance_type='gain')
    regressor.fit(f_array, t_array)
    ft = test.select(FEATURES).collect().to_pandas()
    y_pred = regressor.predict(ft)
    y_true = test.select(TARGET).collect().to_pandas()
    result = evaluate(y_true, y_pred)
    print(result)

def main():
    df = pl.read_parquet("../data/parquets/sold_listings_20260901.parquet").lazy()
    df = build_features(df)
    df = cast_data(df)
    train, test = split(df)
    train_base_lightgbm(train, test)
if __name__ == "__main__":
    main()