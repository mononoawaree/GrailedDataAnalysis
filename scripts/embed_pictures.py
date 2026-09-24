import numpy as np
import open_clip
import torch
from torch.utils.data import DataLoader
import polars as pl
from tqdm import tqdm

from scripts.ImagesDataset import ImagesOnDiscDataset

df = pl.scan_parquet('../data/parquets/sold_listings_20260901.parquet')
ids_keys = df.select(pl.col('photo_key'), pl.col('id')).collect()
ids = ids_keys['id'].to_numpy()
keys = ids_keys['photo_key'].to_numpy()

def main():
    mm = np.lib.format.open_memmap(r'D:\Grailed_Npys\embeddings.npy', mode='w+', dtype=np.float16, shape=(len(ids), 512))
    model, _, preprocess = open_clip.create_model_and_transforms('hf-hub:Marqo/marqo-fashionCLIP')
    model.eval()
    dataset = ImagesOnDiscDataset(keys, ids, preprocess)
    with torch.no_grad():
        ok_list = []
        loader = DataLoader(dataset, batch_size=256, num_workers=16, shuffle=False, pin_memory=True)
        device = "cuda" if torch.cuda.is_available() else "cpu"
        model = model.to(device)
        print(torch.cuda.is_available())
        for i, (images, idxs, ok) in enumerate(tqdm(loader)):
            start = i * 256
            batch = images.to(device)
            gpu_tens = model.encode_image(batch, normalize=True)
            cpu_embs = gpu_tens.cpu().numpy()
            end = start + len(cpu_embs)
            mm[start:end] = cpu_embs
            ok_list.append(ok)
        mm.flush()
        np.save(r'D:\Grailed_Npys\ok.npy', torch.cat(ok_list).numpy())
if __name__ == "__main__":
    main()