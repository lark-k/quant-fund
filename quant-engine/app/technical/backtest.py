"""Versioned NAV replay with disclosed-information position sizing."""
from collections import Counter
from datetime import date, datetime, time
from hashlib import sha256
from json import dumps
from math import isfinite

from app.technical.rules import analyze, VERSION, BALANCED_VERSION
from app.technical.trend import Policy, APPROVED_CONFIG, APPROVED_VERSION


def replay(request, policy=None):
    if policy is None and request.ruleVersion == APPROVED_VERSION:
        policy = Policy(APPROVED_CONFIG, request.initialPositionPercent/100)
    rows = sorted(request.rows, key=lambda r: r.date)
    if len({r.date for r in rows}) != len(rows):
        raise ValueError('历史数据存在重复日期，请核对数据源')
    selected = [i for i, r in enumerate(rows) if request.startDate <= r.date <= request.endDate]
    if len(selected) < 2:
        raise ValueError('回测区间至少需要两个净值点')
    first, last = selected[0], selected[-1]
    if first < 120 + request.disclosureDelay - 1:
        earliest = 120 + request.disclosureDelay - 1
        hint = f'；现有数据最早可从 {rows[earliest].date} 开始' if earliest < len(rows) else '；现有历史长度不足，请补齐数据'
        raise ValueError('区间起点之前不足 120 个已披露净值点，请缩短回测区间' + hint)
    # The whole valuation path must be known; never silently skip missing returns.
    if any(r.dailyGrowthRate is None or r.dailyGrowthRate <= -100 for r in rows[first+1:last+1]):
        raise ValueError('回测区间日收益率缺失或无效，无法可靠估值')
    if any((b.date-a.date).days > 14 for a, b in zip(rows[first:last], rows[first+1:last+1])):
        raise ValueError('回测区间存在超过 14 天的数据间隔')
    raw = [r.model_dump(mode='json') for r in rows]
    cash, lots, pending, trades, curve = request.initialCash, [], [], [], []
    settlement_delay = max(request.settlementDelay, request.disclosureDelay) if policy is not None else request.settlementDelay
    counts, blockers, unmet, skipped = Counter(), Counter(), Counter(), Counter()
    synthetic = 1.0
    buy_hold_units = request.initialCash / (1 + request.buyFee / 100)
    buy_hold_fee = request.initialCash - buy_hold_units
    fees = invested_days = analyzed = 0
    initial_amount = cash * request.initialPositionPercent / 100
    if initial_amount:
        initial_units = initial_amount / (1 + request.buyFee / 100)
        fees = initial_amount - initial_units
        cash -= initial_amount
        lots.append(dict(units=initial_units, bought=rows[first].date, confirmed=first+request.confirmDelay))
        trades.append(dict(signalDate=rows[first].date.isoformat(), navAsOf=None, executionDate=rows[first].date.isoformat(),
                           action='INITIAL_BUY', amount=round(initial_amount, 4), fee=round(fees, 4), unitNav=rows[first].nav,
                           syntheticPrice=1, units=round(initial_units, 8), settlementDate=None,
                           reason='按期初建仓假设买入，不属于技术信号；从本日起计算持有期及确认延迟。', evidence=[]))
    peak = bench_peak = request.initialCash
    previous_action = None
    signal_days = []
    price_path = [None] * len(rows)
    price_path[first] = 1.0
    for j in range(first+1, last+1):
        price_path[j] = price_path[j-1] * (1 + rows[j].dailyGrowthRate / 100)
    pending_estimates = {}
    for lot in lots:
        lot.update(boughtIndex=first, bookValue=lot['units'])
    for i in selected:
        row = rows[i]
        if i != first:
            synthetic *= 1 + row.dailyGrowthRate / 100
        if not isfinite(synthetic) or synthetic <= 0:
            raise ValueError('复权估值异常')
        # Proceeds remain assets while awaiting settlement, but cannot fund purchases.
        cash += sum(amount for due, amount in pending if due <= i)
        pending = [(due, amount) for due, amount in pending if due > i]
        known = i - request.disclosureDelay
        evaluation = datetime.combine(row.date, time(10))
        args = (raw[:known+1], request.fundType, evaluation, row.date.isoformat() in request.tradingDates)
        plan = None
        if policy is None:
            signal = analyze(*args) if request.ruleVersion == VERSION else analyze(*args, rule_version=request.ruleVersion)
        else:
            known_price = price_path[known] if known >= first else None
            known_holding = sum(l['units']*known_price if l['boughtIndex'] <= known else l['bookValue'] for l in lots) if known_price is not None else 0.
            signal, plan = policy.decide(*args, index=i, cash=cash, units=sum(l['units'] for l in lots),
                eligible=sum(l['units'] for l in lots if l['confirmed'] <= i and l['boughtIndex'] <= known),
                unsettled=sum(amount if due-settlement_delay <= known else pending_estimates[due] for due, amount in pending),
                known_price=known_price, holding_value=known_holding,
                buy_percent=request.buyPercent, sell_percent=request.sellPercent, fee_rate=request.buyFee/100)

        signal['blockers'] = [b.replace('刷新后再分析。', '该历史观察日暂停分析。').replace('今日', '该历史观察日') for b in signal['blockers']]
        action = signal['action']
        counts[action] += 1
        if action == 'UNAVAILABLE':
            blockers.update(signal['blockers'])
        else:
            analyzed += 1
            unmet.update(k for k, value in signal['conditions'].items() if not value)
        record = dict(date=row.date.isoformat(), asOf=signal['asOf'], action=action,
                      conditions=signal['conditions'], blockers=signal['blockers'])
        if plan is not None:
            record.update(plan)
        signal_days.append(record)
        # A continuous BUY/REDUCE is one signal, not a daily instruction to churn.
        if action in ('BUY', 'REDUCE') and (policy is not None or action != previous_action):
            gross = fee = quantity = 0.0
            if action == 'BUY':
                gross = plan['buyBudget'] if plan is not None else cash * request.buyPercent / 100
                if gross >= 1:
                    net = gross / (1 + request.buyFee / 100)
                    fee = gross - net
                    quantity = net / synthetic
                    lots.append(dict(units=quantity, bought=row.date, confirmed=i+request.confirmDelay, boughtIndex=i, bookValue=net))
                    cash -= gross
                else:
                    skipped['可用现金不足 1 元'] += 1
                    gross = 0
            else:
                eligible = sum(l['units'] for l in lots if l['confirmed'] <= i)
                remaining = plan['sellUnits'] if plan is not None else eligible * request.sellPercent / 100
                if remaining * synthetic < 1:
                    skipped['无足够已确认份额'] += 1
                    remaining = 0
                for lot in lots:
                    if lot['confirmed'] > i or remaining <= 0:
                        continue
                    qty = min(remaining, lot['units'])
                    age = (row.date - lot['bought']).days
                    rate = request.shortSellFee if age < 7 else request.mediumSellFee if age < 30 else request.sellFee
                    amount = qty * synthetic
                    gross += amount
                    fee += amount * rate / 100
                    quantity += qty
                    lot['units'] -= qty
                    remaining -= qty
                lots = [l for l in lots if l['units'] > 1e-10]
                if gross:
                    pending.append((i+settlement_delay, gross-fee))
                    if policy is not None:
                        pending_estimates[i+settlement_delay] = (gross-fee)/synthetic*known_price
            if gross:
                fees += fee
                if policy is not None:
                    policy.executed(i)
                trades.append(dict(signalDate=row.date.isoformat(), navAsOf=signal['asOf'], executionDate=row.date.isoformat(),
                                   action=action, amount=round(gross, 4), fee=round(fee, 4), unitNav=row.nav,
                                   syntheticPrice=round(synthetic, 8), units=round(quantity, 8),
                                   settlementDate=rows[i+settlement_delay].date.isoformat() if action == 'REDUCE' and i+settlement_delay <= last else None,
                                   reason=signal['explanation'], evidence=signal['evidence']))
        previous_action = action
        holding = sum(l['units'] for l in lots) * synthetic
        unsettled = sum(amount for _, amount in pending)
        equity = cash + unsettled + holding
        benchmark = buy_hold_units * synthetic
        peak, bench_peak = max(peak, equity), max(bench_peak, benchmark)
        invested_days += holding / equity if equity > 0 else 0
        curve.append(dict(date=row.date.isoformat(), equity=round(equity, 4), benchmark=round(benchmark, 4),
                          returnRate=(equity/request.initialCash-1)*100, benchmarkReturnRate=(benchmark/request.initialCash-1)*100,
                          drawdown=(equity/peak-1)*100, benchmarkDrawdown=(benchmark/bench_peak-1)*100,
                          cash=round(cash, 4), unsettled=round(unsettled, 4), holding=round(holding, 4)))
    days = (rows[last].date - rows[first].date).days
    total_return = curve[-1]['returnRate']
    return dict(ruleVersion=policy.version if policy is not None else request.ruleVersion, engineVersion='NAV-REPLAY v3.1' if policy is not None else 'NAV-REPLAY v2', fundCode=request.fundCode, fundType=request.fundType,
                validationStatus='SIMULATED' if analyzed else 'NO_ELIGIBLE_DAYS',
                requestedStart=request.startDate.isoformat(), requestedEnd=request.endDate.isoformat(),
                startDate=rows[first].date.isoformat(), endDate=rows[last].date.isoformat(),
                parameters=request.model_dump(mode='json', exclude={'rows', 'tradingDates'}),
                **(dict(ruleId=policy.config.name, ruleParameters=policy.parameters()) if policy is not None else {}),
                dataHash=sha256(dumps(raw, sort_keys=True).encode()).hexdigest(),
                source=' / '.join(dict.fromkeys(r.sourceName or '基金历史净值接口' for r in rows)),
                metrics=dict(totalReturn=total_return, benchmarkReturn=curve[-1]['benchmarkReturnRate'],
                             excessReturn=total_return-curve[-1]['benchmarkReturnRate'],
                             annualReturn=((curve[-1]['equity']/request.initialCash)**(365/days)-1)*100 if days >= 365 else None,
                             maxDrawdown=min(p['drawdown'] for p in curve), benchmarkMaxDrawdown=min(p['benchmarkDrawdown'] for p in curve),
                             tradeCount=len(trades), totalFees=round(fees, 4), benchmarkFees=round(buy_hold_fee, 4),
                             initialTradeCount=int(initial_amount > 0), signalTradeCount=len(trades)-int(initial_amount > 0),
                             exposure=invested_days/len(curve)*100, analyzedDays=analyzed, totalDays=len(curve)),
                signalCounts=dict(counts), unmetConditions=dict(unmet), blockedReasons=dict(blockers), skippedTrades=dict(skipped),
                curve=curve, trades=trades, signals=signal_days,
                assumptions=[
                    f'缺少历史实际披露时刻，按延迟 {request.disclosureDelay} 个净值观察日可见处理；当日 10:00 生成信号，按当日尚未知的正式净值模拟成交。',
                    f'确认延迟 {request.confirmDelay}、赎回到账延迟 {request.settlementDelay} 均以净值观察日计；不等同于基金真实开放日，QDII 尤须核对。',
                    '收益以公布日收益率构造再投资净值指数，份额为模拟复权单位；并非真实登记份额。买入持有使用相同初始资金、起点和申购费。',
                    f'期初按资金 {request.initialPositionPercent:g}% 模拟建仓并扣申购费，其余为现金；不是导入当前真实持仓。期初建仓单列，不计作规则买入信号，持有期从回测起点计算。',
                    policy.description if policy is not None else '买入按可用现金比例、减仓按已确认份额比例；持续同方向信号仅首次交易，信号改变后可重新触发。赎回采用先进先出及分档费率。',
                    '期末未强制清仓，净资产含持仓及待到账赎回款；收益已扣已发生费用，未扣期末假设赎回费。',
                    '市场日历采用系统配置的 A 股日历；净值数据可能被事后修订，未模拟申赎暂停、限额、实际分红到账或逐基金交易日历。',
                    '此结果是指定假设下的历史模拟，不是规则已通过样本外验证或未来收益保证；单基金表现不能证明策略普遍有效。'])



def compare_rules(request, selected_result=None):
    """Fixed candidate, no parameter search; chronological tail restarts both portfolios equally."""
    dates = [r.date for r in sorted(request.rows, key=lambda r: r.date) if request.startDate <= r.date <= request.endDate]
    split = int(len(dates) * .7)
    enough = split >= 60 and len(dates)-split >= 40
    segments = [('FULL', request)]
    if enough:
        segments += [('REFERENCE', request.model_copy(update={'endDate': dates[split-1]})),
                     ('HOLDOUT', request.model_copy(update={'startDate': dates[split]}))]
    results = []
    for name, segment in segments:
        for version in ((VERSION, BALANCED_VERSION, APPROVED_VERSION) if request.ruleVersion == APPROVED_VERSION else (VERSION, BALANCED_VERSION)):
            result = selected_result if name == 'FULL' and version == request.ruleVersion and selected_result else replay(segment.model_copy(update={'ruleVersion': version}))
            results.append(dict(segment=name, ruleVersion=version, startDate=result['startDate'], endDate=result['endDate'],
                                validationStatus=result['validationStatus'], metrics=result['metrics']))
    return dict(status='AVAILABLE' if enough else 'INSUFFICIENT', results=results,
                method='固定版本对照（选择 v3.1 时含 v1 / v2 / v3.1），不自动寻优；按观察日切分前 70% 参考段、后 30% 留出段（至少 40 点）。各段以相同资金及期初仓位重新开始，指标只读取当时已披露净值。历史后段验证不等于未来样本外业绩。',
                candidate='v3.1 使用 MA70 ±2%、3 点确认、20% 防守底仓、18% 回撤刹车与 MA20 恢复确认；偏离目标 >5pp、成交间隔至少 3 点。v2 保留 MA20/MA60 中期趋势、MACD 与 RSI 45–70 / 乖离 ≤5% 过滤；取消 MA5/MA10 排列与周线硬门槛；减仓改为跌破 MA20、MA20 下行且 DIF < DEA。QDII 允许 2 个工作日、最多 6 个自然日的披露滞后。',
                adoption='今日推荐已按用户审核启用 NAV-TA v3.1-trend / T70-B2-F20-S18-I3。此处切换回测版本不会改变今日推荐；历史对照不是独立样本外验证。')
