"""Freeze a review candidate and disclose sensitivities; does not publish a strategy."""
import csv, json, hashlib
from pathlib import Path
from collections import defaultdict
from dataclasses import asdict
from statistics import mean, median
from research.trend import Config, Policy
from research.run_nav_research import request, summarize
from research.replay import replay
from app.technical.backtest import replay as baseline

OUTPUT = Path('../output/nav-research-2026-10-01')
REVIEW = Config(period=70, buffer=.02, floor=.2, stop=.18, confirm=3, interval=3, ceiling=1., band=.05)


def main():
    groups = defaultdict(list)
    source = OUTPUT/'nav-snapshot.csv'
    for r in csv.DictReader(source.open(encoding='utf-8-sig')):
        code=r.pop('fundCode'); r['nav']=float(r['nav']); r['dailyGrowthRate']=float(r['dailyGrowthRate']) if r['dailyGrowthRate'] else None
        groups[code].append(r)
    all_results = {}
    for code, rows in groups.items():
        results = {}
        for name, start, end in [('FULL','2025-10-01','2026-09-30'),('JAN_START','2026-01-01','2026-09-30'),
                                 ('APR_START','2026-04-01','2026-09-30'),('JUL_START','2026-07-01','2026-09-30'),
                                 ('RISE','2026-04-01','2026-06-30')]:
            req = request(code,rows,start,end)
            new = replay(req, Policy(REVIEW,1))
            old = baseline(req)
            results[name] = dict(candidate=summarize(new), v2=summarize(old))
            if name == 'FULL':
                new['researchConfig'] = asdict(REVIEW)
                new['approvalStatus'] = 'AWAITING_USER_REVIEW_NOT_DEPLOYED'
                (OUTPUT/f'{code}-review.json').write_text(json.dumps(new,ensure_ascii=False),encoding='utf-8')
                (OUTPUT/f'{code}-v2-baseline.json').write_text(json.dumps(old,ensure_ascii=False),encoding='utf-8')
        req = request(code,rows,'2025-10-01','2026-09-30')
        cases = dict(COST_STRESS=req.model_copy(update={'buyFee': .3, 'shortSellFee': 3., 'mediumSellFee': 1., 'sellFee': .1, 'settlementDelay': 5}),
                     CASH_START=req.model_copy(update={'initialPositionPercent':0}))
        for name, case in cases.items():
            results[name] = dict(candidate=summarize(replay(case,Policy(REVIEW,case.initialPositionPercent/100))),v2=summarize(baseline(case)))
        all_results[code] = results
        r=results['FULL'];print(code,round(r['candidate']['metrics']['totalReturn'],2),round(r['candidate']['metrics']['maxDrawdown'],2),flush=True)
    summary = dict(version=Policy.version, candidate=REVIEW.name, parameters=asdict(REVIEW),
        execution=dict(initialCash=10000,initialPositionPercent=100,buyPercent=50,sellPercent=50,buyFee=.15,shortSellFee=1.5,mediumSellFee=.5,sellFee=0,confirmDelay=1,settlementDelay=3,disclosureDelay='境内 1 / QDII 2'),
        status='RESEARCH_ONLY_AWAITING_USER_APPROVAL', sourceHash=hashlib.sha256(source.read_bytes()).hexdigest(),
        selection='84 组配置/机制的探索性比较后，选取收益与典型回撤折中；并非未见数据上的最优策略证明。',results=all_results)
    (OUTPUT/'review-candidate.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    for window in next(iter(all_results.values())):
        a=[r[window]['candidate']['metrics'] for r in all_results.values()]
        b=[r[window]['v2']['metrics'] for r in all_results.values()]
        print(window,'return',round(mean(x['totalReturn'] for x in b),2),round(mean(x['totalReturn'] for x in a),2),
              'medianDD',round(median(x['maxDrawdown'] for x in b),2),round(median(x['maxDrawdown'] for x in a),2),flush=True)


if __name__ == '__main__': main()
