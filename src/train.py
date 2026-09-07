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
    t_array = t.to_numpy()
    vectorizer, X_train, X_test = fit_title_feature(train, test)
    X_train_full = hstack([csr_matrix(f_array), X_train]).tocsr()
    regressor = LGBMRegressor(importance_type='gain')
    regressor.fit(X_train_full, t_array, categorical_feature=[i for i, c in enumerate(FEATURES) if c in CATEGORICAL])
    ft = test.select(FEATURES).collect()
    ft_array = ft.with_columns(pl.col(pl.Categorical).to_physical()).to_numpy()
    X_test_full = hstack([csr_matrix(ft_array), X_test]).tocsr()
    y_pred = regressor.predict(X_test_full)
    return y_pred

def train_w_folds(df: pl.LazyFrame, start: datetime, end: datetime) -> list:
    folds = []
    while start < end:
        train_end = start
        test_end = start + relativedelta(months=1)
        traindf, testdf = split(df, train_end, test_end)
        y_pred = train_lightgbm(traindf, testdf)
        lookup = traindf.select(pl.col('title')).group_by(pl.col('title')).agg(pl.col('title').count().alias('count_title'))
        joined = testdf.join(lookup, on=["title"], how="left")
        title_count = joined.select(pl.col('count_title')).collect().to_series().to_numpy()
        cols = testdf.select(TARGET,'sold_price', 'is_archive').collect()
        fold = {
            'y_true': cols[TARGET].to_numpy(),
            'y_pred': y_pred,
            'archive': cols['is_archive'].to_numpy(),
            'price': cols['sold_price'].to_numpy(),
            'title_count': title_count,
            'start': train_end,
        }
        folds.append(fold)
        start = start + relativedelta(months=1)
    return folds

def fit_title_feature(train: pl.LazyFrame, test: pl.LazyFrame):
    vectorizer = TfidfVectorizer(min_df=5, ngram_range=(1,2), max_features=60000)
    train_titles = train.select(pl.col('title')).collect().get_column("title").to_numpy()
    test_titles = test.select(pl.col('title')).collect().get_column("title").to_numpy()
    X_train = vectorizer.fit_transform(train_titles)
    X_test = vectorizer.transform(test_titles)
    return vectorizer, X_train, X_test

def report(folds: list, segment: str=None) -> list:
    metrics = []*len(folds)
    for fold in folds:
        y_true = fold['y_true']
        y_pred = fold['y_pred']
        if segment is None:
            metrics.append(evaluate(y_true, y_pred))
            print(len(y_true))
        elif segment == 'archivelist':
            mask = fold['archive']
            metrics.append(evaluate(y_true[mask], y_pred[mask]))
            print(len(y_true[mask]))
        elif segment == 'same_titles':
            mask = fold['title_count']
            metrics.append(evaluate(y_true[(mask > 11) & (mask < 100)], y_pred[(mask > 11) & (mask < 100)]))
            print(len(y_true[(mask > 11) & (mask < 100)]))
        elif segment == 'unique_titles':
            mask = fold['title_count']
            metrics.append(evaluate(y_true[mask == 1], y_pred[mask == 1]))
            print(len(y_true[mask == 1]))
        elif segment == 'unseen_titles':
            mask = fold['title_count']
            metrics.append(evaluate(y_true[np.isnan(mask)], y_pred[np.isnan(mask)]))
            print(len(y_true[np.isnan(mask)]))
    return metrics

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