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

**(2026-09-03) — snapshot sold_listings_20260830.parquet, 
0 25 3665 {'RMSE': 1.1386472731218116, 'MAE': 1.0100150885952848, 'MAPE': 2.344048967966982, 'WITHIN_20%': 0.01937244201909959}
25 50 8997 {'RMSE': 0.5840512572065008, 'MAE': 0.4600517383358808, 'MAPE': 0.7013160265774078, 'WITHIN_20%': 0.2641991775036123}
50 100 14460 {'RMSE': 0.45863319912394324, 'MAE': 0.3603767283713242, 'MAPE': 0.4407227123817305, 'WITHIN_20%': 0.3503457814661134}
100 250 18688 {'RMSE': 0.5093028370875875, 'MAE': 0.3958928647183415, 'MAPE': 0.34904299320315624, 'WITHIN_20%': 0.3402183219178082}
250 500 7993 {'RMSE': 0.6817853047899195, 'MAE': 0.5278148851399006, 'MAPE': 0.37865485881628386, 'WITHIN_20%': 0.2591017139997498}
500 1000 3023 {'RMSE': 0.8521827290886851, 'MAE': 0.6792325080071775, 'MAPE': 0.43805894168123566, 'WITHIN_20%': 0.19219318557724116}
1000 1000000000 1110 {'RMSE': 1.3564811208435803, 'MAE': 1.175165485831758, 'MAPE': 0.6262831526502981, 'WITHIN_20%': 0.04234234234234234}
The model regresses to the middle. It's excellent at $50–250 and terrible outside it. Under $25 it predicts too high; over $1000 it predicts too low. That's the classic shape of a model that can identify the category and brand but nothing item-specific - so it predicts the designer's typical price and misses whenever an item is unusually cheap or unusually expensive for its brand.

**(2026-09-03) — snapshot sold_listings_20260830.parquet, added season and year flags
{'RMSE': 0.6396, 'MAE': 0.4841, 'MAPE': 0.6059, 'WITHIN_20%': 0.2836},
{'RMSE': 0.6413, 'MAE': 0.4847, 'MAPE': 0.6361, 'WITHIN_20%': 0.2842},
{'RMSE': 0.6426, 'MAE': 0.4849, 'MAPE': 0.6219, 'WITHIN_20%': 0.2833},
{'RMSE': 0.6489, 'MAE': 0.4854, 'MAPE': 0.6402, 'WITHIN_20%': 0.2856},
{'RMSE': 0.6370, 'MAE': 0.4818, 'MAPE': 0.6081, 'WITHIN_20%': 0.2856},
{'RMSE': 0.6415, 'MAE': 0.4824, 'MAPE': 0.6118, 'WITHIN_20%': 0.2905},
{'RMSE': 0.6658, 'MAE': 0.4885, 'MAPE': 0.7490, 'WITHIN_20%': 0.2878},
{'RMSE': 0.6382, 'MAE': 0.4830, 'MAPE': 0.6109, 'WITHIN_20%': 0.2846},
{'RMSE': 0.6329, 'MAE': 0.4811, 'MAPE': 0.5850, 'WITHIN_20%': 0.2855},
{'RMSE': 0.6413, 'MAE': 0.4851, 'MAPE': 0.6074, 'WITHIN_20%': 0.2833},
{'RMSE': 0.6345, 'MAE': 0.4805, 'MAPE': 0.5727, 'WITHIN_20%': 0.2877},
{'RMSE': 0.6453, 'MAE': 0.4874, 'MAPE': 0.5961, 'WITHIN_20%': 0.2849},
{'RMSE': 0.6391, 'MAE': 0.4838, 'MAPE': 0.5669, 'WITHIN_20%': 0.2860},

**(2026-09-03) — snapshot sold_listings_20260830.parquet, added TF-IDF
{'RMSE': 0.6215364955537139, 'MAE': 0.4697046774301541, 'MAPE': 0.5869491702666767, 'WITHIN_20%': 0.2912665999513158, 'start_fold': datetime.datetime(2025, 2, 1, 0, 0), 'end_fold': datetime.datetime(2025, 3, 1, 0, 0)}
{'RMSE': 0.6242642589726524, 'MAE': 0.47175133051798496, 'MAPE': 0.6118928473812753, 'WITHIN_20%': 0.28897404417327033, 'start_fold': datetime.datetime(2025, 3, 1, 0, 0), 'end_fold': datetime.datetime(2025, 4, 1, 0, 0)}
{'RMSE': 0.6273592584600785, 'MAE': 0.4729182847841299, 'MAPE': 0.6033126611555905, 'WITHIN_20%': 0.29108732103701795, 'start_fold': datetime.datetime(2025, 4, 1, 0, 0), 'end_fold': datetime.datetime(2025, 5, 1, 0, 0)}
{'RMSE': 0.6303082663458891, 'MAE': 0.47195444772879835, 'MAPE': 0.6174074961752933, 'WITHIN_20%': 0.29415525948351234, 'start_fold': datetime.datetime(2025, 5, 1, 0, 0), 'end_fold': datetime.datetime(2025, 6, 1, 0, 0)}
{'RMSE': 0.6195208286711729, 'MAE': 0.46850046925033123, 'MAPE': 0.5873654734638551, 'WITHIN_20%': 0.2919382787358693, 'start_fold': datetime.datetime(2025, 6, 1, 0, 0), 'end_fold': datetime.datetime(2025, 7, 1, 0, 0)}
{'RMSE': 0.6250177218122765, 'MAE': 0.4704143415624449, 'MAPE': 0.5957079718842206, 'WITHIN_20%': 0.2949972088572264, 'start_fold': datetime.datetime(2025, 7, 1, 0, 0), 'end_fold': datetime.datetime(2025, 8, 1, 0, 0)}
{'RMSE': 0.6467133594925586, 'MAE': 0.47606069483908026, 'MAPE': 0.7059783019185839, 'WITHIN_20%': 0.2936645262173005, 'start_fold': datetime.datetime(2025, 8, 1, 0, 0), 'end_fold': datetime.datetime(2025, 9, 1, 0, 0)}
{'RMSE': 0.6200340189350022, 'MAE': 0.4690793184684533, 'MAPE': 0.5940901589621924, 'WITHIN_20%': 0.29109717687173114, 'start_fold': datetime.datetime(2025, 9, 1, 0, 0), 'end_fold': datetime.datetime(2025, 10, 1, 0, 0)}
{'RMSE': 0.616968907784633, 'MAE': 0.46898870723016406, 'MAPE': 0.5698721254454171, 'WITHIN_20%': 0.28865213789951727, 'start_fold': datetime.datetime(2025, 10, 1, 0, 0), 'end_fold': datetime.datetime(2025, 11, 1, 0, 0)}
{'RMSE': 0.6237156178555521, 'MAE': 0.47147639011343306, 'MAPE': 0.5901432245060674, 'WITHIN_20%': 0.2910478113911446, 'start_fold': datetime.datetime(2025, 11, 1, 0, 0), 'end_fold': datetime.datetime(2025, 12, 1, 0, 0)}
{'RMSE': 0.6147903973566918, 'MAE': 0.46574709383572255, 'MAPE': 0.5515233257481711, 'WITHIN_20%': 0.2936308218164202, 'start_fold': datetime.datetime(2025, 12, 1, 0, 0), 'end_fold': datetime.datetime(2026, 1, 1, 0, 0)}
{'RMSE': 0.6292120960928985, 'MAE': 0.4751542590929224, 'MAPE': 0.5805297973287331, 'WITHIN_20%': 0.2911295869844783, 'start_fold': datetime.datetime(2026, 1, 1, 0, 0), 'end_fold': datetime.datetime(2026, 2, 1, 0, 0)}
{'RMSE': 0.6195145531763402, 'MAE': 0.46839368148962274, 'MAPE': 0.5468081496715583, 'WITHIN_20%': 0.2955330019331676, 'start_fold': datetime.datetime(2026, 2, 1, 0, 0), 'end_fold': datetime.datetime(2026, 3, 1, 0, 0)}

**(2026-09-03) — snapshot sold_listings_20260830.parquet, TF-IDF for archive 
FE1 (2026-09-05) — TF-IDF on lowercased titles, thirteen folds, snapshot sold_listings_20260901.parquet.
Aggregate: 0.6421 → 0.6249 (2.7%). Archive (~4k rows/fold): 0.6190 → 0.6103 (1.4%).
min_df=5 vs default: no measurable change.
Title features helped mass-market roughly twice as much as archive — contrary to the hypothesis that archive titles carry more signal. Likely because mass-market vocabulary is consistent and repeated while archive phrasing is idiosyncratic. 
Motivates sentence embeddings over bag-of-words for the archive segment.
{'RMSE': 0.6258598123171348, 'MAE': 0.4731234524135664, 'MAPE': 0.6113076442782884, 'WITHIN_20%': 0.28547809313860567, 'start_fold': datetime.datetime(2025, 2, 1, 0, 0), 'end_fold': datetime.datetime(2025, 3, 1, 0, 0), 'len_sample': 3629}
{'RMSE': 0.5975514543441446, 'MAE': 0.45894558253016726, 'MAPE': 0.492273000857255, 'WITHIN_20%': 0.2940294778915813, 'start_fold': datetime.datetime(2025, 3, 1, 0, 0), 'end_fold': datetime.datetime(2025, 4, 1, 0, 0), 'len_sample': 4003}
{'RMSE': 0.6250285896789896, 'MAE': 0.47565553823410744, 'MAPE': 0.5187707825220296, 'WITHIN_20%': 0.2725274725274725, 'start_fold': datetime.datetime(2025, 4, 1, 0, 0), 'end_fold': datetime.datetime(2025, 5, 1, 0, 0), 'len_sample': 4095}
{'RMSE': 0.6082777395402943, 'MAE': 0.46907900751688114, 'MAPE': 0.48705624025225275, 'WITHIN_20%': 0.28621991505427086, 'start_fold': datetime.datetime(2025, 5, 1, 0, 0), 'end_fold': datetime.datetime(2025, 6, 1, 0, 0), 'len_sample': 4238}
{'RMSE': 0.5944070805796561, 'MAE': 0.4532496188094879, 'MAPE': 0.47449560765566595, 'WITHIN_20%': 0.31713688610240337, 'start_fold': datetime.datetime(2025, 6, 1, 0, 0), 'end_fold': datetime.datetime(2025, 7, 1, 0, 0), 'len_sample': 3828}
{'RMSE': 0.6034637455152413, 'MAE': 0.45742103418313773, 'MAPE': 0.5027112406827647, 'WITHIN_20%': 0.30473876063183475, 'start_fold': datetime.datetime(2025, 7, 1, 0, 0), 'end_fold': datetime.datetime(2025, 8, 1, 0, 0), 'len_sample': 4115}
{'RMSE': 0.5877503841740013, 'MAE': 0.4513342085163575, 'MAPE': 0.48466865923928326, 'WITHIN_20%': 0.29620014160962943, 'start_fold': datetime.datetime(2025, 8, 1, 0, 0), 'end_fold': datetime.datetime(2025, 9, 1, 0, 0), 'len_sample': 4237}
{'RMSE': 0.6004010646131455, 'MAE': 0.4604113503432408, 'MAPE': 0.536655831848975, 'WITHIN_20%': 0.2881133560745211, 'start_fold': datetime.datetime(2025, 9, 1, 0, 0), 'end_fold': datetime.datetime(2025, 10, 1, 0, 0), 'len_sample': 3811}
{'RMSE': 0.5945287194064492, 'MAE': 0.4559085761591517, 'MAPE': 0.473879153896208, 'WITHIN_20%': 0.2932131495227996, 'start_fold': datetime.datetime(2025, 10, 1, 0, 0), 'end_fold': datetime.datetime(2025, 11, 1, 0, 0), 'len_sample': 3772}
{'RMSE': 0.6322811674631621, 'MAE': 0.4780569354973492, 'MAPE': 0.5610394443828539, 'WITHIN_20%': 0.2873162222775978, 'start_fold': datetime.datetime(2025, 11, 1, 0, 0), 'end_fold': datetime.datetime(2025, 12, 1, 0, 0), 'len_sample': 4013}
{'RMSE': 0.619236857843588, 'MAE': 0.4671207658856183, 'MAPE': 0.5119358419013337, 'WITHIN_20%': 0.30018717828731867, 'start_fold': datetime.datetime(2025, 12, 1, 0, 0), 'end_fold': datetime.datetime(2026, 1, 1, 0, 0), 'len_sample': 4274}
{'RMSE': 0.6197732807524661, 'MAE': 0.47688635113134215, 'MAPE': 0.4813561803901579, 'WITHIN_20%': 0.2817265443266994, 'start_fold': datetime.datetime(2026, 1, 1, 0, 0), 'end_fold': datetime.datetime(2026, 2, 1, 0, 0), 'len_sample': 3869}
{'RMSE': 0.6150776067209375, 'MAE': 0.46642006393154306, 'MAPE': 0.515834759804623, 'WITHIN_20%': 0.2905405405405405, 'start_fold': datetime.datetime(2026, 2, 1, 0, 0), 'end_fold': datetime.datetime(2026, 3, 1, 0, 0), 'len_sample': 3256}

This is 13 folds TF-IDF for the whole dataset
{'RMSE': 0.6216364593458511, 'MAE': 0.4697496914822316, 'MAPE': 0.5882300002714856, 'WITHIN_20%': 0.290928515403132, 'start_fold': datetime.datetime(2025, 2, 1, 0, 0), 'end_fold': datetime.datetime(2025, 3, 1, 0, 0), 'len_sample': 0}
{'RMSE': 0.6230431635842602, 'MAE': 0.4702973146562659, 'MAPE': 0.6103286659883335, 'WITHIN_20%': 0.2911566650545035, 'start_fold': datetime.datetime(2025, 3, 1, 0, 0), 'end_fold': datetime.datetime(2025, 4, 1, 0, 0), 'len_sample': 0}
{'RMSE': 0.6260889336816019, 'MAE': 0.47212765849372823, 'MAPE': 0.6030825242779766, 'WITHIN_20%': 0.2919515026441378, 'start_fold': datetime.datetime(2025, 4, 1, 0, 0), 'end_fold': datetime.datetime(2025, 5, 1, 0, 0), 'len_sample': 0}
{'RMSE': 0.6317994530894905, 'MAE': 0.4734126383024927, 'MAPE': 0.6185438423309798, 'WITHIN_20%': 0.2915976146046742, 'start_fold': datetime.datetime(2025, 5, 1, 0, 0), 'end_fold': datetime.datetime(2025, 6, 1, 0, 0), 'len_sample': 0}
{'RMSE': 0.6195532408959921, 'MAE': 0.4681781937526619, 'MAPE': 0.5871517026538751, 'WITHIN_20%': 0.2940974227796573, 'start_fold': datetime.datetime(2025, 6, 1, 0, 0), 'end_fold': datetime.datetime(2025, 7, 1, 0, 0), 'len_sample': 0}
{'RMSE': 0.6254999048547237, 'MAE': 0.47113741672757564, 'MAPE': 0.5971019651450288, 'WITHIN_20%': 0.29313644700816077, 'start_fold': datetime.datetime(2025, 7, 1, 0, 0), 'end_fold': datetime.datetime(2025, 8, 1, 0, 0), 'len_sample': 0}
{'RMSE': 0.6467957974248718, 'MAE': 0.4760283285725239, 'MAPE': 0.7053150245180623, 'WITHIN_20%': 0.2939977698311993, 'start_fold': datetime.datetime(2025, 8, 1, 0, 0), 'end_fold': datetime.datetime(2025, 9, 1, 0, 0), 'len_sample': 0}
{'RMSE': 0.619361627955936, 'MAE': 0.46856699594671486, 'MAPE': 0.5945068904441833, 'WITHIN_20%': 0.2907937700465224, 'start_fold': datetime.datetime(2025, 9, 1, 0, 0), 'end_fold': datetime.datetime(2025, 10, 1, 0, 0), 'len_sample': 0}
{'RMSE': 0.6154278240685492, 'MAE': 0.4676189637683264, 'MAPE': 0.5673075955531527, 'WITHIN_20%': 0.2897946359715518, 'start_fold': datetime.datetime(2025, 10, 1, 0, 0), 'end_fold': datetime.datetime(2025, 11, 1, 0, 0), 'len_sample': 0}
{'RMSE': 0.6230117597028069, 'MAE': 0.4711719937009026, 'MAPE': 0.5889142357778738, 'WITHIN_20%': 0.2908374356600889, 'start_fold': datetime.datetime(2025, 11, 1, 0, 0), 'end_fold': datetime.datetime(2025, 12, 1, 0, 0), 'len_sample': 0}
{'RMSE': 0.6150895676869743, 'MAE': 0.46546753334979796, 'MAPE': 0.5527525429748924, 'WITHIN_20%': 0.29655098168508026, 'start_fold': datetime.datetime(2025, 12, 1, 0, 0), 'end_fold': datetime.datetime(2026, 1, 1, 0, 0), 'len_sample': 0}
{'RMSE': 0.6291217430248364, 'MAE': 0.4748349807286726, 'MAPE': 0.58189633815876, 'WITHIN_20%': 0.29111463348984656, 'start_fold': datetime.datetime(2026, 1, 1, 0, 0), 'end_fold': datetime.datetime(2026, 2, 1, 0, 0), 'len_sample': 0}
{'RMSE': 0.6196518981670374, 'MAE': 0.4685358458811681, 'MAPE': 0.546957102799652, 'WITHIN_20%': 0.2956883457608395, 'start_fold': datetime.datetime(2026, 2, 1, 0, 0), 'end_fold': datetime.datetime(2026, 3, 1, 0, 0), 'len_sample': 0}

Top 100 gain features
 feature           ┆ importance    │
│ ---               ┆ ---           │
│ str               ┆ f64           │
╞═══════════════════╪═══════════════╡
│ primary_designer  ┆ 5.9576e6      │
│ path              ┆ 2.8050e6      │
│ vintage           ┆ 1.2325e6      │
│ category          ┆ 637606.401733 │
│ condition         ┆ 511973.01297  │
│ location          ┆ 412054.815796 │
│ y2k               ┆ 119161.049316 │
│ supreme           ┆ 109697.873138 │
│ photo_count       ┆ 96474.590805  │
│ season            ┆ 92251.621399  │
│ color             ┆ 59001.597168  │
│ year              ┆ 57489.641144  │
│ nike              ┆ 53432.317566  │
│ sticker           ┆ 48034.702164  │
│ balenciaga        ┆ 28391.688965  │
│ carhartt          ┆ 25542.203186  │
│ rick              ┆ 24485.447906  │
│ off               ┆ 19359.619934  │
│ yeezy             ┆ 17922.509949  │
│ mens              ┆ 17280.203003  │
│ men               ┆ 17009.44104   │
│ size              ┆ 16651.102997  │
│ hearts            ┆ 16161.043121  │
│ measurement_count ┆ 15467.692017  │
│ uniqlo            ┆ 15011.886993  │
│ adidas            ┆ 14955.880035  │
│ graphic           ┆ 14797.287231  │
│ jordan            ┆ 13002.839874  │
│ rare              ┆ 12553.461914  │
│ bundle            ┆ 11819.069946  │
│ windbreaker       ┆ 11435.780167  │
│ bape              ┆ 11096.1521    │
│ gap               ┆ 10801.57486   │
│ tote              ┆ 10756.891998  │
│ ralph             ┆ 10085.860046  │
│ levi              ┆ 9983.997131   │
│ dickies           ┆ 9692.279907   │
│ gucci             ┆ 9634.384979   │
│ lastdrop          ┆ 9509.322998   │
│ converse          ┆ 8870.399963   │
│ prada             ┆ 8818.059998   │
│ sweatshirt        ┆ 8644.058105   │
│ shirt             ┆ 8247.13913    │
│ dunk              ┆ 7931.638062   │
│ double            ┆ 7757.771027   │
│ fit               ┆ 7364.856934   │
│ exclusive         ┆ 6715.089996   │
│ travis            ┆ 6692.965942   │
│ margiela          ┆ 6341.061035   │
│ detroit           ┆ 6063.064926   │
│ stussy            ┆ 6051.795013   │
│ shoes             ┆ 6022.407013   │
│ 600               ┆ 5838.666046   │
│ hanes             ┆ 5582.842102   │
│ style             ┆ 5539.214081   │
│ kith              ┆ 5391.056      │
│ crewneck          ┆ 5160.693909   │
│ runway            ┆ 4939.087921   │
│ box               ┆ 4938.186066   │
│ 5ml               ┆ 4829.715057   │
│ polo              ┆ 4611.140991   │
│ dior              ┆ 4430.003021   │
│ champion          ┆ 4279.941956   │
│ hoodie            ┆ 4270.829956   │
│ button            ┆ 4242.518982   │
│ pair              ┆ 4110.168976   │
│ zara              ┆ 4024.968994   │
│ vans              ┆ 3785.5        │
│ grail             ┆ 3574.045959   │
│ island            ┆ 3531.400085   │
│ beanie            ┆ 3381.334961   │
│ stickers          ┆ 3375.004028   │
│ kapital           ┆ 3225.684082   │
│ rug               ┆ 3219.761017   │
│ og                ┆ 3198.073029   │
│ deck              ┆ 3156.675049   │
│ pin               ┆ 3125.111084   │
│ oblique           ┆ 3040.144043   │
│ bonner            ┆ 2996.321991   │
│ 2021m             ┆ 2808.072052   │
│ scott             ┆ 2800.310974   │
│ shark             ┆ 2741.391998   │
│ burberry          ┆ 2708.505981   │
│ socks             ┆ 2609.080078   │
│ casual            ┆ 2536.864929   │
│ hysteric          ┆ 2386.151001   │
│ blank             ┆ 2380.225037   │
│ lock              ┆ 2287.535004   │
│ monogram          ┆ 2276.356995   │
│ sb                ┆ 2216.619995   │
│ tee               ┆ 2172.127991   │
│ gallery           ┆ 2156.563049   │
│ vetements         ┆ 2148.175049   │
│ sleeve            ┆ 2105.864014   │
│ reebok            ┆ 2083.643036   │
│ backpack          ┆ 2061.941986   │
│ puma              ┆ 2061.35498    │
│ streetwear        ┆ 1995.506042   │
│ north             ┆ 1933.468964   │
│ chuck             ┆ 1809.018005   │
 