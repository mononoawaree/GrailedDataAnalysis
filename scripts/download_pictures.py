import polars as pl

df = pl.read_parquet('../data/parquets/sold_listings_20260901.parquet').lazy()
urls = df.select(pl.col('cover_photo_url').unique()).collect()['cover_photo_url'].to_list()
#img_path = ROOT / key[:2] / key[2:4] / f"{key}.jpg"



