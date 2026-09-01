import numpy as np
from sklearn.metrics import mean_absolute_error, root_mean_squared_error, mean_absolute_percentage_error

def evaluate(y_true: list, y_pred: list) -> dict:
    rmse = root_mean_squared_error(y_true, y_pred)
    mae = mean_absolute_error(y_true, y_pred)
    y_dollar_true = np.exp(y_true)
    y_dollar_pred = np.exp(y_pred)
    mape = mean_absolute_percentage_error(y_dollar_true, y_dollar_pred)
    return {'RMSE': rmse, 'MAE': mae, 'MAPE': mape}