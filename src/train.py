from datetime import datetime, UTC

import numpy as np
import polars as pl
from dateutil.relativedelta import relativedelta
from lightgbm import LGBMRegressor
from scipy.sparse import hstack, csr_matrix
from sklearn.feature_extraction.text import TfidfVectorizer

from src.features import build_features, FEATURES, TARGET, cast_data, CATEGORICAL, ARCHIVELIST, ICARELIST
from src.evaluate import evaluate

TRAIN_END = datetime(2026, 2, 1, tzinfo=UTC) #pl.lit('2026-02-01').str.to_datetime(time_zone='UTC')
TEST_END = datetime(2026, 3, 1, tzinfo=UTC) #pl.lit('2026-03-01').str.to_datetime(time_zone='UTC')

def split(df: pl.LazyFrame, train_end, test_end) -> tuple[pl.LazyFrame, pl.LazyFrame]:
    df_train = df.filter(pl.col('sold_at') < train_end)
    df_test = df.filter((pl.col('sold_at') >= train_end) & (pl.col('sold_at') < test_end))
    return df_train, df_test

def train_lightgbm(train: pl.LazyFrame, test: pl.LazyFrame) -> list:
    f = train.select(FEATURES).collect()
    t = train.select(TARGET).collect()
    f_array = f.with_columns(pl.col(pl.Categorical).to_physical()).to_numpy()
    #get ndarray to work with lightgbm
    t_array = t.to_pandas()
    vectorizer, X_train, X_test = fit_title_feature(train, test)
    X_train_full = hstack([csr_matrix(f_array), X_train]).tocsr()
    regressor = LGBMRegressor(importance_type='gain')
    regressor.fit(X_train_full, t_array, categorical_feature=[i for i, c in enumerate(FEATURES) if c in CATEGORICAL])
    ft = test.select(FEATURES).collect()
    ft_array = ft.with_columns(pl.col(pl.Categorical).to_physical()).to_numpy()
    X_test_full = hstack([csr_matrix(ft_array), X_test]).tocsr()
    y_pred = regressor.predict(X_test_full)
    importances = regressor.feature_importances_
    text_feature_names = list(vectorizer.get_feature_names_out())
    all_feature_names = FEATURES + text_feature_names
    importance_df = pl.DataFrame({
        "feature": all_feature_names,
        "importance": regressor.feature_importances_
    })
    top_100_df = importance_df.sort("importance", descending=True).head(100)
    bottom_100_df = importance_df.sort(pl.col("importance") != 0.0, descending=False).head(100)
    print('TOP 100 HIGHEST GAIN FEATURES')
    print(top_100_df)
    print('\nTOP 100 LOWEST GAIN FEATURES')
    print(bottom_100_df)
    return y_pred

def train_w_folds(df: pl.LazyFrame, start: datetime, end: datetime, segment: str=None) -> list:
    metrics = []
    result = {}
    opt = input('Evaluate on segment - 1, Evaluate on everything except segment - 2, Evaluate per decile - 3, 0 - to exit')
    len_sample = 0
    while start < end:
        train_end = start
        test_end = start + relativedelta(months=1)
        traindf, testdf = split(df, train_end, test_end)
        if segment is not None:
            y_pred = train_lightgbm(traindf, testdf)
            cols = testdf.select(TARGET, 'sold_price', segment).collect()
            y_true = cols[TARGET].to_numpy()
            mask = cols[segment].to_numpy()
            price = cols['sold_price'].to_numpy()
            if opt == '1':
                result = evaluate(y_true[mask], y_pred[mask])
                len_sample = len(y_true[mask])
            elif opt == '2':
                result = evaluate(y_true[~mask], y_pred[~mask])
                len_sample = len(y_true[~mask])
            elif opt == '3':
                for lo, hi in [(0, 25), (25, 50), (50, 100), (100, 250), (250, 500), (500, 1000), (1000, 10 ** 9)]:
                    m = (price >= lo) & (price < hi)
                    result = evaluate(y_true[m], y_pred[m])
            elif opt == '0':
                break
            else:
                print('Invalid option, try again')
                continue
        else:
            print('Evaluating for everything')
            y_pred = train_lightgbm(traindf, testdf)
            y_true = testdf.select(TARGET).collect().to_series().to_numpy()
            result = evaluate(y_true, y_pred)
        result['start_fold'] = datetime(train_end.year, train_end.month, train_end.day)
        result['end_fold'] = datetime(test_end.year, test_end.month, test_end.day)
        result['len_sample'] = len_sample
        metrics.append(result)
        start = start + relativedelta(months=1)
    return metrics

def fit_title_feature(train: pl.LazyFrame, test: pl.LazyFrame):
    vectorizer = TfidfVectorizer(min_df=5, ngram_range=(1,2))
    train_titles = train.select(pl.col('title')).collect().get_column("title").to_numpy()
    test_titles = test.select(pl.col('title')).collect().get_column("title").to_numpy()
    X_train = vectorizer.fit_transform(train_titles)
    X_test = vectorizer.transform(test_titles)
    return vectorizer, X_train, X_test

def main():
    df = pl.read_parquet("../data/parquets/sold_listings_20260901.parquet").lazy()
    df = build_features(df)
    df = df.with_columns(
        pl.col('primary_designer').is_in(ARCHIVELIST).alias('archivelist'),
        pl.col('primary_designer').is_in(ICARELIST).alias('brands_icare')
    )
    start = datetime(2026, 2, 1, tzinfo=UTC)
    start = start - relativedelta(months=12)
    end = datetime(2026, 3, 1, tzinfo=UTC)
    option = int(input('Choose an option - 1 fold(1) / 12(2) folds train'))
    while True:
        if option == 1:
            train, test = split(df, TRAIN_END, TEST_END)
            y_pred = train_lightgbm(train, test)
            y_true = test.select(TARGET).collect().to_series().to_numpy()
            res = evaluate(y_pred, y_true)
            print(res)
            break
        elif option == 2:
            l = train_w_folds(df, start, end)
            for metric in l:
                print(metric)
            break
        else:
            print('Invalid option, try again')
            break
if __name__ == "__main__":
    main()