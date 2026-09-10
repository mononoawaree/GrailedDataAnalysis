import hashlib
import os
from pathlib import Path

import aiohttp
import asyncio
import polars as pl

ROOT = Path(r"D:\GrailedImages")
df = pl.read_parquet("../data/parquets/sold_listings_20260901.parquet").lazy()
urls_ids = df.select(pl.col("cover_photo_url"), pl.col("id")).collect()
# def fetch_url(session: aiohttp.ClientSession, url: str) -> str:
#     try:
#         with session.get(url, timeout=10) as response:
#             print(f"Started fetching: {url}")
#             text_data = response.text()
#             print(f"Finished fetching: {url} (Status: {response.status})")
#             return text_data
#     except Exception as e:
#         print(f"Error fetching {url}: {e}")
#         return ''
def batch_update():
    loop = asyncio.get_event_loop()

async def main():
    conn = aiohttp.TCPConnector(limit=30)
    url = urls_ids.get_column("cover_photo_url").first()
    id = urls_ids.get_column("id").first()
    async with aiohttp.ClientSession(connector=conn) as session:
        async with session.get(url) as resp:
            print(resp.status)
            image_bin = await resp.read()
            key = hashlib.sha256(url.encode()).hexdigest()[:16]
            img_path = ROOT / key[:2] / key[2:4] / f"{key}.jpg"
            img_path.parent.mkdir(parents=True, exist_ok=True)
            with open(img_path, 'wb') as f:
                f.write(image_bin)
if __name__ == "__main__":
    asyncio.run(main())
