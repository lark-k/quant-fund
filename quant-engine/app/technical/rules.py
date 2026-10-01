"""NAV-TA v1: shared by live analysis and historical replay, independent of QuantRuleEngine."""
from datetime import date, datetime, timedelta
from math import isfinite

VERSION = 'NAV-TA v1'
BALANCED_VERSION = 'NAV-TA v2-balanced'
SUPPORTED = {'ACTIVE_EQUITY', 'MIXED', 'INDEX', 'INDEX_ENHANCED', 'ETF', 'ETF_LINK', 'QDII'}


def week(d):
    return d - timedelta(days=d.weekday())


def ema(values, period):
    previous, count, result = None, 0, []
    for v in values:
        if v is None:
            previous, count = None, 0
            result.append(None)
            continue
        previous = v if previous is None else v * 2 / (period + 1) + previous * (1 - 2 / (period + 1))
        count += 1
        result.append(previous if count >= period else None)
    return result


def analyze(rows, fund_type, now, trading=True, rule_version=VERSION):
    if rule_version not in (VERSION, BALANCED_VERSION):
        raise ValueError('未知技术规则版本')
    balanced = rule_version == BALANCED_VERSION
    today = now.date()
    result = dict(action='UNAVAILABLE', title='暂无法完成技术分析', explanation='以下条件尚未满足，当前未生成买卖结论。',
                  asOf='--', evaluatedAt=now.strftime('%Y-%m-%d %H:%M:%S'), sampleCount=0,
                  source='--', evidence=[], blockers=[], notes=[], ruleVersion=rule_version, conditions={})
    block = result['blockers'].append
    ft = (fund_type or '').strip().upper()
    if not ft or ft == 'UNKNOWN':
        block('基金类型资料缺失或为 UNKNOWN，暂无法确认分析适用性。')
    elif any(x in ft for x in ['债', '货币', '理财', '固收', 'FOF', 'REIT', 'BOND', 'FIXED_INCOME', 'MONEY']):
        block(f'基金类型「{fund_type}」不适用当前权益趋势规则。')
    elif ft not in SUPPORTED and not any(x in ft for x in ['股票', '混合', '指数', '权益', 'QDII']):
        block(f'基金类型「{fund_type}」尚未识别，暂无法确认分析适用性。')
    if not trading:
        block('系统日历显示今日为 A 股非交易日，本功能暂停今日买卖建议。')
    seen = {}
    for row in rows:
        try:
            d = date.fromisoformat(row['date'])
            nav = row['nav']
            if not isfinite(nav) or nav <= 0:
                raise ValueError()
        except (ValueError, TypeError, KeyError):
            block('历史净值包含无效日期或数值。')
            break
        if d > today:
            block('历史净值含未来日期，无法形成当前建议。')
            break
        prior = seen.get(row['date'])
        if prior and (prior['nav'] != nav or prior.get('dailyGrowthRate') != row.get('dailyGrowthRate')):
            block('同一日期的净值记录不一致，请先核对数据源。')
            break
        seen[row['date']] = row
    rows = sorted(seen.values(), key=lambda r: r['date'])[-120:]
    result['sampleCount'] = len(rows)
    if not rows:
        block('没有可用的正式净值。')
        return result
    latest = rows[-1]
    result['asOf'] = latest['date']
    result['source'] = ' / '.join(dict.fromkeys(r.get('sourceName') or '基金历史净值接口' for r in rows))
    lag = (today - date.fromisoformat(latest['date'])).days
    weekdays = sum((today - timedelta(days=i)).weekday() < 5 for i in range(max(0, min(lag, 7))))
    qdii_delay = balanced and 'QDII' in ft
    if lag > (6 if qdii_delay else 4) or weekdays > (2 if qdii_delay else 1):
        block(f"最新净值为 {latest['date']}，超过本功能的新鲜度门槛；刷新后再分析。")
    if len(rows) < 120:
        block(f'仅有 {len(rows)} 个日净值点，至少需要 120 个用于均线、MACD 与周线分析。')
    if any((date.fromisoformat(b['date']) - date.fromisoformat(a['date'])).days > 14 for a, b in zip(rows, rows[1:])):
        block('分析窗口内存在超过 14 天的数据间隔，无法确认走势连续性。')
    if any(r.get('dailyGrowthRate') is None or not isfinite(r['dailyGrowthRate']) or r['dailyGrowthRate'] <= -100 for r in rows[1:]):
        block('日收益率缺失或无效，不能可靠复权；不以原始净值跳变代替交易信号。')
    if result['blockers']:
        return result
    values = [latest['nav']]
    for r in reversed(rows[1:]):
        values.append(values[-1] / (1 + r['dailyGrowthRate'] / 100))
    values.reverse()
    if any(not isfinite(v) or v <= 0 for v in values):
        block('复权净值计算不可用。')
        return result
    ma = lambda length, offset=0: sum(values[len(values)-offset-length:len(values)-offset]) / length
    price, ma5, ma10, ma20, ma60 = values[-1], ma(5), ma(10), ma(20), ma(60)
    slope, bias = (ma20 / ma(20, 5) - 1) * 100, (price / ma20 - 1) * 100
    fast, slow = ema(values, 12), ema(values, 26)
    difs = [a-b if a is not None and b is not None else None for a, b in zip(fast, slow)]
    deas = ema(difs, 9)
    dif, dea = difs[-1], deas[-1]
    gain = loss = 0
    for i in range(1, len(values)):
        change = values[i] - values[i-1]
        if i <= 14:
            gain += max(change, 0) / 14
            loss += max(-change, 0) / 14
        else:
            gain = (gain * 13 + max(change, 0)) / 14
            loss = (loss * 13 + max(-change, 0)) / 14
    strength = 50 if gain + loss == 0 else gain / (gain + loss) * 100
    weeks = {}
    for r, v in zip(rows, values):
        key = week(date.fromisoformat(r['date']))
        weeks.setdefault(key, []).append((r['date'], v))
    keys = sorted(k for k in weeks if week(date.fromisoformat(rows[0]['date'])) < k < week(today))
    week_valid = len(keys) >= 2 and keys[-1] == week(today) - timedelta(days=7) and keys[-1] - keys[-2] == timedelta(days=7) and all(len(weeks[k]) >= 3 for k in keys[-2:])
    if not week_valid and not balanced:
        block('最近两个完整周的净值覆盖不足，无法确认周 K 趋势。')
        return result
    current, previous = (weeks[keys[-1]], weeks[keys[-2]]) if week_valid else ([], [])
    # v2 keeps weekly information as corroboration, not a veto on daily risk control.
    if not week_valid:
        current = previous = [(latest['date'], price)]
    wopen, close = current[0][1], current[-1][1]
    low, high = min(v for _, v in current), max(v for _, v in current)
    trend_up = price > ma20 > ma60 and ma5 > ma10 > ma20 and slope > 0
    trend_down = price < ma20 < ma60 and ma5 < ma10 < ma20 and slope < 0
    momentum_up, momentum_down = dif > dea and dif > 0, dif < dea and dif < 0
    if balanced:
        trend_up = price > ma20 > ma60 and slope > 0
        trend_down = price < ma20 and slope < 0
        momentum_down = dif < dea
    week_up = close > wopen and low > min(v for _, v in previous) and close > previous[-1][1]
    week_down = close < wopen and high < max(v for _, v in previous) and close < previous[-1][1]
    not_chasing = 45 <= strength <= 70 and bias <= 5
    result['conditions'] = dict(buyTrend=trend_up, buyMomentum=momentum_up, buyWeek=week_up, buyFilter=not_chasing,
                                sellTrend=trend_down, sellMomentum=momentum_down, sellWeek=week_down)
    if balanced:
        # Only hard gates appear in unmet-condition counts.
        result['conditions'].pop('buyWeek')
        result['conditions'].pop('sellWeek')
    tone = lambda up, down: 'bull' if up else 'bear' if down else 'neutral'
    cross = '最新点金叉' if difs[-2] <= deas[-2] and dif > dea else '最新点死叉' if difs[-2] >= deas[-2] and dif < dea else '最新点无交叉'
    result['evidence'] = [
        dict(label='日线 / 均线', value=f'净值 {price:.4f} · MA5 {ma5:.4f} · MA10 {ma10:.4f} · MA20 {ma20:.4f} · MA60 {ma60:.4f}', tone=tone(trend_up, trend_down), explanation=f'MA20 相对 5 个净值点前 {slope:+.2f}%；' + ('满足多头排列与向上斜率。' if trend_up else '满足空头排列与向下斜率。' if trend_down else '未形成规则要求的完整单向趋势。')),
        dict(label='MACD（12, 26, 9）', value=f'DIF {dif:.4f} · DEA {dea:.4f} · 柱 {2*(dif-dea):.4f}', tone=tone(momentum_up, momentum_down), explanation=cross + '；' + ('DIF 在零轴上方且高于 DEA，动量偏强。' if momentum_up else 'DIF 在零轴下方且低于 DEA，动量偏弱。' if momentum_down else '零轴位置与两线关系未形成同向确认。')),
        dict(label='完整周 K（日净值聚合）', value=f'{current[0][0]} — {current[-1][0]} · 首 {wopen:.4f} / 末 {close:.4f} / 低 {low:.4f} / 高 {high:.4f}', tone=tone(week_up, week_down), explanation=f'对比前周 {previous[0][0]} — {previous[-1][0]}；' + ('上涨，低点与收盘抬高。' if week_up else '下跌，高点及收盘降低。' if week_down else '未形成周线同向确认。') + '当前未结束周不参与。'),
        dict(label='RSI / 追高检查', value=f'RSI14 {strength:.2f} · 距 MA20 {bias:+.2f}%', tone='neutral', explanation='达到本规则的偏热门槛，暂停新增买入；不单凭超买要求卖出。' if strength > 70 or bias > 5 else 'RSI 尚未达到本规则的买入确认区间；超卖不代表必然反弹。' if strength < 45 else '满足 RSI 45–70、正乖离不超过 5% 的买入过滤条件。')]
    if balanced:
        result['evidence'][0]['explanation'] = f'MA20 相对 5 个净值点前 {slope:+.2f}%；买入需净值 > MA20 > MA60 且 MA20 上行；减仓需净值 < MA20 且 MA20 下行。'
        result['evidence'][1]['explanation'] = cross + '；买入需 DIF > DEA 且 DIF > 0；减仓只需 DIF < DEA，不等待跌破零轴。'
        if not week_valid:
            result['evidence'][2].update(value='最近两个完整周覆盖不足', tone='neutral', explanation='周线仅供参考，不以不完整周线生成确认信号。')
        else:
            result['evidence'][2]['explanation'] += ' v2 周线仅供参考，不作为买卖硬性条件。'
    if trend_up and momentum_up and (balanced or week_up) and not_chasing:
        result.update(action='BUY', title='可考虑分批买入', explanation='日线均线、MACD 与完整周 K 同向偏强，且未触发追高过滤。若投资期限和仓位计划允许，可考虑分批布局。')
    elif trend_down and momentum_down and (balanced or week_down):
        result.update(action='REDUCE', title='可考虑减仓', explanation='均线趋势、MACD 与完整周 K 同向偏弱。已有持仓可结合风险计划考虑减仓，操作前核对持有期和赎回费用。')
    elif trend_up:
        result.update(action='HOLD', title='持有观察，暂不加仓', explanation='均线趋势偏强，但 RSI 或乖离未通过买入过滤；暂不追涨。' if not not_chasing else '均线趋势偏强，但 MACD 或完整周 K 尚未共同确认，等待下一次正式净值。')
    else:
        result.update(action='WATCH', title='观望，暂不交易', explanation='当前指标未同时满足买入或减仓规则，趋势与动量证据不够一致，不根据单一涨跌或交叉作出交易判断。')
    if balanced:
        result['explanation'] = {
            'BUY': '中期均线上行、净值站上 MA20、MACD 偏强，且 RSI 45–70、正乖离不超过 5%；候选规则触发分批买入。',
            'REDUCE': '净值低于 MA20、MA20 下行且 DIF 低于 DEA；候选规则提前减仓，不等待周线或 MACD 零轴确认。',
            'HOLD': '中期趋势向上，但 MACD 或追高过滤未通过，暂不新增买入。',
            'WATCH': '日线趋势与动量尚未同时满足候选规则的买入或减仓条件。'
        }[result['action']]
    result['notes'] = [f"以 {latest['date']} 正式净值为依据，不含盘中估值。" + ('这不是今日收盘后的信号。' if latest['date'] != today.isoformat() else ''),
                       '技术指标均来源于同一净值序列，并非独立预测；无法保证后续走势。',
                       '未纳入个人风险承受能力、资金需求、申赎限制及费用，不给出具体金额、仓位比例或保证收益。']
    if now.hour >= 15:
        result['notes'].append('北京时间已过 15:00；今日提交可能按下一开放日受理，具体以基金和平台截止时间为准。')
    return result
