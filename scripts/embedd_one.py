import io
import urllib

import open_clip
import requests
import torch

from PIL import Image

model, _, preprocess = open_clip.create_model_and_transforms('hf-hub:Marqo/marqo-fashionCLIP')
device = "cuda" if torch.cuda.is_available() else "cpu"
model.eval().to(device)

#turn off the gradients
@torch.inference_mode()
def embedd_one(url: str) -> float:
    try:
        response = requests.get(url, timeout=10)
        response.raise_for_status()
    except requests.exceptions.Timeout:
        raise RuntimeError("Retrieval error")
    image = Image.open(io.BytesIO(response.content)).convert("RGB")
    tensor_input = preprocess(image).unsqueeze(0).to(device)
    #.unsqueeze(0) because 0th element has to be a batch size
    tensor = model.encode_image(tensor_input, normalize=True)[0].cpu().numpy()
    print(tensor.shape)
    return tensor
