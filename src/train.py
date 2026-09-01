import polars as pl
from src.features import build_features
from src.evaluate import evaluate

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
    #mean by designer × category - log_sold_price_right
    lookup = train.group_by(pl.col('primary_designer'), pl.col('category')).agg(pl.col('log_sold_price').mean())
    joined = test.join(lookup, on=['primary_designer', 'category'], how='left')
    #125 nulls
    #print(y_pred.null_count().collect())
    global_log_mean = train.select(pl.col('log_sold_price').mean()).collect()[0][0].item()
    joined = joined.with_columns(pl.col('log_sold_price_right').fill_null(global_log_mean))
    y_pred = joined.select(pl.col('log_sold_price_right')).collect().to_series().to_list()
    y_true = joined.select(pl.col('log_sold_price')).collect().to_series().to_list()
    print(evaluate(y_true, y_pred))
if __name__ == "__main__":
    main()