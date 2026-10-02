"""Predeclared trend-target candidates. No RSI ceiling, no recursive position halving."""
from dataclasses import dataclass, asdict
from datetime import datetime, time
from copy import deepcopy
from app.technical.rules import analyze, BALANCED_VERSION


@dataclass(frozen=True)
class Config:
    period: int = 60
    buffer: float = .02
    floor: float = .2
    stop: float = 1.0  # 1 = disabled; trailing drawdown from 60 disclosed observations.
    confirm: int = 3
    interval: int = 5
    ceiling: float = 1.0
    band: float = .05

    @property
    def name(self):
        return f'T{self.period}-B{self.buffer*100:g}-F{self.floor*100:g}-S{self.stop*100:g}-I{self.interval}'


APPROVED_VERSION = 'NAV-TA v3.1-trend'
APPROVED_CONFIG = Config(period=70, buffer=.02, floor=.2, stop=.18, confirm=3, interval=3)


class Policy:
    version = APPROVED_VERSION

    def __init__(self, config, initial_position=1.0, cache=None, *, recovery_confirmation=True):
        self.config = config
        self.target = initial_position
        self.last_trade = -10000
        self.braked = False
        self.recovery_confirmation = recovery_confirmation
        self.version = APPROVED_VERSION if recovery_confirmation else 'NAV-TA v3.0-trend-research'
        self.cache = cache if cache is not None else {}

    @property
    def description(self):
        c = self.config
        return (f'趋势方案 {c.name}：{c.confirm} 点趋势确认，目标仓位 {c.floor:.0%} / {c.ceiling:.0%}；'
                f'仓位偏离超过 {c.band:.0%} 且距上次成交至少 {c.interval} 个观察日才调仓。'
                '每次买卖比例是上限，不连续减半低于目标；未成交不消耗等待间隔。委托金额/份额只使用已披露净值估值。')

    def executed(self, index):
        self.last_trade = index

    def decide(self, rows, fund_type, now, trading, *, index, cash, units, eligible, unsettled,
               known_price, buy_percent, sell_percent, fee_rate, holding_value=None, enforce_timing=True):
        c = self.config
        # Cache is owned by one fund/snapshot/delay research run; never shared across datasets.
        key = (tuple((r.get('date'), r.get('nav'), r.get('dailyGrowthRate'), r.get('sourceName')) for r in rows), now, trading, fund_type, enforce_timing)
        if key not in self.cache:
            quality = analyze(rows, fund_type, now, trading, rule_version=BALANCED_VERSION, enforce_timing=enforce_timing)
            ordered = sorted({r['date']: r for r in rows}.values(), key=lambda r: r['date'])[-120:]
            values = [ordered[-1]['nav']] if ordered else []
            if quality['action'] != 'UNAVAILABLE':
                for r in reversed(ordered[1:]):
                    values.append(values[-1] / (1 + r['dailyGrowthRate']/100))
                values.reverse()
            self.cache[key] = (quality, values)
        quality, values = self.cache[key]
        signal = deepcopy(quality)
        signal['ruleVersion'] = self.version
        plan = dict(targetWeight=self.target*100, knownWeight=None, buyBudget=0., sellUnits=0., regime='UNAVAILABLE')
        if signal['action'] == 'UNAVAILABLE':
            return signal, plan
        ma = lambda n, off=0: sum(values[len(values)-n-off:len(values)-off])/n
        bull = all(values[-1-o] > ma(c.period, o)*(1+c.buffer) for o in range(c.confirm)) and ma(c.period) > ma(c.period, 5)
        bear = all(values[-1-o] < ma(c.period, o)*(1-c.buffer) for o in range(c.confirm)) and ma(c.period) < ma(c.period, 5)
        drawdown = values[-1]/max(values[-60:])-1
        brake = drawdown <= -c.stop and values[-1] < ma(20)
        recovered = all(values[-1-o] > ma(20, o) for o in range(c.confirm)) and ma(20) > ma(20, 5)
        if brake:
            self.braked = True
        if brake or bear:
            self.target = c.floor
            regime = 'DEFENSIVE'
        elif bull and (not self.recovery_confirmation or not self.braked or recovered):
            self.target = c.ceiling
            self.braked = False
            regime = 'UPTREND'
        else:
            regime = 'KEEP_TARGET'
        signal['conditions'] = dict(trendConfirmed=bull, defenseConfirmed=bear or brake, recoveryConfirmed=not self.braked or recovered)
        signal['trendState'] = dict(bull=bull, bear=bear, brake=brake, braked=self.braked, recovered=recovered, ma=ma(c.period), ma20=ma(20), drawdown=drawdown*100)
        signal['evidence'] = [dict(label='趋势目标仓位', value=f'{self.target:.0%}', tone='neutral',
            explanation=f'MA{c.period} {ma(c.period):.4f}，缓冲 {c.buffer:.0%}，确认 {c.confirm} 点；60 点高位回撤 {drawdown:.2%}。')]
        signal.update(action='HOLD', title='保持目标仓位', explanation='保持既有趋势目标；短期 MACD 或 RSI 波动不单独触发减仓。')
        signal['notes'] = [n for n in signal['notes'] if '不给出具体金额' not in n]
        plan.update(targetWeight=self.target*100, regime=regime)
        # No first-day target sizing with an unobservable execution NAV.
        if known_price is None:
            signal['explanation'] = '起点净值尚未披露，暂不在期初假设之外另行调仓。'
            return signal, plan
        holding_value = units*known_price if holding_value is None else holding_value
        equity = cash + unsettled + holding_value
        weight = holding_value/equity if equity else 0
        plan['knownWeight'] = weight*100
        if index-self.last_trade < c.interval or abs(self.target-weight) <= c.band:
            return signal, plan
        if weight < self.target:
            # Solve target weight after fee using information available at order time.
            net = max(0., (self.target*equity-holding_value)/(1+self.target*fee_rate))
            budget = min(cash*buy_percent/100, net*(1+fee_rate))
            if budget >= 1:
                plan['buyBudget'] = budget
                signal.update(action='BUY', title='按目标分批买入', explanation=f'趋势目标 {self.target:.0%}，已披露净值下仓位 {weight:.1%}；分批恢复仓位，单次最多使用 {buy_percent:g}% 可用现金。')
        else:
            sell_units = min(eligible*sell_percent/100, max(0., (holding_value-self.target*equity)/known_price))
            if sell_units*known_price >= 1:
                plan['sellUnits'] = sell_units
                signal.update(action='REDUCE', title='向防守目标减仓', explanation=f'趋势目标 {self.target:.0%}，已披露净值下仓位 {weight:.1%}；向目标减仓，单次最多卖出 {sell_percent:g}% 已确认份额。')
        return signal, plan

    def parameters(self):
        return asdict(self.config)


RULES = [
    'T70-B2-F20-S18-I3：最近 120 个正式净值点按日收益率前复权；不使用盘中估值。',
    '技术分析可在非交易日查看，按最新已披露净值计算；净值日期较早仅作提示，不阻断查看。回测仍保留原成交日历与披露延迟约束。',
    '进攻：连续 3 点高于各自 MA70 的 102%，且 MA70 高于 5 点前，策略目标仓位 100%。',
    '防守：连续 3 点低于各自 MA70 的 98%，且 MA70 低于 5 点前，目标仓位 20%。',
    '回撤刹车：距最近 60 点最高值回撤至少 18%，且低于 MA20，转为 20%；刹车后恢复还需连续 3 点高于 MA20 且 MA20 高于 5 点前。',
    '未形成新确认时保持既有目标；不因 RSI 偏高或一次 MACD 死叉单独减仓。',
    '执行须偏离目标超过 5 个百分点，且距上次实际成交至少 3 个净值观察日；每次最多使用 50% 可用现金或卖出 50% 已确认份额，向目标调节。查询建议不视为成交。',
    '目标比例相对该基金专属策略资金，非全部账户资金。实时窗口未登记策略现金与执行台账，只提供条件式方向；回测按独立模拟账本执行。',
    '回测默认期初 100%、申购费 0.15%、分档赎回费 1.5% / 0.5% / 0%；披露国内 1 / QDII 2 点，确认 1 点，到账 3 点。这些是假设，可编辑，不是实际费用。',
    '该版本经用户审核启用；历史比较参与过选参，不能视为独立样本外验证。收益与回撤可能均扩大。'
]


def live_analysis(rows, fund_type, now, trading=True, has_holding=True, trading_dates=None):
    """Reconstruct the latched trend state, never infer per-fund cash from account wealth.

    Live NAVs are already disclosed. Historical state uses the same conservative
    observation lag as replay; current decision reads the latest received NAV.
    No fictional transaction is booked when the user requests a recommendation.
    """
    # Viewing an as-of NAV analysis is independent of today's execution calendar.
    # Keep the real evaluation date: future records and other data errors still fail.
    quality = analyze(rows, fund_type, now, trading, rule_version=BALANCED_VERSION, enforce_timing=False)
    quality.update(ruleVersion=APPROVED_VERSION, ruleId=APPROVED_CONFIG.name,
                   ruleParameters=asdict(APPROVED_CONFIG), rules=RULES, executionReady=False)
    if quality['action'] == 'UNAVAILABLE':
        return quality
    ordered = sorted({r['date']: r for r in rows}.values(), key=lambda r: r['date'])
    policy = Policy(APPROVED_CONFIG)
    context = dict(cash=0, units=0, eligible=0, unsettled=0, known_price=None,
                   buy_percent=50, sell_percent=50, fee_rate=.0015)
    delay = 2 if 'QDII' in fund_type.upper() else 1
    for i in range(119+delay, len(ordered)):
        day = datetime.combine(datetime.fromisoformat(ordered[i]['date']).date(), time(10))
        policy.decide(ordered[:i-delay+1], fund_type, day, (ordered[i]['date'] in trading_dates if trading_dates is not None else day.weekday() < 5), index=i, **context)
        policy.cache.clear()
    signal, plan = policy.decide(ordered, fund_type, now, trading, index=len(ordered), enforce_timing=False, **context)
    signal.update(ruleId=APPROVED_CONFIG.name, ruleParameters=asdict(APPROVED_CONFIG), rules=RULES,
                  executionReady=False, targetWeight=plan['targetWeight'], regime=plan['regime'],
                  stateStart=ordered[119]['date'], stateSamples=len(ordered),
                  executionStatus='MISSING_STRATEGY_LEDGER')
    nav_age = (now.date() - datetime.fromisoformat(signal['asOf']).date()).days
    signal['timingNotice'] = f"基于截至 {signal['asOf']} 的已披露净值分析（距查看日 {nav_age} 个自然日），不含此后的行情。"
    if not trading:
        signal['timingNotice'] += ' 当前为系统日历非交易日，可查看分析；申赎受理日以基金及平台为准。'
    if 'QDII' in fund_type.upper():
        signal['timingNotice'] += ' QDII 净值所属日期与公布日期可能不同。'
    state = signal['trendState']
    if policy.target == APPROVED_CONFIG.floor:
        signal.update(action='REDUCE' if has_holding else 'WATCH',
            title='防守目标 20%：超出目标时分批减仓' if has_holding else '防守阶段，等待趋势恢复',
            explanation='若该基金策略仓位高于 25%，且距上次实际成交满 3 个净值观察日，可向 20% 目标分批减仓；已在目标附近则保持，不连续减半。' if has_holding else '当前未登记持仓，不生成卖出指令；20% 为历史策略防守目标，不代表立即新建仓。')
    elif plan['regime'] == 'UPTREND':
        signal.update(action='BUY', title='趋势确认：仓位不足时分批买入',
            explanation='目标 100%。若该基金策略仓位低于 95%，距上次实际成交满 3 个净值观察日，且有专属可用现金，可分批恢复；仓位已达到目标时继续持有。')
    else:
        signal.update(action='HOLD' if has_holding else 'WATCH', title='保持既有目标，等待新确认',
            explanation=f"未形成新的进攻或防守确认，维持历史目标 {plan['targetWeight']:g}%；不因单一 MACD / RSI 波动交易。初始状态按审核方案 100% 重建，不代表当前实际仓位。")
    signal['evidence'] += [
        dict(label='回撤刹车 / 恢复', value=f"60 点回撤 {state['drawdown']:.2f}% · MA20 {state['ma20']:.4f}", tone='bear' if state['braked'] else 'neutral', explanation='刹车状态保留中，需 MA70 与 MA20 共同确认恢复。' if state['braked'] else '未处于刹车锁定状态；18% 回撤且跌破 MA20 才触发刹车。'),
        dict(label='执行条件待核对', value='仓位偏离 >5 个百分点 · 成交间隔 ≥3 点', tone='neutral', explanation='未登记该基金专属现金、待到账款、已确认份额与最近策略成交，不能核实仓位或冷却期。以上为条件式建议，无具体下单金额。'),
        dict(label='MACD / RSI / 周 K', value=quality['evidence'][1]['value'] + ' · ' + quality['evidence'][3]['value'], tone='neutral', explanation='趋势仍成立时不因 RSI 偏高提前卖出；趋势恢复后允许按目标分批重新买入。')]
    signal['notes'] += [f"历史目标从 {signal['stateStart']} 起重建，初始目标 100%；不同起点的回测在确认趋势前可能不同。", '100% / 20% 相对单只基金策略资金，不能直接套用到账户总资产。真实成交需自行核对份额、费用和到账时间。', '历史选参结果并非独立样本外业绩，不保证提高未来收益。']
    return signal
