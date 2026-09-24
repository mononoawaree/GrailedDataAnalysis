from datetime import datetime, UTC

import numpy as np
import polars as pl
import sklearn
from dateutil.relativedelta import relativedelta
from lightgbm import LGBMRegressor
from scipy.sparse import hstack, csr_matrix
from sklearn.feature_extraction.text import TfidfVectorizer

from src.features import build_features, FEATURES, TARGET, cast_data, CATEGORICAL, ARCHIVELIST, ICARELIST
from src.evaluate import evaluate

TRAIN_END = datetime(2026, 2, 1, tzinfo=UTC) #pl.lit('2026-02-01').str.to_datetime(time_zone='UTC')
TEST_END = datetime(2026, 3, 1, tzinfo=UTC) #pl.lit('2026-03-01').str.to_datetime(time_zone='UTC')

# Splits train/test data
def split(df: pl.LazyFrame, train_end, test_end) -> tuple[pl.LazyFrame, pl.LazyFrame]:
    df_train = df.filter(pl.col('sold_at') < train_end)
    df_test = df.filter((pl.col('sold_at') >= train_end) & (pl.col('sold_at') < test_end))
    return df_train, df_test

def train_lightgbm(train: pl.LazyFrame, test: pl.LazyFrame) -> list:
    """Fit LightGBM on structured + TF-IDF title features, return test predictions.
       TF-IDF is fitted on `train` only — the vocabulary is learned, so it must
       stay inside the fold.
       """
    f_train = train.select(FEATURES).collect()
    t_train = train.select(TARGET).collect()

    # LightGBM can't read Polars categoricals; to_physical() gives the integer
    # codes. Safe because the cast happens before the split, so train/test
    # share one mapping.
    f_train_array = f_train.with_columns(pl.col(pl.Categorical).to_physical()).to_numpy()
    t_train_array = t_train.to_numpy()

    X_train, X_test = fit_title_embeddings_features(train, test)
    X_train_full = hstack([csr_matrix(f_train_array), X_train]).tocsr()

    regressor = LGBMRegressor(importance_type='gain')
    # !!!Breaks if csr_matrix(f_array) not first in hstack!!!
    regressor.fit(X_train_full, t_train_array, categorical_feature=[i for i, c in enumerate(FEATURES) if c in CATEGORICAL])
    f_test = test.select(FEATURES).collect()
    f_test_array = f_test.with_columns(pl.col(pl.Categorical).to_physical()).to_numpy()
    X_test_full = hstack([csr_matrix(f_test_array), X_test]).tocsr()
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

def fit_title_embeddings_features(train: pl.LazyFrame, test: pl.LazyFrame):
    vectorizer = TfidfVectorizer(min_df=5, ngram_range=(1,2), max_features=60000)
    pca = sklearn.decomposition.IncrementalPCA(n_components=256, batch_size=10000)
    train_titles = train.select(pl.col('title')).collect().get_column("title").to_numpy()
    test_titles = test.select(pl.col('title')).collect().get_column("title").to_numpy()
    train_embedds_array = train.select('fashion_clip_embbeds').collect().get_column('fashion_clip_embbeds').to_numpy()
    test_embedds_array = test.select('fashion_clip_embbeds').collect().get_column('fashion_clip_embbeds').to_numpy()
    X_train_title = vectorizer.fit_transform(train_titles)
    X_test_title = vectorizer.transform(test_titles)
    X_train_emb = pca.fit_transform(train_embedds_array)
    print(pca.explained_variance_ratio_.sum())
    X_test_emb = pca.transform(test_embedds_array)
    print(X_train_title.shape, X_train_emb.shape, X_test_title.shape, X_test_emb.shape)
    X_train = hstack([X_train_title, X_train_emb])
    X_test = hstack([X_test_title, X_test_emb])
    return X_train, X_test

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
        elif segment == 'within_50_250':
            mask = fold['price']
            metrics.append(evaluate(y_true[(mask >= 50) & (mask <= 250)], y_pred[(mask >= 50) & (mask <= 250)]))
            print(len(y_true[mask]))
    return metrics

def main():
   print('Hello World')
if __name__ == "__main__":
    main()