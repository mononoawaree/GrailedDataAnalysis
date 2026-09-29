# GrailedDataAnalysis
# Price prediction for fashion resale on 4.3M scraped Grailed sold listings.
# 1. Rationale
  Motivation for creating/trainig this model is that I resell and buy clothes on a frequent bas es 
  and I wanted to get better perspective for the price of an item I sell or buy. 
# 2. Data
  4.35M sold listings scraped from Grailed covering Aug 2021 – Mar 2026, ~93% Grailed's public sold index as of Sept. 2026.
# 3. Methodology
    For the target it was chosen to use sold_price in log space. The reason behind it is that I needed the model to penalize based on relative errors 
  since plain dollar comparison error for 100$ - 50$ is 50$ and for 1000$ - 500$ is 500$. 
  In contrast, in log space these errors is easily compared and both represent percentage errors of 50%.

    For the model I picked LightGBM - gradient boosting framework based on decision tree algorithms because features are mostly
  heterogeneous, high-cardinality categoricals, so there is no need for scaling, and GBDTs remain the strongest baseline on tabular data neural alternatives mostly fail to beat them.
  
    For validation I used 13 monthly walk-forward folds (train on everything before month N - test on N). For leakage check I used scramble test.
# 4. Results
  |model                              |  |RMSE    |	|within 20% |
  | --------------------------------- |  | ------ | | --------- |          
  | constant (mean price for all data)|  | 1.060  |	| 17.1%     |
  | designer × category mean          |  | 0.712  |	| 25.2%     | 
  | 11 structured features            |  | 0.6086 |	| 31.1%     |
  | season/year derived from titles   |  | 0.6031 |	| 31.4%     |
  | TF-IDF titles	                  |  | 0.5322 |	| 35.2%     |
  | FashionCLIP embeddings            |  | 0.5181 |	| 36.0%     |