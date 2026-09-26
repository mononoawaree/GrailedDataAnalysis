import asyncio
import hashlib
from pathlib import Path
from typing import Any, Generator

import aiohttp

import polars as pl
import time


start = time.time()

df = pl.scan_parquet('../data/parquets/sold_listings_20260901.parquet')
ROOT = Path(r"D:\GrailedImages")
sem = asyncio.Semaphore(30)
timeout = aiohttp.ClientTimeout(total=30)
pairs = df.select(["cover_photo_url", "photo_key"]).unique().collect()
urls = pairs["cover_photo_url"].to_list()
keys = pairs["photo_key"].to_list()
total = len(urls)
n_batches = (total + 999) // 1000
existing = {p.stem for p in ROOT.rglob("*.jpg")}
missing = [u for u, k in zip(urls, keys) if k not in existing]
retry_urls = [u for u in missing if "media-assets.grailed.com" in u]

def batch_urls(urls: pl.Series) -> Generator[list[Any], Any, None]:
    for offset in range(0, len(urls), 1000):
        yield urls.slice(offset, 1000).to_list()

async def fetch_one(url, session: aiohttp.ClientSession):
    key = hashlib.sha256(url.encode()).hexdigest()[:16]
    path = ROOT / key[:2] / key[2:4] / f"{key}.jpg"
    if path.exists():
        return
    try:
        async with sem:
            async with session.get(url) as resp:
                resp.raise_for_status()
                data = await resp.read()
    except Exception as e:
        print(f"FAIL {url}: {e}")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_bytes(data)
    tmp.replace(path)

async def main():
    async with aiohttp.ClientSession(timeout=timeout) as session:
        for batch in batch_urls(pl.Series(retry_urls)):
            await asyncio.gather(*[fetch_one(u, session) for u in batch], return_exceptions=True)
asyncio.run(main())



