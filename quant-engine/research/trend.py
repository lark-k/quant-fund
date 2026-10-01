"""Predeclared trend-target candidates. No RSI ceiling, no recursive position halving."""
from dataclasses import dataclass, asdict
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


def candidates():
    # Fixed before scoring: 12 hysteresis candidates and 8 with an extra drawdown brake.
    return [Config(period=p, buffer=b, floor=f) for p in (40, 60, 80) for b in (.02, .04) for f in (0., .2)] + [
        Config(period=p, buffer=.02, floor=f, stop=s) for p in (40, 60) for f in (0., .2) for s in (.15, .2)]


class Policy:
    version = 'NAV-TA v3.1-trend-research'

    def __init__(self, config, initial_position=1.0, cache=None, *, recovery_confirmation=True):
        self.config = config
        self.target = initial_position
        self.last_trade = -10000
        self.braked = False
        self.recovery_confirmation = recovery_confirmation
        self.version = 'NAV-TA v3.1-trend-research' if recovery_confirmation else 'NAV-TA v3.0-trend-research'
        self.cache = cache if cache is not None else {}

    @property
    def description(self):
        c = self.config
        return (f'研究方案 {c.name}：{c.confirm} 点趋势确认，目标仓位 {c.floor:.0%} / {c.ceiling:.0%}；'
                f'仓位偏离超过 {c.band:.0%} 且距上次成交至少 {c.interval} 个观察日才调仓。'
                '每次买卖比例是上限，不连续减半低于目标；未成交不消耗等待间隔。委托金额/份额只使用已披露净值估值。')

    def executed(self, index):
        self.last_trade = index

    def decide(self, rows, fund_type, now, trading, *, index, cash, units, eligible, unsettled,
               known_price, buy_percent, sell_percent, fee_rate, holding_value=None):
        c = self.config
        # Cache is owned by one fund/snapshot/delay research run; never shared across datasets.
        key = (rows[-1]['date'], now.date(), trading, fund_type)
        if key not in self.cache:
            quality = analyze(rows, fund_type, now, trading, rule_version=BALANCED_VERSION)
            values = [rows[-1]['nav']]
            if quality['action'] != 'UNAVAILABLE':
                for r in reversed(rows[-119:]):
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
        signal['evidence'] = [dict(label='趋势目标仓位', value=f'{self.target:.0%}', tone='neutral',
            explanation=f'MA{c.period} {ma(c.period):.4f}，缓冲 {c.buffer:.0%}，确认 {c.confirm} 点；60 点高位回撤 {drawdown:.2%}。')]
        signal.update(action='HOLD', explanation='保持既有趋势目标；短期 MACD 或 RSI 波动不单独触发减仓。')
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
                signal.update(action='BUY', explanation=f'趋势目标 {self.target:.0%}，已披露净值下仓位 {weight:.1%}；分批恢复仓位，单次最多使用 {buy_percent:g}% 可用现金。')
        else:
            sell_units = min(eligible*sell_percent/100, max(0., (holding_value-self.target*equity)/known_price))
            if sell_units*known_price >= 1:
                plan['sellUnits'] = sell_units
                signal.update(action='REDUCE', explanation=f'趋势目标 {self.target:.0%}，已披露净值下仓位 {weight:.1%}；向目标减仓，单次最多卖出 {sell_percent:g}% 已确认份额。')
        return signal, plan

    def parameters(self):
        return asdict(self.config)
