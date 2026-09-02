# DECISIONS

Rationale log. Date · decision · why · what was rejected.

---

**08-22 — Postgres is the source of truth; training reads only date-stamped Parquet snapshots.**
Live queries make runs unreproducible. Rejected CSV: type loss and emoji/comma fragility in `title`.

**08-23 — Task: predict `sold_price` for an *unlisted* item.**
Matches the sourcing use case. Rejected the active-listing framing — it legitimises `price`, and the model then just learns `sold_price ≈ 0.85 × price`.

**08-23 — Target = `log(sold_price)`. Shipping excluded.**
Heavy tail: median $80, max $35,000. Shipping is one flat seller rate, effectively a pass-through.

**08-23 — Export wide; filter leakage at the `FEATURES` list, not in SQL.**
Keeps reasoning next to the decision, makes re-framing cheap. Allow-list not deny-list: a new column does nothing until deliberately added.

**08-23 — No WHERE filter on export.**
Verified 0 nulls in `sold_price`/`sold_at`, 0 non-positive prices. The intended filter was a no-op.

**08-25 — Scrape order induces coverage bias. ⚠️ OPEN**
Scraped per-designer newest-first, so temporal depth is per-designer: early years over-represent low-volume designers. Confounds E4 (expanding vs sliding) — "more history" and "different designer mix" can't be separated. Must appear as a limitation in the write-up.

**08-25 — Ship end-to-end before further data analysis.**
Every cleaning decision so far is unmeasurable without a baseline. Stopping rule: does this *block* the next step or merely *improve* it? Leakage blocks; collab encoding improves. Exception: the leakage audit does precede training.

**08-26 — `country_of_origin` excluded: schema rollout, not sparsity.**
Populated rate flat ~3.7% through 2025-02, ramps from 2025-03, plateaus ~90% by 2025-09. Column means different things in different folds. Rejected restricting the window to post-rollout — discards 4M rows to save 670k.

**08-26 — `styles` excluded.** ~92% null, mechanism unverified. Revisit as a "populated" boolean.

**08-26 — Null-rate thresholds rejected as a rule.**
`measurement_count` at 55% null means "seller listed no measurements" — real signal. `country_of_origin` at 85% is a rollout. Same percentage, opposite responses. Investigate *why* before deciding.

**08-26 — F7: designer collapse = normalize → drop denylisted → first survivor → `"unknown"`.**
`designer_names` mixes brands with Grailed tags. 500k rows (11.5%) had a denylisted term at position 0; 291,747 (6.7%) have no surviving designer. Denylist sorted by *"does this carry price info a brand field should hold?"* — `Rare`/`Luxury` are seller positioning, `Vintage`/`Band Tees` duplicate category, `MLB`/`Hat Club` are leagues and retailers. `Jordan Brand` kept — real sub-label.
Rejected raw first-element (11.5% wrong brands) and multi-hot (6.3k levels).
Kept `"unknown"` rather than dropping: unbranded is informative, and dropping biases toward well-labelled listings.
Cost: 262 designers appear only in non-first positions, unreachable as primary.

**08-26 — Collab encoding deferred.** 761k rows have 2+ survivors, but that mixes genuine collabs (price above both parents) with associative tagging (price at or below). Unmeasurable without a baseline.

**08-26 — `category_path` unpacked into `path`.**
Consistently two levels (verified: 0 values with a second dot, 10 rows with none). Keeping `bottoms.denim.32` atomic would prevent generalising across the hierarchy. Drops `category_path`, `category_size`, `category_path_size`.

**08-26 — `created_month` only; year excluded.**
Month cycles, so no extrapolation problem. Year is confounded with the scrape bias — it partly encodes which designers were sampled, not a market signal. Kept numeric.
Note: `sold_at − created_at` is unusable — excluding a column doesn't sanitise things derived from it.

**08-26 — Seller features excluded from v1.**
`seller_id` (327k unique) invites memorising sellers. Aggregate stats may be scrape-time snapshots, which partly reflect the future. Item-only baseline also matches the cross-platform goal.

**08-26 — `title` excluded from v1 despite being the strongest signal.**
Precisely so FE1 can measure it. Sequencing, not permanent.

**08-26 — v1 features:** `department`, `category`, `path`, `color`, `condition`, `size`, `location`, `photo_count`, `measurement_count`, `primary_designer`, `created_month`.
Cast categoricals **before** splitting — separate casts produce separate code mappings. Not a leakage violation: assigning codes to strings learns nothing about the target.

**08-26 — Export query bug: missing comma after `photo_count`** parsed as `photo_count AS measurement_count`. Valid SQL, silent failure. Practice: diff the SELECT list against `FEATURES` mechanically.

**08-24 — Walk-forward at monthly grain.**
Yearly conflates accuracy with staleness and gives only ~4 noisy folds. Thinnest full month is ~57k rows.

**08-24 — Fold start comes from a learning curve (E3), not a round year.**

**08-26 — Transformed features are never persisted.**
Parquet holds raw data, code holds transformations. Persisting hides the leakage boundary — once transforms are fold-scoped, that's where an all-data encoding gets saved unnoticed. Exception: CLIP embeddings.

**08-26 — Notebooks import constants from `features.py`, never redefine them.**
A stale `DENYLIST` cell gave 291,815 where the module gave 291,747.

B1: CUT OFF training split is evrth before 2026-01 and validate on 2026-02

---

## Open

| ID | Item | Blocking |
|---|---|---|
| F2 | Re-export: comma fix, `photo_count`, `sold`, `dropped` | **v1** |
| B1 | Pick the metric and defend it | **next** |
| F6 | Why does a sold-listings table have a `sold` boolean? | no |
| — | `sold_at < created_at` same-row check | no |
| — | Top-100 position-0 values — junk not yet in denylist | no |
| — | Designer composition per year (quantify scrape bias) | before E4 |
| — | `color` at 2,913 unique — high for a colour field | no |

**(2026-08-31) — snapshot sold_listings_20260830.parquet, 
train < 2026-02-01, test 2026-02, 57,936 rows. 
Constant = train mean log price. 
{'RMSE': 0.9560050805837702, 'MAE': 0.7625944414030207, 'MAPE': 1.0159937979125413}**

**(2026-08-31) — snapshot sold_listings_20260830.parquet, 
train < 2026-02-01, test 2026-02, 57,936 rows. 
Mean log price by category X primary_designer, fit on train, unmatched 125 test rows filled with global mean - mean of log price from train 
and compared to test log price
{'RMSE': 0.7119398454915198, 'MAE': 0.5444915601807783, 'MAPE': 0.6556966058010049}
if evaluate for sold_price > 30 we get
{'RMSE': 0.659, 'MAPE': 0.491} 
MAPE is unstable because it has enormous errors for cheap items => keep RMSE and MAE from now on**

**(2026-09-01) — snapshot sold_listings_20260830.parquet, 
train < 2026-02-01, test 2026-02, 57,936 rows.
Model trained on 11 features, fit on train, gives ~10% increase in accuracy over category X primary_designer
{'RMSE': 0.6420129540551772, 'MAE': 0.48514603749676366, 'MAPE': 0.5675015915275922}