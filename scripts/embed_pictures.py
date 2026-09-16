import clip
import numpy as np
import torch
from torch.utils.data import DataLoader
import polars as pl

from scripts.ImagesDataset import ImagesOnDiscDataset

device = "cuda" if torch.cuda.is_available() else "cpu"
df = pl.scan_parquet('../data/parquets/sold_listings_20260901.parquet')
ids_keys = df.select(pl.col('photo_key'), pl.col('id')).collect()
ids = ids_keys['id'].to_numpy()
keys = ids_keys['photo_key'].to_numpy()

def main():
    model, preprocess = clip.load("ViT-B/32", device=device)
    dataset = ImagesOnDiscDataset(keys, ids, preprocess)
    loader = DataLoader(dataset, batch_size=100, num_workers=8, shuffle=False, pin_memory=True)
    images, idxs, ok = next(iter(loader))
    model.eval()
    with torch.no_grad():
        batch = images.to(device)
        gpu_tens = model.encode_image(batch)
        cpu_embs = gpu_tens.cpu().numpy()
        print(cpu_embs)
if __name__ == "__main__":
    main()