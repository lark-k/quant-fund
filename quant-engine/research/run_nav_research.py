"""Reproducible bounded experiments; run from quant-engine, no server or account writes.

python -m research.run_nav_research --input ../.tmp/nav-rule-research.csv --output ../output/nav-research-2026-10-01
"""
import argparse
import csv
import hashlib
import json
from collections import defaultdict
from datetime import date
from pathlib import Path
from statistics import median
from dataclasses import asdict
from app.api.technical import ReplayRequest
from app.technical.backtest import replay as baseline_replay
from research.replay import replay
from research.trend import Policy, Config, candidates

HOLIDAYS = set('2026-01-01,2026-02-16,2026-02-17,2026-02-18,2026-02-19,2026-02-20,2026-04-06,2026-05-01,2026-05-04,2026-05-05,2026-06-19,2026-09-25,2026-10-01'.split(','))


def request(code, rows, start, end, **params):
    qdii = code in ('012922', '013403')
    earliest = rows[121 if qdii else 120]['date']
    return ReplayRequest(rows=rows, fundCode=code, fundType='QDII' if qdii else 'INDEX' if code == '025833' else 'MIXED',
        startDate=max(start, earliest), endDate=end, tradingDates=[r['date'] for r in rows if date.fromisoformat(r['date']).weekday() < 5 and r['date'] not in HOLIDAYS],
        **dict(dict(initialCash=10000, initialPositionPercent=100, buyPercent=50, sellPercent=50,
                    disclosureDelay=2 if qdii else 1, ruleVersion='NAV-TA v2-balanced'), **params))


def summarize(result):
    return dict(start=result['startDate'], end=result['endDate'], metrics=result['metrics'])


def score(metrics):
    # Frozen objective: rewards return, penalizes median drawdown and worst fund loss.
    returns = [m['totalReturn'] for m in metrics]
    dd = [m['maxDrawdown'] for m in metrics]
    return median(returns) + .75*median(dd) + .25*min(0., min(returns))


def run(input_path, output):
    output.mkdir(parents=True, exist_ok=True)
    groups = defaultdict(list)
    for r in csv.DictReader(input_path.open(encoding='utf-8-sig')):
        code = r.pop('fundCode')
        r['nav'] = float(r['nav'])
        r['dailyGrowthRate'] = float(r['dailyGrowthRate']) if r['dailyGrowthRate'] else None
        if r['date'] <= '2026-09-30':
            groups[code].append(r)
    for rows in groups.values():
        rows.sort(key=lambda r: r['date'])
    protocol = dict(training=['2025-04-01', '2026-03-31'], validation=['2026-04-01', '2026-09-30'],
        candidates=[asdict(c) for c in candidates()], objective='median return + 0.75 * median negative drawdown - 0.25 * worst loss',
        sourceHash=hashlib.sha256(input_path.read_bytes()).hexdigest(),
        caveat='后段历史在先前诊断中已经看过，只能称冻结参数后的时间外复核；不是完全盲测。基金池是当前持仓，有选择/存续偏差。')
    (output/'protocol.json').write_text(json.dumps(protocol, ensure_ascii=False, indent=2), encoding='utf-8')
    train_requests = {}
    for code, rows in groups.items():
        if rows[121]['date'] >= '2026-03-01':
            continue
        req = request(code, rows, *protocol['training'])
        if sum(req.startDate <= r.date <= req.endDate for r in req.rows) >= 120:
            train_requests[code] = req
    caches = {c: {} for c in train_requests}
    rankings = []
    for config in candidates():
        results = {c: summarize(replay(req, Policy(config, req.initialPositionPercent/100, caches[c]))) for c, req in train_requests.items()}
        value = score([r['metrics'] for r in results.values()])
        rankings.append(dict(name=config.name, config=asdict(config), score=value, funds=results))
        print(f'TRAIN {config.name}: {value:.3f}', flush=True)
    rankings.sort(key=lambda r: r['score'], reverse=True)
    winner = Config(**rankings[0]['config'])
    (output/'training-ranking.json').write_text(json.dumps(rankings, ensure_ascii=False, indent=2), encoding='utf-8')
    (output/'frozen-candidate.json').write_text(json.dumps(dict(name=winner.name, parameters=asdict(winner), selectedUsing='TRAINING ONLY'), ensure_ascii=False, indent=2), encoding='utf-8')
    print('FROZEN', winner.name, flush=True)
    # No selection/parameter changes below this line. Report every fund, including losses.
    results = {}
    windows = dict(FULL=('2025-10-01', '2026-09-30'), VALIDATION=tuple(protocol['validation']),
                   RISE=('2026-04-01', '2026-06-30'), FALL=('2026-07-01', '2026-09-30'))
    for code, rows in groups.items():
        results[code] = {}
        cache = {}
        for name, (start, end) in windows.items():
            if rows[121]['date'] >= end:
                continue
            req = request(code, rows, start, end)
            base = baseline_replay(req)
            new = replay(req, Policy(winner, req.initialPositionPercent/100, cache))
            results[code][name] = dict(v2=summarize(base), candidate=summarize(new))
            if name == 'FULL':
                new['researchConfig'] = asdict(winner)
                (output/f'{code}-candidate.json').write_text(json.dumps(new, ensure_ascii=False), encoding='utf-8')
            print(code, name, round(base['metrics']['totalReturn'], 2), round(new['metrics']['totalReturn'], 2), round(new['metrics']['maxDrawdown'], 2), flush=True)
        # Adverse assumptions and cash-start sensitivity: do not optimize fees to improve returns.
        req = request(code, rows, *windows['FULL'])
        stress = req.model_copy(update={'buyFee': min(5., req.buyFee*2), 'shortSellFee': min(5., req.shortSellFee*2),
                                      'mediumSellFee': min(5., req.mediumSellFee*2), 'sellFee': max(.1, req.sellFee*2),
                                      'settlementDelay': req.settlementDelay+2})
        for name, case in [('STRESS', stress), ('CASH_START', req.model_copy(update={'initialPositionPercent': 0}))]:
            results[code][name] = dict(v2=summarize(baseline_replay(case)), candidate=summarize(replay(case, Policy(winner, case.initialPositionPercent/100))))
    summary = dict(protocol=protocol, winner=asdict(winner), training=rankings[0], results=results)
    (output/'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    run(args.input, args.output)
