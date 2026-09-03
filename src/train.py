from datetime import datetime, timedelta, UTC
import polars as pl
from PIL.Image import preinit
from dateutil.relativedelta import relativedelta
from lightgbm import LGBMRegressor
from src.features import build_features, FEATURES, TARGET, cast_data
from src.evaluate import evaluate

TRAIN_END = datetime(2026, 2, 1, tzinfo=UTC) #pl.lit('2026-02-01').str.to_datetime(time_zone='UTC')
TEST_END = datetime(2026, 3, 1, tzinfo=UTC) #pl.lit('2026-03-01').str.to_datetime(time_zone='UTC')

def split(df: pl.LazyFrame, train_end, test_end) -> tuple[pl.LazyFrame, pl.LazyFrame]:
    df_train = df.filter(pl.col('sold_at') < train_end)
    df_test = df.filter((pl.col('sold_at') >= train_end) & (pl.col('sold_at') < test_end))
    return df_train, df_test

def train_lightgbm(train: pl.LazyFrame, test: pl.LazyFrame) -> dict:
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
    importances = regressor.feature_importances_
    result = evaluate(y_true, y_pred)
    return result

def train_w_folds(df: pl.LazyFrame, start: datetime, end: datetime) -> list:
    metrics = []
    while start < end:
        train_end = start
        test_end = start + relativedelta(months=1)
        traindf, testdf = split(df, train_end, test_end)
        result = train_lightgbm(traindf, testdf)
        result['start_fold'] = datetime(train_end.year, train_end.month, train_end.day)
        result['end_fold'] = datetime(test_end.year, test_end.month, test_end.day)
        metrics.append(result)
        start = start + relativedelta(months=1)
    return metrics

def main():
    df = pl.read_parquet("../data/parquets/sold_listings_20260901.parquet").lazy()
    df = build_features(df)
    start = datetime(2026, 2, 1, tzinfo=UTC)
    start = start - relativedelta(months=12)
    end = datetime(2026, 3, 1, tzinfo=UTC)
    train, test = split(df, TRAIN_END, TEST_END)
    res = train_lightgbm(train, test)
    print(res)
    #l = train_w_folds(df, start, end)
    #for metric in l:
        #print(metric)
if __name__ == "__main__":
    main()