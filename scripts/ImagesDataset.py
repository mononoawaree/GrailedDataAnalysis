import os
from pathlib import Path

import PIL
import torch
from torch.utils.data import Dataset
import clip
from PIL import Image

#Util class for DataLoader
ROOT = Path(r"D:\GrailedImages")

class ImagesOnDiscDataset(Dataset):
    def __init__(self, photo_keys, ids, transform=None, target_transform=None):
        self.photo_keys = photo_keys
        self.ids = ids
        self.transform = transform
        self.target_transform = target_transform

    def __len__(self):
        return len(self.photo_keys)

    def __getitem__(self, idx):
        key = self.photo_keys[idx]
        img_path = ROOT / key[:2] / key[2:4] / f"{key}.jpg"
        try:
            image = PIL.Image.open(img_path)
            image = image.convert("RGB")
            image = self.transform(image)
        except Exception as e:
            print(f'{self.ids[idx]} Failed with exception {e}')
            return torch.zeros(3, 224, 224), self.ids[idx], False
        return image, self.ids[idx], True

def main():
    print(clip.available_models())
if __name__ == '__main__':
    main()