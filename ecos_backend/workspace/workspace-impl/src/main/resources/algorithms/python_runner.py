# ECOS 场景算法 Python 执行模板（V367 批次 B3）
# 仅标准库 + numpy/scipy（沙盒 APT 包）；无文件/网络/环境读取。
# 调用方注入：__ALGO__ 算法键、__PARAMS__ JSON 参数。输出单行 `RESULT <json>`。
import json
import math

import numpy as np

try:
    from scipy import stats as sps
except Exception:
    sps = None

ALGO = "__ALGO__"
# 参数以 JSON 字符串注入再解析：避免 JSON 的 true/false 直接进入 Python 源码
P = json.loads(__PARAMS__)


def emit(obj):
    print("RESULT " + json.dumps(obj, default=float, ensure_ascii=False))


def _arr(key, default=None):
    return np.array(P.get(key, default if default is not None else []), dtype=float)


def arima():
    y = _arr("series")
    if len(y) < 4:
        raise ValueError("series 至少 4 个点")
    d = int(P.get("d", 1))
    p = int(P.get("p", 1))
    h = int(P.get("horizon", 1))
    for _ in range(d):
        y = np.diff(y)
    if len(y) <= p + 1:
        raise ValueError("p 相对样本过大")
    # 逐行构建设计矩阵（比 column_stack 更稳，避免空/一维拼接歧义）
    rows = [[1.0] + [float(y[t - 1 - i]) for i in range(p)] for t in range(p, len(y))]
    design = np.array(rows, dtype=float)
    target = y[p:]
    beta = np.linalg.lstsq(design, target, rcond=None)[0]
    hist = list(y)
    fc = []
    for _ in range(h):
        lag = np.array(hist[-p:][::-1]) if p > 0 else np.array([])
        v = float(beta[0] + (lag @ beta[1:] if p > 0 else 0.0))
        hist.append(v)
        fc.append(v)
    emit({"forecast": fc, "ar": beta[1:].tolist() if p > 0 else [], "d": d})


def _stump_fit(x, y):
    # 一维决策树桩：在候选切分点取 SSE 最小
    order = np.argsort(x)
    xs, ys = x[order], y[order]
    best = None
    for i in range(1, len(xs)):
        if xs[i] == xs[i - 1]:
            continue
        left, right = ys[:i], ys[i:]
        sse = float(((left - left.mean()) ** 2).sum() + ((right - right.mean()) ** 2).sum())
        if best is None or sse < best[0]:
            best = (sse, (xs[i] + xs[i - 1]) / 2, float(left.mean()), float(right.mean()))
    if best is None:
        return (float("inf"), float("inf"), float(y.mean()), float(y.mean()))
    return best[1:]


def _stump_pred(stump, x):
    thr, lv, rv = stump
    return np.where(x <= thr, lv, rv)


def random_forest():
    X = np.array(P.get("X", []), dtype=float)
    y = _arr("y")
    if X.ndim != 2 or X.shape[0] != len(y):
        raise ValueError("X 必须为二维且行数与 y 一致")
    trees = int(min(50, max(1, P.get("trees", 20))))
    rng = np.random.default_rng(int(P.get("seed", 42)))
    n, m = X.shape
    models = []
    for _ in range(trees):
        idx = rng.integers(0, n, n)
        feat = int(rng.integers(0, m))
        models.append((feat, _stump_fit(X[idx, feat], y[idx])))
    pred = np.mean([_stump_pred(s, X[:, f]) for f, s in models], axis=0)
    emit({"predictions": pred.tolist(), "trees": trees,
          "note": "浅树桩集成（最小可用随机森林），非生产级"})


def gbdt():
    X = np.array(P.get("X", []), dtype=float)
    y = _arr("y")
    if X.ndim != 2 or X.shape[0] != len(y):
        raise ValueError("X 必须为二维且行数与 y 一致")
    rounds = int(min(200, max(1, P.get("rounds", 30))))
    lr = float(P.get("learningRate", 0.1))
    base = float(y.mean())
    pred = np.full(len(y), base)
    models = []
    for _ in range(rounds):
        residual = y - pred
        feat = int(np.argmax([abs(np.corrcoef(X[:, j], residual)[0, 1]) if np.std(X[:, j]) > 0 else 0
                              for j in range(X.shape[1])]))
        stump = _stump_fit(X[:, feat], residual)
        pred += lr * _stump_pred(stump, X[:, feat])
        models.append((feat, stump))
    rmse = float(np.sqrt(((y - pred) ** 2).mean()))
    emit({"predictions": pred.tolist(), "rounds": rounds, "rmse": rmse})


def time_series_mlp():
    y = _arr("series")
    if len(y) < 6:
        raise ValueError("series 至少 6 个点")
    lags = int(P.get("lags", 3))
    hidden = int(P.get("hidden", 4))
    epochs = int(min(5000, P.get("epochs", 500)))
    lr = float(P.get("learningRate", 0.01))
    X = np.array([y[i:i + lags] for i in range(len(y) - lags)])
    t = y[lags:]
    rng = np.random.default_rng(int(P.get("seed", 42)))
    W1 = rng.normal(0, 0.1, (lags, hidden))
    b1 = np.zeros(hidden)
    W2 = rng.normal(0, 0.1, hidden)
    for _ in range(epochs):
        h = np.tanh(X @ W1 + b1)
        pred = h @ W2
        err = pred - t
        gW2 = h.T @ err / len(t)
        gh = (err[:, None] * W2[None, :]) * (1 - h ** 2)
        gW1 = X.T @ gh / len(t)
        W2 -= lr * gW2
        W1 -= lr * gW1
    hist = list(y[-lags:])
    fc = []
    for _ in range(int(P.get("horizon", 1))):
        h = np.tanh(np.array(hist[-lags:]) @ W1 + b1)
        v = float(h @ W2)
        hist.append(v)
        fc.append(v)
    emit({"forecast": fc, "rmse": float(np.sqrt((err ** 2).mean()))})


def causal_discovery_pc():
    X = np.array(P.get("X", []), dtype=float)
    names = P.get("names", [f"x{i}" for i in range(X.shape[1] if X.ndim == 2 else 0)])
    if X.ndim != 2 or X.shape[1] != len(names):
        raise ValueError("X 与 names 维度不一致")
    alpha = float(P.get("alpha", 0.05))
    edges = []
    for i in range(len(names)):
        for j in range(i + 1, len(names)):
            if sps is None:
                r = float(np.corrcoef(X[:, i], X[:, j])[0, 1])
                p = 0.0 if abs(r) > 0.3 else 1.0
            else:
                r, p = sps.pearsonr(X[:, i], X[:, j])
            if p < alpha:
                edges.append([names[i], names[j], round(float(r), 6), round(float(p), 6)])
    emit({"skeleton": edges, "method": "PC-pairwise（未做条件集迭代）"})


def propensity_score():
    X = np.array(P.get("X", []), dtype=float)
    t = _arr("treatment")
    y = _arr("outcome")
    if X.ndim != 2 or len(t) != X.shape[0] or len(y) != len(t):
        raise ValueError("X/treatment/outcome 维度不一致")
    design = np.column_stack([np.ones(len(t)), X])
    w = np.zeros(design.shape[1])
    for _ in range(300):
        z = design @ w
        p = 1 / (1 + np.exp(-z))
        w -= 0.1 * design.T @ (p - t) / len(t)
    ps = 1 / (1 + np.exp(-(design @ w)))
    ps = np.clip(ps, 1e-3, 1 - 1e-3)
    ate = float(np.mean(t * y / ps - (1 - t) * y / (1 - ps)))
    emit({"ate": ate, "propensity": ps.tolist()})


def doubly_robust():
    X = np.array(P.get("X", []), dtype=float)
    t = _arr("treatment")
    y = _arr("outcome")
    design = np.column_stack([np.ones(len(t)), X])
    w = np.zeros(design.shape[1])
    for _ in range(300):
        p = 1 / (1 + np.exp(-(design @ w)))
        w -= 0.1 * design.T @ (p - t) / len(t)
    ps = np.clip(1 / (1 + np.exp(-(design @ w))), 1e-3, 1 - 1e-3)
    # 结果模型：两组线性回归
    m1 = np.linalg.lstsq(design[t == 1], y[t == 1], rcond=None)[0]
    m0 = np.linalg.lstsq(design[t == 0], y[t == 0], rcond=None)[0]
    mu1, mu0 = design @ m1, design @ m0
    dr = mu1 - mu0 + t * (y - mu1) / ps - (1 - t) * (y - mu0) / (1 - ps)
    emit({"ate": float(np.mean(dr)), "n": int(len(t))})


def causal_forest():
    X = np.array(P.get("X", []), dtype=float)
    t = _arr("treatment")
    y = _arr("outcome")
    if X.ndim != 2 or len(t) != X.shape[0]:
        raise ValueError("X/treatment 维度不一致")
    # 简化：按特征切分做两组的差分响应（非诚实森林，明确标注）
    feat = int(P.get("feature", 0))
    thr = float(np.median(X[:, feat]))
    left = X[:, feat] <= thr
    def effect(mask):
        if mask.sum() < 2 or len(set(t[mask])) < 2:
            return 0.0
        return float(y[mask & (t == 1)].mean() - y[mask & (t == 0)].mean())
    emit({"cate": {"left": effect(left), "right": effect(~left)}, "splitFeature": feat,
          "threshold": thr, "note": "单切分简化版，非诚实因果森林"})


def mixed_integer_programming():
    import itertools
    c = _arr("c")
    A = np.array(P.get("A", []), dtype=float)
    b = _arr("b")
    maximize = bool(P.get("maximize", True))
    n = len(c)
    if n == 0 or n > 14:
        raise ValueError("仅支持 1~14 个 0/1 变量")
    best_val, best_x = None, None
    for bits in itertools.product([0, 1], repeat=n):
        x = np.array(bits, dtype=float)
        if A.size and np.any(A @ x > b + 1e-9):
            continue
        val = float(c @ x)
        if best_val is None or (val > best_val if maximize else val < best_val):
            best_val, best_x = val, bits
    if best_val is None:
        emit({"status": "INFEASIBLE"})
    else:
        emit({"status": "OPTIMAL", "x": list(best_x), "objective": best_val,
              "method": "0/1 穷举（<=14 变量）"})


DISPATCH = {
    "arima": arima,
    "random-forest": random_forest,
    "gbdt": gbdt,
    "time-series-mlp": time_series_mlp,
    "causal-discovery-pc": causal_discovery_pc,
    "causal-discovery-fci": causal_discovery_pc,
    "propensity-score": propensity_score,
    "doubly-robust": doubly_robust,
    "causal-forest": causal_forest,
    "mixed-integer-programming": mixed_integer_programming,
}

fn = DISPATCH.get(ALGO)
if fn is None:
    print("RESULT " + json.dumps({"error": "unknown algorithm: " + ALGO}))
else:
    fn()
