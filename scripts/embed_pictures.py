from torch.utils.data import DataLoader
import polars as pl

from scripts.ImagesDataset import ImagesOnDiscDataset


def embed_pictures():
    df = pl.scan_parquet('../data/parquets/sold_listings_20260901.parquet')
    ids_keys = df.select(pl.col('photo_keys'), pl.col('id')).collect()
    ids = ids_keys['id'].to_numpy()
    keys = ids_keys['photo_keys'].to_numpy()
    dataset = ImagesOnDiscDataset(keys, ids)
    loader = DataLoader(dataset, batch_size=1000, num_workers=8, shuffle=False, pin_memory=True)