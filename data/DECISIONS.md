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
{'RMSE': 1.0595973040817848, 'MAE': 0.8372516107034856, 'MAPE': 0.9008734215763111, 'WITHIN_20%': 0.1705675227837614}**

**(2026-08-31) — snapshot sold_listings_20260830.parquet, 
train < 2026-02-01, test 2026-02, 57,936 rows. 
Mean log price by category X primary_designer, fit on train, unmatched 125 test rows filled with global mean - mean of log price from train 
and compared to test log price
{'RMSE': 0.7119398454915198, 'MAE': 0.5444915601807783, 'MAPE': 0.6556966058010049, 'WITHIN_20%': 0.2518986467826567}
if evaluate for sold_price > 30 we get
{'RMSE': 0.659, 'MAPE': 0.491} 
MAPE is unstable because it has enormous errors for cheap items**

**(2026-09-01) — snapshot sold_listings_20260830.parquet, 
train < 2026-02-01, test 2026-02, 57,936 rows.
Model trained on 11 features, fit on train, gives ~10% increase(RMSE metric) in accuracy over category X primary_designer
{'RMSE': 0.6420129540551772, 'MAE': 0.48514603749676366, 'MAPE': 0.5675015915275922, 'WITHIN_20%': 0.2874551228942281}
importances = regressor.feature_importances_ 
importance_type='gain' 
Feature  	        Gain	    Share
primary_designer	7,328,808	58.1%
path	            2,860,361	22.7%
color	            809,460	    6.4%
category		    735,742     5.8%
size	            419,243	    3.3%
location	        97,619	    0.8%
photo_count	        87,382	    0.7%
condition	        38,184	    0.3%
measurement_count	23,005	    0.2%
created_month	    3,785	    0.03%
department	        0	        0%
After scramble test (shuffled = train.with_columns(pl.col('log_sold_price').shuffle(1234)); train_base_lightgbm(shuffled, test))
I got {'RMSE': 1.0595973040817848, 'MAE': 0.8372516107034856, 'MAPE': 0.9008734215763111, 'WITHIN_20%': 0.1705675227837614} = model trained on mean log price
=> No leakages found**

**(2026-09-02) — snapshot sold_listings_20260901.parquet,
Walk forward on 12 months showed that lightgbm model trained on 11 features shows stable metrics. Therefore current features perform uniformly regardless data volume - model trained on 3.5mil rows = model trained on 4.4mil rows
The model has saturated on these eleven features, and additional months add nothing. Therefore further gains must to come from better features.
{'RMSE': 0.6413174388071619, 'MAE': 0.484528442330482, 'MAPE': 0.6063601770185226, 'WITHIN_20%': 0.28332837475996, 'start_fold': datetime.datetime(2025, 2, 1, 0, 0), 'end_fold': datetime.datetime(2025, 3, 1, 0, 0)}
{'RMSE': 0.6445476676183405, 'MAE': 0.48740633762378327, 'MAPE': 0.6384316961340354, 'WITHIN_20%': 0.28124806230390514, 'start_fold': datetime.datetime(2025, 3, 1, 0, 0), 'end_fold': datetime.datetime(2025, 4, 1, 0, 0)}
{'RMSE': 0.6449838463944545, 'MAE': 0.4860430438375963, 'MAPE': 0.6235460093397521, 'WITHIN_20%': 0.2847671868953953, 'start_fold': datetime.datetime(2025, 4, 1, 0, 0), 'end_fold': datetime.datetime(2025, 5, 1, 0, 0)}
{'RMSE': 0.6500448178763011, 'MAE': 0.48630176674650466, 'MAPE': 0.64156644658406, 'WITHIN_20%': 0.2854253389531925, 'start_fold': datetime.datetime(2025, 5, 1, 0, 0), 'end_fold': datetime.datetime(2025, 6, 1, 0, 0)}
{'RMSE': 0.6400537694367767, 'MAE': 0.48360656616090253, 'MAPE': 0.6099621397510896, 'WITHIN_20%': 0.28697362268614024, 'start_fold': datetime.datetime(2025, 6, 1, 0, 0), 'end_fold': datetime.datetime(2025, 7, 1, 0, 0)}
{'RMSE': 0.6434049627367535, 'MAE': 0.4834992482370238, 'MAPE': 0.6122435033827054, 'WITHIN_20%': 0.2907307477604402, 'start_fold': datetime.datetime(2025, 7, 1, 0, 0), 'end_fold': datetime.datetime(2025, 8, 1, 0, 0)}
{'RMSE': 0.6661330525854595, 'MAE': 0.48986574778952047, 'MAPE': 0.7274720134179843, 'WITHIN_20%': 0.28626908140116125, 'start_fold': datetime.datetime(2025, 8, 1, 0, 0), 'end_fold': datetime.datetime(2025, 9, 1, 0, 0)}
{'RMSE': 0.6393158186227345, 'MAE': 0.4828647174381569, 'MAPE': 0.6120807070402131, 'WITHIN_20%': 0.2865749703817147, 'start_fold': datetime.datetime(2025, 9, 1, 0, 0), 'end_fold': datetime.datetime(2025, 10, 1, 0, 0)}
{'RMSE': 0.636527744641391, 'MAE': 0.4837276380934099, 'MAPE': 0.588875304652317, 'WITHIN_20%': 0.2822684299220245, 'start_fold': datetime.datetime(2025, 10, 1, 0, 0), 'end_fold': datetime.datetime(2025, 11, 1, 0, 0)}
{'RMSE': 0.6447891690399983, 'MAE': 0.4875499580737756, 'MAPE': 0.6108689819820472, 'WITHIN_20%': 0.2811882021290024, 'start_fold': datetime.datetime(2025, 11, 1, 0, 0), 'end_fold': datetime.datetime(2025, 12, 1, 0, 0)}
{'RMSE': 0.6365924477914696, 'MAE': 0.4815015030188194, 'MAPE': 0.573992819409147, 'WITHIN_20%': 0.2858257862227665, 'start_fold': datetime.datetime(2025, 12, 1, 0, 0), 'end_fold': datetime.datetime(2026, 1, 1, 0, 0)}
{'RMSE': 0.6501872636503928, 'MAE': 0.4908906388413077, 'MAPE': 0.5988557183508603, 'WITHIN_20%': 0.2828154439692556, 'start_fold': datetime.datetime(2026, 1, 1, 0, 0), 'end_fold': datetime.datetime(2026, 2, 1, 0, 0)}
{'RMSE': 0.6420129540551772, 'MAE': 0.48514603749676366, 'MAPE': 0.5675015915275922, 'WITHIN_20%': 0.2874551228942281, 'start_fold': datetime.datetime(2026, 2, 1, 0, 0), 'end_fold': datetime.datetime(2026, 3, 1, 0, 0)}

Useful flags AW,FW/ SS or A/W, F/W, S/S and then year 00 98 19 and can be 2016 1998 as well or year and season can be vice versa etc. ALSO there can be only a year 05 16 98 1996 etc. OR it acn be spring summer or fall winter; 
2000s; Archive; 90s 90's; 00s 00's; Y2K; vintage?; Selvedge; Rare?; streetwear, grails, street, designer, japan, france; Core; .925(metal probe)?; by Hedi Slimane, Raf Simons, Richard Avedon etc.; Designer; reworked; patchwork; patch; runway; 