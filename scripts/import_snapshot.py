import polars as pl
from datetime import date
from pathlib import Path
import os
from dotenv import load_dotenv

load_dotenv()
URI = os.environ["URI"]

def export(uri: str, out_dir: Path) -> Path:
    q = """SELECT title, department, source, category, category_path,
         category_size, color, condition, size, styles, country_of_origin,
         price, sold_price, created_at, sold_at, cover_photo_url, location, seller_id, seller_total_transactions,
         seller_trusted, seller_rating_average, seller_rating_count, followers_count, heat_score, photo_count,
         measurement_count, external_id, currency, local_image_path, image_download_status, original_price, scraped_at, designer_ids, designer_names, id FROM sold_listings"""
    df = pl.read_database_uri(q, uri)
    out = out_dir / f"sold_listings_{date.today():%Y%m%d}.parquet"
    out.parent.mkdir(parents=True, exist_ok=True)
    df.write_parquet(out, compression="zstd")
    print(f"{out}  {df.height:,} rows")
    return out

REPO_ROOT = Path(__file__).resolve().parent.parent

def main():
    export(URI, REPO_ROOT / "data")

if __name__ == "__main__":
    main()