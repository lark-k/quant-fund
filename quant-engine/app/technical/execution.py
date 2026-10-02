"""Read-only live sizing against the per-fund cash book, never the account cash total."""
from datetime import date
from decimal import Decimal, ROUND_DOWN, ROUND_HALF_UP


def amount(value, digits=2, down=False):
    return float(Decimal(str(value)).quantize(Decimal(10) ** -digits, rounding=ROUND_DOWN if down else ROUND_HALF_UP))


def attach_execution(signal, policy, rows, fund_type, now, trading, ledger):
    """Retains the approved trend policy. Pending trades block a second instruction."""
    cash, units = ledger['cashBalance'], ledger['holdingShares']
    price = rows[-1]['nav']
    holding = units * price
    total = holding + cash
    last = ledger.get('lastTradeDate')
    elapsed = sum(r['date'] > last for r in rows) if last else None
    known_weight = holding / total * 100 if total > 0 and cash >= 0 else None
    detail = dict(cashBalance=amount(cash), holdingShares=amount(units, 4, True), holdingAmount=amount(holding),
                  referenceNav=price, navDate=rows[-1]['date'], strategyAssets=amount(total),
                  currentWeight=known_weight, targetWeight=policy.target*100, pendingTrades=ledger['pendingTrades'],
                  lastTradeDate=last, observationsSinceTrade=elapsed, requiredInterval=policy.config.interval,
                  snapshotVersion=ledger['snapshotVersion'], snapshotAt=ledger['snapshotAt'],
                  suggestedAmount=0., suggestedShares=0., estimatedHoldingAfter=amount(holding),
                  estimatedCashAfter=amount(cash), direction='NONE', feeIncluded=False,
                  status='WAIT', reason='', sizingVersion='FUND-CASH v1')
    status, reason, action = 'READY', '', 'HOLD' if units else 'WATCH'
    if ledger['pendingTrades']:
        status, reason = 'PENDING_TRADE', f"该基金有 {ledger['pendingTrades']} 笔待确认或关联转换交易，完成后重新分析，避免重复操作。"
    elif cash < 0:
        status, reason = 'CASH_DEFICIT', '该基金现金存在待补差额，请先在首页现金分配中核对补充，再计算执行金额。'
    elif units < 0 or (units == 0 and ledger['holdingAmount'] > 0):
        status, reason = 'HOLDING_INCONSISTENT', '持仓金额与已确认份额不一致，请先核对持仓，暂不推算可赎回份额。'
    elif last and (date.fromisoformat(last) > now.date() or elapsed < policy.config.interval):
        status, reason = 'COOLDOWN', f"距最近完成交易新增 {elapsed} 个净值观察点，须达到 {policy.config.interval} 点后再调仓。"
    elif total <= 0:
        status, reason = 'NO_CAPITAL', '尚无持仓或该基金可用现金，请先在首页分配资金。'
    elif abs(policy.target*100-known_weight) <= policy.config.band*100 + 1e-9:
        status, reason = 'AT_TARGET', '当前仓位已在目标 ±5 个百分点范围内，建议保持。'
    elif known_weight < policy.target*100 and (policy.target == policy.config.floor or not signal['trendState']['bull']):
        status, reason = 'WAIT_TREND', '当前未形成进攻趋势确认，暂不新增买入；保留已分配现金等待趋势恢复。'
    else:
        # An all-in purchase budget is capped by cash; actual fees reduce acquired shares.
        sized, plan = policy.decide(rows, fund_type, now, trading, index=len(rows), cash=cash, units=units,
                                   eligible=units, unsettled=0, known_price=price, buy_percent=50,
                                   sell_percent=50, fee_rate=0, enforce_timing=False)
        action = sized['action']
        if action == 'BUY':
            budget = amount(plan['buyBudget'], down=True)
            detail.update(direction='BUY', suggestedAmount=budget,
                          estimatedHoldingAfter=amount(holding+budget), estimatedCashAfter=amount(cash-budget))
            reason = f"当前仓位 {known_weight:.2f}%，目标 {policy.target:.0%}；本次买入预算 ¥{budget:.2f}，不超过该基金可用现金的 50%。"
        elif action == 'REDUCE':
            shares = amount(plan['sellUnits'], 4, True)
            proceeds = amount(Decimal(str(shares))*Decimal(str(price)), down=True)
            detail.update(direction='SELL', suggestedAmount=proceeds, suggestedShares=shares,
                          estimatedHoldingAfter=amount(holding-shares*price), estimatedCashAfter=amount(cash+shares*price))
            reason = f"当前仓位 {known_weight:.2f}%，向 {policy.target:.0%} 目标分批减仓；建议赎回 {shares:.4f} 份，按最新正式净值参考 ¥{proceeds:.2f}。"
        else:
            status, reason = 'BELOW_MINIMUM', '按目标计算的本次调整不足 1 元，暂不操作。'
        if detail['direction'] != 'NONE' and detail['suggestedAmount'] < 1:
            status, reason, action = 'BELOW_MINIMUM', '按金额和份额精度取整后不足 1 元，暂不操作。', 'HOLD' if units else 'WATCH'
            detail.update(direction='NONE', suggestedAmount=0., suggestedShares=0., estimatedHoldingAfter=amount(holding), estimatedCashAfter=amount(cash))
    detail.update(status=status, reason=reason)
    signal.update(action=action, title=(f"建议买入 ¥{detail['suggestedAmount']:.2f}" if detail['direction']=='BUY'
                  else f"建议减仓约 ¥{detail['suggestedAmount']:.2f}" if detail['direction']=='SELL' else '暂不操作，保留当前持仓与现金'),
                  explanation=reason, execution=detail, executionStatus=status,
                  executionReady=detail['direction']!='NONE')
    signal['evidence'] = [e for e in signal['evidence'] if e['label']!='执行条件待核对']
    signal['evidence'].insert(2, dict(label='持仓与资金核对', value=f"持仓参考 ¥{holding:.2f} · 基金现金 ¥{cash:.2f}",
        tone='neutral', explanation=reason))
    signal['notes'] += ['金额来自本账户同基金的持仓与专属现金；未分配现金及其他基金现金不参与。待确认交易完成前不生成新执行金额。',
                        '买入金额为含费用的支出预算；卖出金额与调整后持仓按最新正式净值估算，未扣实际费用。实际成交净值、可赎回份额和费用以平台为准。',
                        '最近成交日按交易日期与系统完成更新时间中较晚者核对，间隔只计算此后新增的净值点。查询及修改预览金额不产生交易。']
    return signal
