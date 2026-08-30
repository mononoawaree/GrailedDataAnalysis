# GrailedML — Price Model v1 Roadmap

*Created 2026-08-24 · Repo: `GrailedDataAnalysis` · Owner: Vladimir*

One file to steer the build. Work top to bottom, check items off, and reference item IDs in commits (`git commit -m "B4: first lightgbm baseline"`). When a **⚠️ GATE** fails, stop and change the plan — gates exist so a bad result redirects the work instead of getting buried.

Companions: `DECISIONS.md` holds the *why* behind every non-obvious choice. MLflow holds the *what happened*. This file holds the *what's next*.

**Deadline context:** applications run now → mid-October. v1 ships before any re-scrape, any second platform, any deep learning. A boring model with honest evaluation beats an impressive model with broken evaluation.

---

## The two rules that outrank everything

**1. The prediction-time test.** A column may be a feature only if its value exists at the moment of prediction: you are holding an unlisted item, deciding a price. Item attributes — yes. Your own seller profile — yes. Anything that accumulates while listed (`heat_score`, `followers_count`) — no. Anything derived from the sale (`discount_pct`, `sold_at`) — never.

**2. Split first, fit second.** Any number computed *across rows* (median, mean encoding, vocabulary, quantile cutoff) is fit on the training side of the split only, then applied frozen to validation. Inside walk-forward, that means refit inside every fold.

Nearly every broken ML portfolio project violates one of these two.

---

## Dataset facts (snapshot 2026-08-24)

| Fact | Value |
|---|---|
| Rows | 4,345,274 (~90% of Grailed's ~4.82M sold index — near-complete, claimable) |
| Window | 2021-08 → 2026-03 (≈5 months stale; re-scrape is post-v1, see Parking Lot) |
| Yearly volume | 284k ('21 partial) · 948k · 1,005k · 1,035k · 903k · 170k ('26 partial) — flat, ~57–86k/month |
| Price | min $1 · median $80 · max $35,000 — tails unaudited (F5) |
| Nulls | 0 in `sold_price`, 0 in `sold_at` |
| Currency | single value — column droppable |
| Multi-designer rows | 1,861,932 (43%) — collapse rule undecided (F7) |
| Snapshot naming | `data/sold_listings_YYYYMMDD.parquet` — one file per day, last run of the day wins |

## Column disposition

Features are filtered in `src/features.py`, not in SQL. The Parquet carries everything cheap; one list in one file decides what the model sees, with reasons written next to it.

| Bucket | Columns |
|---|---|
| **Features (pre-listing)** | `title`, `department`, `category`, `category_path`, `category_size`, `size`, `styles`, `color`, `condition`, `country_of_origin`, `location`, `measurement_count`, `photo_count`\*, `designer_names` → collapsed (F7); seller profile: `seller_id`, `seller_total_transactions`, `seller_trusted`, `seller_rating_average`†, `seller_rating_count`† |
| **Target** | `sold_price` → model on `log(sold_price)` |
| **Split key** | `sold_at` — lives in the Parquet, never a feature |
| **Leak — never features** | `heat_score`, `followers_count`, `price_updated_at`, `sold_shipping_price`; `discount_pct` (excluded from export entirely) |
| **Asking-price family — out of v1 framing** | `price`, `original_price` (legitimate only in the "active listing" framing — not ours) |
| **Audit first** | `sold`, `dropped` (F6); †seller stats — scrape-time or sale-time snapshot? mild leak if scrape-time |
| **Image pipeline** | `cover_photo_url`, `local_image_path`, `image_download_status` |
| **Provenance / ids** | `id`, `external_id`, `source`, `scraped_at`, `created_at`‡ |
| **Left out of export** | `raw_payload`, `price_history` (bloat) |

\* `photo_count`, `sold`, `dropped` are **not in the current export query** — add them before the full pull (F2).
‡ `created_at` month/season is known at listing time and usable; `sold_at − created_at` (days-to-sell) is not.

## Decisions already locked — seed `DECISIONS.md` with these

| Date | Decision | Why |
|---|---|---|
| 08-22 | Postgres = source of truth; train only from versioned Parquet snapshots | Tuesday's run must replay on Friday |
| 08-23 | Framing: "what will this **unlisted** item sell for" | matches the sourcing use-case; active-listing framing is easier and less useful |
| 08-23 | Target = `sold_price`, log-transformed; shipping excluded | shipping ≈ pass-through; one flat rate per listing anyway |
| 08-23 | No WHERE filter on export | verified: 0 nulls, 0 non-positive prices — the filter was a no-op |
| 08-23 | Export wide, filter at the feature list | leak-filtering is a modeling concern; keeps re-framing cheap |
| 08-24 | Walk-forward eval at monthly grain; yearly rejected | yearly conflates accuracy with staleness; ~4 folds is too noisy |
| 08-24 | Fold start = open → resolved by learning curve (E3), not by picking a round year | |

---

## Phase 0 — Foundation *(target: Aug 31)*

Goal: a trustworthy snapshot and the four open data questions answered.

- [x] **F1. Export script writes date-stamped Parquet** `easy` — done 08-24
- [ ] **F2. Fix query + dtype check on 10k** `easy` — add `photo_count`, `sold`, `dropped` to the SELECT; re-pull with LIMIT. *Done when:* `sold_at: Datetime`, `sold_price: Int`, `designer_names: List(String)`; any numeric that arrived as String is fixed before the full pull.
- [ ] **F3. Full 4.3M export** `very easy` — *Done when:* printed row count ≈ 4,345,274; file size + duration noted in the progress log.
- [ ] **F4. `src/paths.py` + kill relative paths** `easy` — *Done when:* script **and** notebook both import `DATA_DIR`; PyCharm run-config working dir = repo root; stray `scripts/data/` deleted. (This bit you four times in one day. Make it structurally impossible.)
- [ ] **F5. Audit the tails** `easy` — pull 50 rows at ≤ $5 and 50 at ≥ $10k, look at them. *Done when:* keep/cut decision with affected row counts in `DECISIONS.md`.
- [ ] **F6. Explain `sold` / `dropped`** `easy` — why does a sold-listings table have a `sold` boolean? *Done when:* distribution known; non-sales excluded and counted, or confirmed all-true.
- [ ] **F7. Multi-designer collapse rule** `medium` — eyeball 30 multi-designer rows **first**. Candidates: first element · primary via `designer_aliases` · multi-hot top-N. *Done when:* rule implemented in `features.py`; `DECISIONS.md` entry says what each rejected option would have lost.
- [ ] **F8. Secrets hygiene** `very easy` — `git check-ignore .env` passes; password absent from tracked files **and history** (rotate the DB password if not).

## Phase 1 — Baseline *(target: Sep 7)*

Goal: a defensible number to beat.

- [ ] **B1. Pick THE metric** `medium` — MAE / RMSE / MAPE / median-AE will *rank models differently* on an $80-median, $35k-max distribution. Also fix the standing report views now: overall + price-decile + designer-tier. *Done when:* one-paragraph defense in `DECISIONS.md`. Decide **before** training anything.
- [ ] **B2. Global median baseline** `very easy` — one number. The floor of embarrassment.
- [ ] **B3. Grouped median baseline** `easy` — median by designer × category, falling back designer → category → global for unseen combos. **This is the real opponent.**
- [ ] **B4. First LightGBM** `medium` — single time split (train ≤ 2025-12, val = 2026-01→03), native categoricals, no text yet. *Watch:* Python 3.14 wheels — if `pip install lightgbm` fights you, make a 3.12 venv and move on; do not burn a day on packaging.
- [ ] **⚠️ GATE G1 — B4 beats B3 clearly** — if not, the tabular features aren't carrying signal; fix data/features before touching anything fancier. Routing around this gate is forbidden.

## Phase 2 — Honest evaluation *(target: Sep 21 — the heart of the project)*

Goal: numbers you would defend in an interview without flinching.

- [ ] **E1. Walk-forward harness** `medium` — month-boundary folds (hand-rolled calendar cutoffs are easier to reason about than `TimeSeriesSplit` indices). Report per-fold metric + mean ± spread.
- [ ] **E2. All fitted transforms move inside the fold loop** `hard` — encodings, imputations, vocabularies recomputed per fold from that fold's train only. *Done when the y-scramble test passes:* shuffle `log_price` within train → walk-forward performance must collapse to ≈ B2/B3. If a scrambled model still "wins", something leaks.
- [ ] **E3. Learning curve → fold start** `medium` — train on 1 / 3 / 6 / 12 / 24 months against one fixed val month; find where the curve flattens. *Done when:* fold start chosen **from the curve**, plot saved, `DECISIONS.md` updated. (Closes the "why 2023?" question the right way.)
- [ ] **E4. Expanding vs sliding window** `medium` — volumes are flat, so window row-counts match and the comparison isolates *recency*. Either outcome is a headline finding for the write-up.
- [ ] **E5. Segment report** `easy` — error by price decile, designer volume tier, category, fold; plus % of val rows with a designer unseen in train. This table is what interviewers actually probe.
- [ ] **E6. MLflow on every run** `easy` — params: snapshot filename, row count, fold config, feature list, model params. Metrics: THE metric + segments. From here on, an unlogged run didn't happen.

## Phase 3 — Features *(target: Sep 28)*

Goal: earn error reductions and attribute every one of them.

- [ ] **FE1. Title text features** `medium` — TF-IDF (word + char n-grams, capped vocab) or hashing trick. Measure lift vs Phase-2 baseline on identical folds.
- [ ] **FE2. Structured title extraction** `medium` — year/decade, "vintage", "archive", "grail", "BNWT/DS", measurements-in-title. Cheap, interpretable, often beats raw TF-IDF on niche brands.
- [ ] **FE3. Designer target encoding + shrinkage** `hard` — `(n·group_mean + m·global_mean)/(n + m)`, m ≈ 20–50; below ~5 sales collapse to `other`. Fit per fold.
- [ ] **FE4. Out-of-fold encoding inside train** `hard` — inner K-fold so no row's own price leaks into its designer mean. ~15 lines; the difference between an encoding that generalizes and one that doesn't.
- [ ] **FE5. Importance audit** `easy` — eyeball top-20 by gain. Anything absurdly dominant → suspect leakage; investigate before celebrating.

## Phase 4 — Multimodal *(target: Oct 5)*

Goal: find out what images are worth **before** paying for all of them.

- [ ] **M1. Image inventory** `very easy` — `image_download_status` distribution; do `local_image_path` files actually exist on disk?
- [ ] **M2. CLIP on a 50k subset** `medium` — cover photos only; join embeddings by listing id; evaluate on the same folds.
- [ ] **⚠️ GATE G2 — embeddings move THE metric meaningfully on the subset** — if the lift is marginal, *that is a reportable finding* ("visual signal adds X% over tabular+text") and the full run is officially skipped. Write it up; don't sink the weekend anyway.
- [ ] **M3. Full-corpus run (only if G2 passes)** `hard` — stream URL → decode → embed → drop pixels; never save 800 GB of images. Checkpoint every ~50k with resume. Expect network-bound (8h+ best case, CDN rate limits), not GPU-bound. Store fp16 `.npy` memmap + id index, not 512 Parquet columns.

## Phase 5 — Ship *(target: Oct 12 → faculty emails mid-October)*

- [ ] **S1. README** `easy` — problem, data card, headline table (B2 / B3 / LGBM / +text / +CLIP on identical folds), segment table, honest limitations.
- [ ] **S2. `DECISIONS.md` complete** `easy` — every gate and every F5/F7/B1/E3-class decision present. This file is your interview script.
- [ ] **S3. Fresh-clone reproducibility** `medium` — clone → README steps → headline number reproduces. `data/` stays gitignored; the snapshot ships as a release asset or regenerates via the export script.
- [ ] **S4. Write-up** `medium` — 6–10 pages or a long-form post: dataset, method, walk-forward design, expanding-vs-sliding finding, segment analysis, what images were worth. Then the faculty email from the spec, with this attached.

---

## Working agreements

1. Prediction-time test before any feature ships.
2. Split first, fit second — per fold, no exceptions.
3. `LIMIT 10000` / `.head()` before any full-table operation.
4. Notebooks explore; the moment code works it graduates to `src/` and the notebook imports it back.
5. No relative paths — everything goes through `src/paths.py`.
6. Every run logs snapshot filename + row count at minimum, or it didn't happen.
7. Non-obvious choice → three lines in `DECISIONS.md` the same day.
8. A failed gate changes the plan, never the numbers.

## Parking lot (post-v1 — do not touch before S4)

Re-scrape the Mar→now Grailed gap · Vestiaire integration via `designer_aliases` · cross-platform matching (spec Layer 3a) · arbitrage detector calibrated against structural platform premiums (3b) · residual-based trend/regime detection (3c) · Telegram alerts · seller-stats snapshot-bias study · CatBoost comparison if rare-designer error dominates.

## Progress log

| Date | Shipped | Headline number |
|---|---|---|
| 2026-08-24 | F1 — export script + first 10k snapshot | — |
|  |  |  |
