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

Embeddings coverage 09/19/2026 - 99.7%
4331899
4345274
ok,sum() / embbeds length 

.8980160115951862 - PCA 256(was 512) components at 90% retained variance 
(3354978, 60000) (3354978, 256) (73946, 60000) (73946, 256)
13 Folds with v32 embeddings - slightly worse performance
{'RMSE': 0.6214677856051926, 'MAE': 0.4711154136650995, 'MAPE': 0.5906452399825808, 'WITHIN_20%': 0.28898114840559325, 'start_fold': datetime.datetime(2025, 2, 1, 0, 0), 'end_fold': datetime.datetime(2025, 3, 1, 0, 0), 'len_sample': 73946}
{'RMSE': 0.6233711482909061, 'MAE': 0.4718708350682412, 'MAPE': 0.6172573786817657, 'WITHIN_20%': 0.28839118518794105, 'start_fold': datetime.datetime(2025, 3, 1, 0, 0), 'end_fold': datetime.datetime(2025, 4, 1, 0, 0), 'len_sample': 80637}
{'RMSE': 0.6271672807868941, 'MAE': 0.4752756906672267, 'MAPE': 0.6086526113830611, 'WITHIN_20%': 0.28542499677544175, 'start_fold': datetime.datetime(2025, 4, 1, 0, 0), 'end_fold': datetime.datetime(2025, 5, 1, 0, 0), 'len_sample': 77530}
{'RMSE': 0.6319331736795601, 'MAE': 0.47456875493204903, 'MAPE': 0.6220715294940503, 'WITHIN_20%': 0.28974462698837317, 'start_fold': datetime.datetime(2025, 5, 1, 0, 0), 'end_fold': datetime.datetime(2025, 6, 1, 0, 0), 'len_sample': 76633}
{'RMSE': 0.6209270774995651, 'MAE': 0.471099010339403, 'MAPE': 0.5924205935801646, 'WITHIN_20%': 0.28867893390543775, 'start_fold': datetime.datetime(2025, 6, 1, 0, 0), 'end_fold': datetime.datetime(2025, 7, 1, 0, 0), 'len_sample': 72714}
{'RMSE': 0.6271224146279661, 'MAE': 0.4742880061709387, 'MAPE': 0.605115966890599, 'WITHIN_20%': 0.2878598580504532, 'start_fold': datetime.datetime(2025, 7, 1, 0, 0), 'end_fold': datetime.datetime(2025, 8, 1, 0, 0), 'len_sample': 75238}
{'RMSE': 0.6540749657699467, 'MAE': 0.4779513591128111, 'MAPE': 0.7959788401986087, 'WITHIN_20%': 0.290652516630138, 'start_fold': datetime.datetime(2025, 8, 1, 0, 0), 'end_fold': datetime.datetime(2025, 9, 1, 0, 0), 'len_sample': 78021}
{'RMSE': 0.6196638617372512, 'MAE': 0.4698621444486044, 'MAPE': 0.599403101593989, 'WITHIN_20%': 0.29109717687173114, 'start_fold': datetime.datetime(2025, 9, 1, 0, 0), 'end_fold': datetime.datetime(2025, 10, 1, 0, 0), 'len_sample': 69214}
{'RMSE': 0.6169743448030733, 'MAE': 0.47040264853283104, 'MAPE': 0.5755827279278753, 'WITHIN_20%': 0.2861529233669418, 'start_fold': datetime.datetime(2025, 10, 1, 0, 0), 'end_fold': datetime.datetime(2025, 11, 1, 0, 0), 'len_sample': 70022}
{'RMSE': 0.6243352666872197, 'MAE': 0.47409999673300013, 'MAPE': 0.5947097952525807, 'WITHIN_20%': 0.28584451830970115, 'start_fold': datetime.datetime(2025, 11, 1, 0, 0), 'end_fold': datetime.datetime(2025, 12, 1, 0, 0), 'len_sample': 71301}
{'RMSE': 0.6152240752580589, 'MAE': 0.46799353419824874, 'MAPE': 0.5569710643361546, 'WITHIN_20%': 0.2913835098437647, 'start_fold': datetime.datetime(2025, 12, 1, 0, 0), 'end_fold': datetime.datetime(2026, 1, 1, 0, 0), 'len_sample': 74311}
{'RMSE': 0.6293001190861, 'MAE': 0.4767746660656404, 'MAPE': 0.5859739903245935, 'WITHIN_20%': 0.2859706313365433, 'start_fold': datetime.datetime(2026, 1, 1, 0, 0), 'end_fold': datetime.datetime(2026, 2, 1, 0, 0), 'len_sample': 66874}
{'RMSE': 0.6198643922762364, 'MAE': 0.47118685990071124, 'MAPE': 0.5516293307805444, 'WITHIN_20%': 0.290907207953604, 'start_fold': datetime.datetime(2026, 2, 1, 0, 0), 'end_fold': datetime.datetime(2026, 3, 1, 0, 0), 'len_sample': 57936}

13 Folds with fashion clip embeddings better overall and 15% better on seen titles
0.9134308612717728
(3354978, 60000) (3354978, 256) (73946, 60000) (73946, 256)
=== All ===
{'RMSE': 0.6186929183899037, 'MAE': 0.46939459081509644, 'MAPE': 0.5861029522385595, 'WITHIN_20%': 0.2886836340031915, 'start_fold': datetime.datetime(2025, 2, 1, 0, 0), 'end_fold': datetime.datetime(2025, 3, 1, 0, 0), 'len_sample': 73946}
{'RMSE': 0.6215454398176241, 'MAE': 0.47057341153557253, 'MAPE': 0.6162623953675486, 'WITHIN_20%': 0.288403586442948, 'start_fold': datetime.datetime(2025, 3, 1, 0, 0), 'end_fold': datetime.datetime(2025, 4, 1, 0, 0), 'len_sample': 80637}
{'RMSE': 0.624279459331536, 'MAE': 0.47312994073371584, 'MAPE': 0.6043945074005866, 'WITHIN_20%': 0.28573455436605183, 'start_fold': datetime.datetime(2025, 4, 1, 0, 0), 'end_fold': datetime.datetime(2025, 5, 1, 0, 0), 'len_sample': 77530}
{'RMSE': 0.6299672598500254, 'MAE': 0.47370567683196657, 'MAPE': 0.6210767054718139, 'WITHIN_20%': 0.2893401015228426, 'start_fold': datetime.datetime(2025, 5, 1, 0, 0), 'end_fold': datetime.datetime(2025, 6, 1, 0, 0), 'len_sample': 76633}
{'RMSE': 0.6184690352136869, 'MAE': 0.4693814460196838, 'MAPE': 0.5902044151225899, 'WITHIN_20%': 0.28905025167092996, 'start_fold': datetime.datetime(2025, 6, 1, 0, 0), 'end_fold': datetime.datetime(2025, 7, 1, 0, 0), 'len_sample': 72714}
{'RMSE': 0.6232037746717387, 'MAE': 0.4708434406546391, 'MAPE': 0.5981387079417213, 'WITHIN_20%': 0.2927510034822829, 'start_fold': datetime.datetime(2025, 7, 1, 0, 0), 'end_fold': datetime.datetime(2025, 8, 1, 0, 0), 'len_sample': 75238}
{'RMSE': 0.6546309553858985, 'MAE': 0.4784885185949583, 'MAPE': 0.7980232715203364, 'WITHIN_20%': 0.2903064559541662, 'start_fold': datetime.datetime(2025, 8, 1, 0, 0), 'end_fold': datetime.datetime(2025, 9, 1, 0, 0), 'len_sample': 78021}
{'RMSE': 0.6181254118182223, 'MAE': 0.46902237175545525, 'MAPE': 0.5986368798625674, 'WITHIN_20%': 0.2912272083682492, 'start_fold': datetime.datetime(2025, 9, 1, 0, 0), 'end_fold': datetime.datetime(2025, 10, 1, 0, 0), 'len_sample': 69214}
{'RMSE': 0.6147964966448081, 'MAE': 0.46893926060170127, 'MAPE': 0.5722467616168857, 'WITHIN_20%': 0.2863814229813487, 'start_fold': datetime.datetime(2025, 10, 1, 0, 0), 'end_fold': datetime.datetime(2025, 11, 1, 0, 0), 'len_sample': 70022}
{'RMSE': 0.622757305393384, 'MAE': 0.4727988716184981, 'MAPE': 0.594096140872196, 'WITHIN_20%': 0.28651772064907927, 'start_fold': datetime.datetime(2025, 11, 1, 0, 0), 'end_fold': datetime.datetime(2025, 12, 1, 0, 0), 'len_sample': 71301}
{'RMSE': 0.6126128226486021, 'MAE': 0.4657666803146876, 'MAPE': 0.5546577342957723, 'WITHIN_20%': 0.2916122781284063, 'start_fold': datetime.datetime(2025, 12, 1, 0, 0), 'end_fold': datetime.datetime(2026, 1, 1, 0, 0), 'len_sample': 74311}
{'RMSE': 0.6267865571966222, 'MAE': 0.47448296522781935, 'MAPE': 0.5814486491341269, 'WITHIN_20%': 0.29180249424290455, 'start_fold': datetime.datetime(2026, 1, 1, 0, 0), 'end_fold': datetime.datetime(2026, 2, 1, 0, 0), 'len_sample': 66874}
{'RMSE': 0.6162691529377206, 'MAE': 0.4681293023676557, 'MAPE': 0.548116933706242, 'WITHIN_20%': 0.29303024026512015, 'start_fold': datetime.datetime(2026, 2, 1, 0, 0), 'end_fold': datetime.datetime(2026, 3, 1, 0, 0), 'len_sample': 57936}

=== Archive ===
{'RMSE': 0.6247104129213242, 'MAE': 0.4789972656399767, 'MAPE': 0.5568987055831608, 'WITHIN_20%': 0.27087351887572336, 'start_fold': datetime.datetime(2025, 2, 1, 0, 0), 'end_fold': datetime.datetime(2025, 3, 1, 0, 0), 'len_sample': 3629}
{'RMSE': 0.6002159281246847, 'MAE': 0.46645366838171926, 'MAPE': 0.4687542397003046, 'WITHIN_20%': 0.2720459655258556, 'start_fold': datetime.datetime(2025, 3, 1, 0, 0), 'end_fold': datetime.datetime(2025, 4, 1, 0, 0), 'len_sample': 4003}
{'RMSE': 0.6232539800003747, 'MAE': 0.4774466289552669, 'MAPE': 0.500974450592855, 'WITHIN_20%': 0.2754578754578755, 'start_fold': datetime.datetime(2025, 4, 1, 0, 0), 'end_fold': datetime.datetime(2025, 5, 1, 0, 0), 'len_sample': 4095}
{'RMSE': 0.6057105838805086, 'MAE': 0.468924389195205, 'MAPE': 0.46859153492475175, 'WITHIN_20%': 0.27701746106654085, 'start_fold': datetime.datetime(2025, 5, 1, 0, 0), 'end_fold': datetime.datetime(2025, 6, 1, 0, 0), 'len_sample': 4238}
{'RMSE': 0.5901043244377436, 'MAE': 0.45381838099130506, 'MAPE': 0.4520144250817811, 'WITHIN_20%': 0.3077324973876698, 'start_fold': datetime.datetime(2025, 6, 1, 0, 0), 'end_fold': datetime.datetime(2025, 7, 1, 0, 0), 'len_sample': 3828}
{'RMSE': 0.602202942675564, 'MAE': 0.4554844167914694, 'MAPE': 0.4876788244811029, 'WITHIN_20%': 0.31227217496962334, 'start_fold': datetime.datetime(2025, 7, 1, 0, 0), 'end_fold': datetime.datetime(2025, 8, 1, 0, 0), 'len_sample': 4115}
{'RMSE': 0.5818184674587815, 'MAE': 0.4507048864896356, 'MAPE': 0.45666971736440864, 'WITHIN_20%': 0.2926599008732594, 'start_fold': datetime.datetime(2025, 8, 1, 0, 0), 'end_fold': datetime.datetime(2025, 9, 1, 0, 0), 'len_sample': 4237}
{'RMSE': 0.6003215655996142, 'MAE': 0.4623403521527098, 'MAPE': 0.5208834524738387, 'WITHIN_20%': 0.2862765678299659, 'start_fold': datetime.datetime(2025, 9, 1, 0, 0), 'end_fold': datetime.datetime(2025, 10, 1, 0, 0), 'len_sample': 3811}
{'RMSE': 0.5954436307551255, 'MAE': 0.4590401950464819, 'MAPE': 0.453824135757581, 'WITHIN_20%': 0.2871155885471898, 'start_fold': datetime.datetime(2025, 10, 1, 0, 0), 'end_fold': datetime.datetime(2025, 11, 1, 0, 0), 'len_sample': 3772}
{'RMSE': 0.6313021881552493, 'MAE': 0.48099506667949304, 'MAPE': 0.5380877369206047, 'WITHIN_20%': 0.2731123847495639, 'start_fold': datetime.datetime(2025, 11, 1, 0, 0), 'end_fold': datetime.datetime(2025, 12, 1, 0, 0), 'len_sample': 4013}
{'RMSE': 0.6137735790975003, 'MAE': 0.4680470991295123, 'MAPE': 0.4915426143294351, 'WITHIN_20%': 0.29410388394946185, 'start_fold': datetime.datetime(2025, 12, 1, 0, 0), 'end_fold': datetime.datetime(2026, 1, 1, 0, 0), 'len_sample': 4274}
{'RMSE': 0.619391470257282, 'MAE': 0.47901189547718837, 'MAPE': 0.46012790272070986, 'WITHIN_20%': 0.28896355647454125, 'start_fold': datetime.datetime(2026, 1, 1, 0, 0), 'end_fold': datetime.datetime(2026, 2, 1, 0, 0), 'len_sample': 3869}
{'RMSE': 0.618453256196534, 'MAE': 0.4724675477947109, 'MAPE': 0.49882794332244335, 'WITHIN_20%': 0.2936117936117936, 'start_fold': datetime.datetime(2026, 2, 1, 0, 0), 'end_fold': datetime.datetime(2026, 3, 1, 0, 0), 'len_sample': 3256}

=== Same titles ===
{'RMSE': 0.5457512860832346, 'MAE': 0.40932040780312534, 'MAPE': 0.5561151352900257, 'WITHIN_20%': 0.3328220858895706, 'start_fold': datetime.datetime(2025, 2, 1, 0, 0), 'end_fold': datetime.datetime(2025, 3, 1, 0, 0), 'len_sample': 3260}
{'RMSE': 0.5339927102916439, 'MAE': 0.4090690102503034, 'MAPE': 0.5213957814270088, 'WITHIN_20%': 0.32548359966358287, 'start_fold': datetime.datetime(2025, 3, 1, 0, 0), 'end_fold': datetime.datetime(2025, 4, 1, 0, 0), 'len_sample': 3567}
{'RMSE': 0.5602468430874575, 'MAE': 0.41520145443601925, 'MAPE': 0.5699304853668374, 'WITHIN_20%': 0.33755896226415094, 'start_fold': datetime.datetime(2025, 4, 1, 0, 0), 'end_fold': datetime.datetime(2025, 5, 1, 0, 0), 'len_sample': 3392}
{'RMSE': 0.5227417195383942, 'MAE': 0.4000096155052783, 'MAPE': 0.49163764756906886, 'WITHIN_20%': 0.33515151515151514, 'start_fold': datetime.datetime(2025, 5, 1, 0, 0), 'end_fold': datetime.datetime(2025, 6, 1, 0, 0), 'len_sample': 3300}
{'RMSE': 0.5397867835793494, 'MAE': 0.4049305534103508, 'MAPE': 0.5366641220700766, 'WITHIN_20%': 0.3223500155424308, 'start_fold': datetime.datetime(2025, 6, 1, 0, 0), 'end_fold': datetime.datetime(2025, 7, 1, 0, 0), 'len_sample': 3217}
{'RMSE': 0.5454441625218709, 'MAE': 0.40952531015118043, 'MAPE': 0.5811832363314814, 'WITHIN_20%': 0.32213209733487835, 'start_fold': datetime.datetime(2025, 7, 1, 0, 0), 'end_fold': datetime.datetime(2025, 8, 1, 0, 0), 'len_sample': 3452}
{'RMSE': 0.5347012068830934, 'MAE': 0.3998200033684557, 'MAPE': 0.5317513761980422, 'WITHIN_20%': 0.3470213996529786, 'start_fold': datetime.datetime(2025, 8, 1, 0, 0), 'end_fold': datetime.datetime(2025, 9, 1, 0, 0), 'len_sample': 3458}
{'RMSE': 0.5275180691873536, 'MAE': 0.4042487997468162, 'MAPE': 0.5073648762543167, 'WITHIN_20%': 0.3206426825008732, 'start_fold': datetime.datetime(2025, 9, 1, 0, 0), 'end_fold': datetime.datetime(2025, 10, 1, 0, 0), 'len_sample': 2863}
{'RMSE': 0.5416245479324792, 'MAE': 0.4005453454711673, 'MAPE': 0.5972823854434772, 'WITHIN_20%': 0.34165866154338775, 'start_fold': datetime.datetime(2025, 10, 1, 0, 0), 'end_fold': datetime.datetime(2025, 11, 1, 0, 0), 'len_sample': 3123}
{'RMSE': 0.5609790033713083, 'MAE': 0.42734036096942285, 'MAPE': 0.5659406533291079, 'WITHIN_20%': 0.3201382343700911, 'start_fold': datetime.datetime(2025, 11, 1, 0, 0), 'end_fold': datetime.datetime(2025, 12, 1, 0, 0), 'len_sample': 3183}
{'RMSE': 0.5189685789262402, 'MAE': 0.3986560256035775, 'MAPE': 0.4790844529184628, 'WITHIN_20%': 0.32847158918261926, 'start_fold': datetime.datetime(2025, 12, 1, 0, 0), 'end_fold': datetime.datetime(2026, 1, 1, 0, 0), 'len_sample': 3291}
{'RMSE': 0.5781342105642059, 'MAE': 0.4242119168138618, 'MAPE': 0.5864625623149734, 'WITHIN_20%': 0.34024604569420036, 'start_fold': datetime.datetime(2026, 1, 1, 0, 0), 'end_fold': datetime.datetime(2026, 2, 1, 0, 0), 'len_sample': 2845}
{'RMSE': 0.5190194554943758, 'MAE': 0.4007537963960764, 'MAPE': 0.47672297410520875, 'WITHIN_20%': 0.34107002360346184, 'start_fold': datetime.datetime(2026, 2, 1, 0, 0), 'end_fold': datetime.datetime(2026, 3, 1, 0, 0), 'len_sample': 2542}

=== Unique_titles ===
{'RMSE': 0.5932422012062832, 'MAE': 0.44794382510695147, 'MAPE': 0.5590476738431504, 'WITHIN_20%': 0.3029593713425848, 'start_fold': datetime.datetime(2025, 2, 1, 0, 0), 'end_fold': datetime.datetime(2025, 3, 1, 0, 0), 'len_sample': 5981}
{'RMSE': 0.6095291993696735, 'MAE': 0.4598756729635456, 'MAPE': 0.5942087371294975, 'WITHIN_20%': 0.28767336761726664, 'start_fold': datetime.datetime(2025, 3, 1, 0, 0), 'end_fold': datetime.datetime(2025, 4, 1, 0, 0), 'len_sample': 6417}
{'RMSE': 0.5943000308572366, 'MAE': 0.4558090774188864, 'MAPE': 0.5574245921972605, 'WITHIN_20%': 0.29124203821656053, 'start_fold': datetime.datetime(2025, 4, 1, 0, 0), 'end_fold': datetime.datetime(2025, 5, 1, 0, 0), 'len_sample': 6280}
{'RMSE': 0.5970694893406457, 'MAE': 0.44877776544893605, 'MAPE': 0.60952202069125, 'WITHIN_20%': 0.30493707647628265, 'start_fold': datetime.datetime(2025, 5, 1, 0, 0), 'end_fold': datetime.datetime(2025, 6, 1, 0, 0), 'len_sample': 6198}
{'RMSE': 0.5837429863387013, 'MAE': 0.4436679175040048, 'MAPE': 0.5476477656845143, 'WITHIN_20%': 0.29874572405929306, 'start_fold': datetime.datetime(2025, 6, 1, 0, 0), 'end_fold': datetime.datetime(2025, 7, 1, 0, 0), 'len_sample': 6139}
{'RMSE': 0.5986432072007171, 'MAE': 0.45341202649699514, 'MAPE': 0.560083166793047, 'WITHIN_20%': 0.2991372549019608, 'start_fold': datetime.datetime(2025, 7, 1, 0, 0), 'end_fold': datetime.datetime(2025, 8, 1, 0, 0), 'len_sample': 6375}
{'RMSE': 0.5797399682690366, 'MAE': 0.43928358274483525, 'MAPE': 0.5387011151900696, 'WITHIN_20%': 0.3086013462976814, 'start_fold': datetime.datetime(2025, 8, 1, 0, 0), 'end_fold': datetime.datetime(2025, 9, 1, 0, 0), 'len_sample': 6685}
{'RMSE': 0.5844466685312343, 'MAE': 0.4434823443472056, 'MAPE': 0.5383913455520969, 'WITHIN_20%': 0.31127893613528157, 'start_fold': datetime.datetime(2025, 9, 1, 0, 0), 'end_fold': datetime.datetime(2025, 10, 1, 0, 0), 'len_sample': 6091}
{'RMSE': 0.5724646250331118, 'MAE': 0.4391028250722422, 'MAPE': 0.5138010119940785, 'WITHIN_20%': 0.30190761469336275, 'start_fold': datetime.datetime(2025, 10, 1, 0, 0), 'end_fold': datetime.datetime(2025, 11, 1, 0, 0), 'len_sample': 6343}
{'RMSE': 0.6033367437445999, 'MAE': 0.45350501986784697, 'MAPE': 0.6189752397848406, 'WITHIN_20%': 0.3015848826577263, 'start_fold': datetime.datetime(2025, 11, 1, 0, 0), 'end_fold': datetime.datetime(2025, 12, 1, 0, 0), 'len_sample': 6562}
{'RMSE': 0.5886069603110272, 'MAE': 0.4475407229082579, 'MAPE': 0.5244151274989163, 'WITHIN_20%': 0.30035180299032543, 'start_fold': datetime.datetime(2025, 12, 1, 0, 0), 'end_fold': datetime.datetime(2026, 1, 1, 0, 0), 'len_sample': 6822}
{'RMSE': 0.59241536257642, 'MAE': 0.4523897205415946, 'MAPE': 0.5137575759678068, 'WITHIN_20%': 0.30662358642972537, 'start_fold': datetime.datetime(2026, 1, 1, 0, 0), 'end_fold': datetime.datetime(2026, 2, 1, 0, 0), 'len_sample': 6190}
{'RMSE': 0.5901158331686298, 'MAE': 0.4503222284847078, 'MAPE': 0.515952828220155, 'WITHIN_20%': 0.29673477181592517, 'start_fold': datetime.datetime(2026, 2, 1, 0, 0), 'end_fold': datetime.datetime(2026, 3, 1, 0, 0), 'len_sample': 5237}

=== Unseen_titles ===
{'RMSE': 0.6327320433792485, 'MAE': 0.480424114512786, 'MAPE': 0.5996590473626261, 'WITHIN_20%': 0.2824402048116715, 'start_fold': datetime.datetime(2025, 2, 1, 0, 0), 'end_fold': datetime.datetime(2025, 3, 1, 0, 0), 'len_sample': 57028}
{'RMSE': 0.6338009924626643, 'MAE': 0.48044038964870006, 'MAPE': 0.6227255299318216, 'WITHIN_20%': 0.2837284707619705, 'start_fold': datetime.datetime(2025, 3, 1, 0, 0), 'end_fold': datetime.datetime(2025, 4, 1, 0, 0), 'len_sample': 62299}
{'RMSE': 0.6375419501661949, 'MAE': 0.48346872298078525, 'MAPE': 0.6180670923889209, 'WITHIN_20%': 0.279507868765004, 'start_fold': datetime.datetime(2025, 4, 1, 0, 0), 'end_fold': datetime.datetime(2025, 5, 1, 0, 0), 'len_sample': 59984}
{'RMSE': 0.6457028730583295, 'MAE': 0.48581161502885795, 'MAPE': 0.6385704067369382, 'WITHIN_20%': 0.2825815593266084, 'start_fold': datetime.datetime(2025, 5, 1, 0, 0), 'end_fold': datetime.datetime(2025, 6, 1, 0, 0), 'len_sample': 59282}
{'RMSE': 0.6323025120031361, 'MAE': 0.4804696601474204, 'MAPE': 0.6019238391883365, 'WITHIN_20%': 0.28369278287571603, 'start_fold': datetime.datetime(2025, 6, 1, 0, 0), 'end_fold': datetime.datetime(2025, 7, 1, 0, 0), 'len_sample': 55687}
{'RMSE': 0.6369285266333451, 'MAE': 0.4817052910155529, 'MAPE': 0.6081939129089488, 'WITHIN_20%': 0.2877565217391304, 'start_fold': datetime.datetime(2025, 7, 1, 0, 0), 'end_fold': datetime.datetime(2025, 8, 1, 0, 0), 'len_sample': 57500}
{'RMSE': 0.6788948202056037, 'MAE': 0.4930520449506237, 'MAPE': 0.8772210439201892, 'WITHIN_20%': 0.2832005367776566, 'start_fold': datetime.datetime(2025, 8, 1, 0, 0), 'end_fold': datetime.datetime(2025, 9, 1, 0, 0), 'len_sample': 59615}
{'RMSE': 0.6337162930126679, 'MAE': 0.48145656698029443, 'MAPE': 0.6172074274524372, 'WITHIN_20%': 0.28337854128993367, 'start_fold': datetime.datetime(2025, 9, 1, 0, 0), 'end_fold': datetime.datetime(2025, 10, 1, 0, 0), 'len_sample': 53088}
{'RMSE': 0.6311704588812873, 'MAE': 0.4825303896076788, 'MAPE': 0.5848434701721796, 'WITHIN_20%': 0.277385625849313, 'start_fold': datetime.datetime(2025, 10, 1, 0, 0), 'end_fold': datetime.datetime(2025, 11, 1, 0, 0), 'len_sample': 52984}
{'RMSE': 0.6341526200161843, 'MAE': 0.4821001864564985, 'MAPE': 0.5961051519430977, 'WITHIN_20%': 0.2805871247177285, 'start_fold': datetime.datetime(2025, 11, 1, 0, 0), 'end_fold': datetime.datetime(2025, 12, 1, 0, 0), 'len_sample': 54026}
{'RMSE': 0.6273569255422969, 'MAE': 0.47668576635916515, 'MAPE': 0.5662230147582605, 'WITHIN_20%': 0.28716541326585165, 'start_fold': datetime.datetime(2025, 12, 1, 0, 0), 'end_fold': datetime.datetime(2026, 1, 1, 0, 0), 'len_sample': 56114}
{'RMSE': 0.6417100520076169, 'MAE': 0.4865782323166951, 'MAPE': 0.5978186187470237, 'WITHIN_20%': 0.2829616413916146, 'start_fold': datetime.datetime(2026, 1, 1, 0, 0), 'end_fold': datetime.datetime(2026, 2, 1, 0, 0), 'len_sample': 50445}
{'RMSE': 0.6314657038850089, 'MAE': 0.4800845777354588, 'MAPE': 0.5596009482396724, 'WITHIN_20%': 0.28552523500958293, 'start_fold': datetime.datetime(2026, 2, 1, 0, 0), 'end_fold': datetime.datetime(2026, 3, 1, 0, 0), 'len_sample': 43828}

=== Within 50-250 ===
{'RMSE': 0.4690315270237969, 'MAE': 0.3667991735364967, 'MAPE': 0.36791080155633976, 'WITHIN_20%': 0.35307262569832404, 'start_fold': datetime.datetime(2025, 2, 1, 0, 0), 'end_fold': datetime.datetime(2025, 3, 1, 0, 0), 'len_sample': 73946}
{'RMSE': 0.4662157685942803, 'MAE': 0.3649081946878726, 'MAPE': 0.3644161505996939, 'WITHIN_20%': 0.3531671560726823, 'start_fold': datetime.datetime(2025, 3, 1, 0, 0), 'end_fold': datetime.datetime(2025, 4, 1, 0, 0), 'len_sample': 80637}
{'RMSE': 0.47146879851781676, 'MAE': 0.3692357563277193, 'MAPE': 0.36675079652396797, 'WITHIN_20%': 0.34919503219871206, 'start_fold': datetime.datetime(2025, 4, 1, 0, 0), 'end_fold': datetime.datetime(2025, 5, 1, 0, 0), 'len_sample': 77530}
{'RMSE': 0.47740691734945767, 'MAE': 0.37329175871123355, 'MAPE': 0.36932762868111246, 'WITHIN_20%': 0.3500138427464009, 'start_fold': datetime.datetime(2025, 5, 1, 0, 0), 'end_fold': datetime.datetime(2025, 6, 1, 0, 0), 'len_sample': 76633}
{'RMSE': 0.47488354287675155, 'MAE': 0.3705638681075126, 'MAPE': 0.3668531853788474, 'WITHIN_20%': 0.3537158752430889, 'start_fold': datetime.datetime(2025, 6, 1, 0, 0), 'end_fold': datetime.datetime(2025, 7, 1, 0, 0), 'len_sample': 72714}
{'RMSE': 0.4782372934435085, 'MAE': 0.3715898488590796, 'MAPE': 0.37134945477115006, 'WITHIN_20%': 0.3541091852375899, 'start_fold': datetime.datetime(2025, 7, 1, 0, 0), 'end_fold': datetime.datetime(2025, 8, 1, 0, 0), 'len_sample': 75238}
{'RMSE': 0.47395783155471854, 'MAE': 0.3694311980003138, 'MAPE': 0.3723535092769139, 'WITHIN_20%': 0.35478358628442946, 'start_fold': datetime.datetime(2025, 8, 1, 0, 0), 'end_fold': datetime.datetime(2025, 9, 1, 0, 0), 'len_sample': 78021}
{'RMSE': 0.4730445129081104, 'MAE': 0.3702230812730844, 'MAPE': 0.37640551271711453, 'WITHIN_20%': 0.35213167162017034, 'start_fold': datetime.datetime(2025, 9, 1, 0, 0), 'end_fold': datetime.datetime(2025, 10, 1, 0, 0), 'len_sample': 69214}
{'RMSE': 0.470947460590691, 'MAE': 0.3698482207414351, 'MAPE': 0.37674563772620345, 'WITHIN_20%': 0.34518354274451835, 'start_fold': datetime.datetime(2025, 10, 1, 0, 0), 'end_fold': datetime.datetime(2025, 11, 1, 0, 0), 'len_sample': 70022}
{'RMSE': 0.4661559241036733, 'MAE': 0.3663821304040138, 'MAPE': 0.375244805028319, 'WITHIN_20%': 0.34871967654986524, 'start_fold': datetime.datetime(2025, 11, 1, 0, 0), 'end_fold': datetime.datetime(2025, 12, 1, 0, 0), 'len_sample': 71301}
{'RMSE': 0.46645499159766607, 'MAE': 0.36545288190678465, 'MAPE': 0.3679264041046153, 'WITHIN_20%': 0.35391076589595377, 'start_fold': datetime.datetime(2025, 12, 1, 0, 0), 'end_fold': datetime.datetime(2026, 1, 1, 0, 0), 'len_sample': 74311}
{'RMSE': 0.4683472689664889, 'MAE': 0.36595147207805545, 'MAPE': 0.37200957000576973, 'WITHIN_20%': 0.3591869960062069, 'start_fold': datetime.datetime(2026, 1, 1, 0, 0), 'end_fold': datetime.datetime(2026, 2, 1, 0, 0), 'len_sample': 66874}
{'RMSE': 0.4691387534072434, 'MAE': 0.367633785940484, 'MAPE': 0.37012652119137596, 'WITHIN_20%': 0.3535879119263897, 'start_fold': datetime.datetime(2026, 2, 1, 0, 0), 'end_fold': datetime.datetime(2026, 3, 1, 0, 0), 'len_sample': 57936}

An underfit baseline masked feature effects An underfit baseline masked feature effects An underfit baseline masked feature effects An underfit baseline masked feature effects An underfit baseline masked feature effects An underfit baseline masked feature effects
RERUH EVERYTHING
TF-IDF NO EMBEDDS
57936
All [{'RMSE': 0.5761377252367218, 'MAE': 0.43297663984041634, 'MAPE': 0.5027658403050825, 'WITHIN_20%': 0.3198184203258768}]
3256
Archive [{'RMSE': 0.5768893714675439, 'MAE': 0.43417988722797146, 'MAPE': 0.503519206765922, 'WITHIN_20%': 0.3178746928746929}]
2542
Same titles [{'RMSE': 0.46720652436852733, 'MAE': 0.35524158660880145, 'MAPE': 0.424915656876563, 'WITHIN_20%': 0.3780487804878049}]
5237
Unique_titles [{'RMSE': 0.5483153892810362, 'MAE': 0.4165465401186957, 'MAPE': 0.4826674010632339, 'WITHIN_20%': 0.3164025205270193}]
43828
Unseen_titles [{'RMSE': 0.5943471028595783, 'MAE': 0.4474048798063392, 'MAPE': 0.5159298474835683, 'WITHIN_20%': 0.31021264944784155}]
57936
Within 50-250 [{'RMSE': 0.46476746574391614, 'MAE': 0.36080227992942504, 'MAPE': 0.37027212059601267, 'WITHIN_20%': 0.36428844401328747}]
1632
within_50_250_and_same_titles [{'RMSE': 0.4036539337273856, 'MAE': 0.3111655179577809, 'MAPE': 0.3425239536415928, 'WITHIN_20%': 0.41299019607843135}]

TF-IDF W EMBEDDS n_segments 500
57936
All [{'RMSE': 0.5601514020213376, 'MAE': 0.4213104920742713, 'MAPE': 0.48786497216030306, 'WITHIN_20%': 0.3271886219276443}]
3256
Archive [{'RMSE': 0.5621003099655961, 'MAE': 0.4257545719735947, 'MAPE': 0.49948023602453545, 'WITHIN_20%': 0.3178746928746929}]
2542
Same titles [{'RMSE': 0.4581004497215516, 'MAE': 0.3471073448622596, 'MAPE': 0.4085937267014011, 'WITHIN_20%': 0.3957513768686074}]
5237
Unique_titles [{'RMSE': 0.5307334177108776, 'MAE': 0.40223974704294635, 'MAPE': 0.46474118102842954, 'WITHIN_20%': 0.32423143020813444}]
43828
Unseen_titles [{'RMSE': 0.5777156373561917, 'MAE': 0.4353796887506758, 'MAPE': 0.5013536614426463, 'WITHIN_20%': 0.31710322168476773}]
57936
Within 50-250 [{'RMSE': 0.45546188068262206, 'MAE': 0.35460351278216956, 'MAPE': 0.3615664631204415, 'WITHIN_20%': 0.3656407090572361}]
1632
within_50_250_and_same_titles [{'RMSE': 0.39685858828490356, 'MAE': 0.30640591150855984, 'MAPE': 0.32965981111586506, 'WITHIN_20%': 0.4252450980392157}]

TF-IDF W EMBEDDS n_segments 1000
57936
All [{'RMSE': 0.5440390112479584, 'MAE': 0.4083021937735132, 'MAPE': 0.4715319973061986, 'WITHIN_20%': 0.33706158519745927}]
3256
Archive [{'RMSE': 0.547918971483859, 'MAE': 0.413094429788868, 'MAPE': 0.48898761723083944, 'WITHIN_20%': 0.3298525798525799}]
2542
Same titles [{'RMSE': 0.43934767390820934, 'MAE': 0.3317276658413812, 'MAPE': 0.38752274136792514, 'WITHIN_20%': 0.41581431943351693}]
5237
Unique_titles [{'RMSE': 0.5118150819796161, 'MAE': 0.3874472797584214, 'MAPE': 0.4474354633754238, 'WITHIN_20%': 0.34218063776971547}]
43828
Unseen_titles [{'RMSE': 0.5623571529897422, 'MAE': 0.42298813931949786, 'MAPE': 0.4861770068219676, 'WITHIN_20%': 0.32472392078123574}]
57936
Within 50-250 [{'RMSE': 0.4468944431660266, 'MAE': 0.3471317385445323, 'MAPE': 0.35430930966575525, 'WITHIN_20%': 0.3739894758503101}]
1632
within_50_250_and_same_titles [{'RMSE': 0.3843616962299535, 'MAE': 0.29392128578948656, 'MAPE': 0.31710077987245955, 'WITHIN_20%': 0.4479166666666667}]

TF-IDF W EMBEDDS n_segments 2000
57936
All [{'RMSE': 0.5295513684566149, 'MAE': 0.3962969076471416, 'MAPE': 0.45631087415840876, 'WITHIN_20%': 0.3497997790665562}]
3256
Archive [{'RMSE': 0.531539801938865, 'MAE': 0.3983040371736647, 'MAPE': 0.47565879868120897, 'WITHIN_20%': 0.35503685503685506}]
2542
Same titles [{'RMSE': 0.42477102086925056, 'MAE': 0.31956962534616606, 'MAPE': 0.37059572329865137, 'WITHIN_20%': 0.4331235247836349}]
5237
Unique_titles [{'RMSE': 0.4973264640240311, 'MAE': 0.37490730620542645, 'MAPE': 0.434220286788965, 'WITHIN_20%': 0.355547068932595}]
43828
Unseen_titles [{'RMSE': 0.5486060054463355, 'MAE': 0.4117049945328528, 'MAPE': 0.47193167201946534, 'WITHIN_20%': 0.33667974810623347}]
57936
Within 50-250 [{'RMSE': 0.4392390704990873, 'MAE': 0.34030834494381906, 'MAPE': 0.3474655335704857, 'WITHIN_20%': 0.3842196548784431}]
1632
within_50_250_and_same_titles [{'RMSE': 0.3746018837575369, 'MAE': 0.28588142506414665, 'MAPE': 0.3074967018842041, 'WITHIN_20%': 0.46017156862745096}]

TF-IDF W EMBEDDS n_segments 2000 learning rate 0.05 - worse 
57936
All [{'RMSE': 0.5419486020011646, 'MAE': 0.406786003393807, 'MAPE': 0.4688032404238611, 'WITHIN_20%': 0.33882214857774096}]
3256
Archive [{'RMSE': 0.5432160947555609, 'MAE': 0.4087370855204946, 'MAPE': 0.4824797148104258, 'WITHIN_20%': 0.3356879606879607}]
2542
Same titles [{'RMSE': 0.4364141509533854, 'MAE': 0.3301754905696255, 'MAPE': 0.38427733339376846, 'WITHIN_20%': 0.4205350118017309}]
5237
Unique_titles [{'RMSE': 0.5114756684153272, 'MAE': 0.38694447163586815, 'MAPE': 0.44735062657481667, 'WITHIN_20%': 0.34409012793584115}]
43828
Unseen_titles [{'RMSE': 0.560128322564168, 'MAE': 0.4213114933884557, 'MAPE': 0.48286976286348204, 'WITHIN_20%': 0.3259788263210733}]
57936
Within 50-250 [{'RMSE': 0.44533935807074, 'MAE': 0.346218255940262, 'MAPE': 0.35297142391760283, 'WITHIN_20%': 0.3756945056883323}]
1632
within_50_250_and_same_titles [{'RMSE': 0.38336517196339076, 'MAE': 0.2940091816957207, 'MAPE': 0.3167284506201958, 'WITHIN_20%': 0.44730392156862747}]

57936 TF-IDF W EMBEDDS n_segments 4000
All [{'RMSE': 0.5210178774196585, 'MAE': 0.3890405383553082, 'MAPE': 0.4482136914078572, 'WITHIN_20%': 0.3558064070698702}]
3256
Archive [{'RMSE': 0.522751276800783, 'MAE': 0.39089508954982155, 'MAPE': 0.47696251259310446, 'WITHIN_20%': 0.3578009828009828}]
2542
Same titles [{'RMSE': 0.413597344644986, 'MAE': 0.31072665942271804, 'MAPE': 0.3573402166273383, 'WITHIN_20%': 0.44256490952006294}]
5237
Unique_titles [{'RMSE': 0.4825615521183991, 'MAE': 0.3630992288639374, 'MAPE': 0.41952579689973335, 'WITHIN_20%': 0.370441092228375}]
43828
Unseen_titles [{'RMSE': 0.5415145544793052, 'MAE': 0.4054760679748347, 'MAPE': 0.4660616696110719, 'WITHIN_20%': 0.3410605092634845}]
57936
Within 50-250 [{'RMSE': 0.4344501677697644, 'MAE': 0.3362496985054484, 'MAPE': 0.3427819916768598, 'WITHIN_20%': 0.3882176558779434}]
1632
within_50_250_and_same_titles [{'RMSE': 0.36817909825619416, 'MAE': 0.28120810224136217, 'MAPE': 0.30053856595225364, 'WITHIN_20%': 0.4644607843137255}]

features plateaued, then tuning revealed the plateau was artificial, and the feature gains reappeared

57936 TF-IDF W EMBEDDS n_segments 2000 num_leaves=63
57936
All [{'RMSE': 0.5122916963875959, 'MAE': 0.38175601522921776, 'MAPE': 0.4396032664204166, 'WITHIN_20%': 0.36302126484396574}]
3256
Archive [{'RMSE': 0.5128582206449019, 'MAE': 0.38174537446785334, 'MAPE': 0.4765116415088566, 'WITHIN_20%': 0.3584152334152334}]
2542
Same titles [{'RMSE': 0.4022714147785047, 'MAE': 0.3011577624948524, 'MAPE': 0.345591231303477, 'WITHIN_20%': 0.45082612116443743}]
5237
Unique_titles [{'RMSE': 0.4689626237733183, 'MAE': 0.3531428043516961, 'MAPE': 0.407238186053543, 'WITHIN_20%': 0.37731525682642736}]
43828
Unseen_titles [{'RMSE': 0.5339253573467669, 'MAE': 0.3990202581907631, 'MAPE': 0.45868648485265984, 'WITHIN_20%': 0.34708405585470475}]
57936
Within 50-250 [{'RMSE': 0.42968628962014654, 'MAE': 0.3313708976192429, 'MAPE': 0.3383231418956478, 'WITHIN_20%': 0.39515536349472324}]
1632
within_50_250_and_same_titles [{'RMSE': 0.35997392116090254, 'MAE': 0.2747054717408579, 'MAPE': 0.2951422463968883, 'WITHIN_20%': 0.47058823529411764}]

57936 TF-IDF W EMBEDDS n_segments 2000 num_leaves=127
57936
All [{'RMSE': 0.5110499176014807, 'MAE': 0.38033944400287645, 'MAPE': 0.4368721544095563, 'WITHIN_20%': 0.36554128693731014}]
3256
Archive [{'RMSE': 0.5087271623970511, 'MAE': 0.37935988426568706, 'MAPE': 0.46172485221000986, 'WITHIN_20%': 0.36947174447174447}]
2542
Same titles [{'RMSE': 0.4021407730903832, 'MAE': 0.3017023759757719, 'MAPE': 0.34674268671253566, 'WITHIN_20%': 0.44767899291896146}]
5237
Unique_titles [{'RMSE': 0.46789512998298904, 'MAE': 0.35034179400782445, 'MAPE': 0.40554819463905206, 'WITHIN_20%': 0.3864808096238304}]
43828
Unseen_titles [{'RMSE': 0.5328255569279678, 'MAE': 0.39809539998378346, 'MAPE': 0.4561861785202001, 'WITHIN_20%': 0.34852149310942776}]
57936
Within 50-250 [{'RMSE': 0.4294972066660057, 'MAE': 0.3305836298186968, 'MAPE': 0.33684931935538676, 'WITHIN_20%': 0.3973307463915101}]
1632
within_50_250_and_same_titles [{'RMSE': 0.35943429051409004, 'MAE': 0.2728408954961714, 'MAPE': 0.2925618622945359, 'WITHIN_20%': 0.47610294117647056}]