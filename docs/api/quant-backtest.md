# 量化分析与回测

Spring Boot 负责权限、数据组装和持久化，`quant-engine` 负责特征计算、确定性动作、批量回测和可选 ML 推理。业务接口都需要登录。

## 量化分析

Base path：`/api/quant`

| 方法与路径 | 作用 |
| --- | --- |
| `GET /api/quant/health` | 查询 Python 引擎状态和模型版本 |
| `POST /api/quant/holdings/{holdingId}/analyze` | 分析一个当前用户持仓并保存信号 |
| `POST /api/quant/accounts/{accountId}/analyze` | 批量分析账户持仓 |
| `GET /api/quant/signals` | 查询已保存信号 |

信号查询支持 `accountId`、`holdingId`、`fundCode` 和 `action`。动作包括 `BUY`、`SELL`、`HOLD`、`WATCH`。

Java 会组装：

- 账户资产和权益仓位；
- 用户风险偏好和单基金上限；
- 持仓、成本、收益、持有天数和盘中估值；
- 历史净值与近期模拟交易；
- A 股、港股和美股指数因子；
- 交易日、当前阶段和 15:00 决策截止时间。

Python 返回总分、趋势/机会/风险分、指标、动作、建议比例、理由、风险和模型版本。Java 将结果写入 `quant_signal`，并同步生成可供业务和 AI 使用的策略信号。

## 回测业务 API

Base path：`/api/backtests`

| 方法与路径 | 作用 |
| --- | --- |
| `POST /api/backtests/nav-cache/refresh` | 将所选基金区间净值拉取到 Java 本地缓存 |
| `POST /api/backtests/run` | 组装本地净值并调用 Python 批量回测 |
| `POST /api/backtests/ml-training-samples/export` | 导出 UTF-8 CSV 训练样本 |

回测请求包含账户、日期区间、初始资金、费率、规则参数、预热天数、并发数和是否启用 ML 对比。单批基金数、参数网格、响应内存和超时受 `quantfund.quant-engine` 配置限制。

回测结果包括：

- 总收益、年化收益、最大回撤、夏普和卡玛；
- 满仓买入持有基准与相同仓位上限基准；
- 交易次数、通过状态、诊断和数据覆盖率；
- 权益曲线和逐笔交易的评分、仓位、理由与指标；
- 批次汇总、成功/失败基金和模型版本；
- 启用 ML 时的规则/ML A/B 指标。

## Python 直接 API

Base URL 默认 `http://127.0.0.1:8091`：

| 方法与路径 | 作用 |
| --- | --- |
| `GET /api/v1/health` | 服务和模型健康 |
| `POST /api/v1/quant/analyze` | 单项分析 |
| `POST /api/v1/quant/analyze-batch` | 批量分析 |
| `POST /api/v1/backtest/run` | 单基金回测 |
| `POST /api/v1/backtest/run-batch` | 批量回测 |
| `POST /api/v1/backtest/run-grid` | 参数网格回测 |
| `GET /api/v1/ml/models` | 模型注册表和 active 模型 |
| `POST /api/v1/ml/predict` | 特征字典预测 smoke |
| `POST /api/v1/ml/training-samples/export` | Python 侧训练样本导出能力 |

前端和普通客户端应优先调用 Java `/api/quant`、`/api/backtests`，不要绕过业务权限直接把 Python 服务暴露到公网。

## LightGBM 辅助

- 默认 `QUANT_ENGINE_ML_ENABLED=false`，生产行为保持纯规则模型；
- active 模型由 `quant-engine/models/registry.json` 指定；
- 概率模型只在 `QUANT_ENGINE_ML_SCORE_ADJUSTMENT_CAP` 范围内调整规则分数；
- 回归模型输出未来收益参考，供解释和回测诊断；
- ML 不能绕过仓位、市场时段、QDII 和极端风险约束。

训练和模型注册命令见 [`quant-engine/README.md`](../../quant-engine/README.md#optional-lightgbm-helper)。

## 安全与限制

- 回测只使用 Java 提供的本地历史净值，Python 不请求外部基金数据。
- 回测结果不代表未来表现，置信度也不是准确率保证。
- 分析和回测写接口受限流和防重复提交保护。
- 所有动作都是研究参考，不触发真实交易。
