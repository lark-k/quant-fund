"""Exploratory complete grid: transparent post-diagnostic comparison, not a blind test."""
import csv, json, argparse
from pathlib import Path
from collections import defaultdict
from statistics import median
from dataclasses import asdict
from research.run_nav_research import request, summarize, score
from research.replay import replay
from research.trend import candidates, Policy, Config


def main(legacy=False):
    groups = defaultdict(list)
    for r in csv.DictReader(open('../output/nav-research-2026-10-01/nav-snapshot.csv', encoding='utf-8-sig')):
        code = r.pop('fundCode'); r['nav'] = float(r['nav']); r['dailyGrowthRate'] = float(r['dailyGrowthRate']) if r['dailyGrowthRate'] else None
        groups[code].append(r)
    cache = {code: {} for code in groups}
    results = []
    # Round two is openly exploratory: examine the neighborhood of the risk-limited design.
    grid = list(dict.fromkeys(candidates() + [Config(period=p, buffer=.02, floor=.2, stop=s, interval=i)
                         for p in (50, 60, 70) for s in (.15, .18, .20, .22) for i in (3, 5)]))
    for c in grid:
        full, tail = {}, {}
        for code, rows in groups.items():
            full[code] = summarize(replay(request(code, rows, '2025-10-01', '2026-09-30'), Policy(c, 1., cache[code], recovery_confirmation=not legacy)))
            tail[code] = summarize(replay(request(code, rows, '2026-07-01', '2026-09-30'), Policy(c, 1., cache[code], recovery_confirmation=not legacy)))
        metrics = [r['metrics'] for r in full.values()]
        result = dict(name=c.name, policyVersion='v3.0' if legacy else 'v3.1', config=asdict(c), full=full, tail=tail, score=score(metrics),
                      meanReturn=sum(m['totalReturn'] for m in metrics)/len(metrics), worstDrawdown=min(m['maxDrawdown'] for m in metrics),
                      medianDrawdown=median(m['maxDrawdown'] for m in metrics))
        results.append(result)
        print(c.name, *(round(result[k],2) for k in ('score','meanReturn','medianDrawdown','worstDrawdown')), flush=True)
    filename = 'round2-grid.json' if legacy else 'exploratory-grid.json'
    (Path('../output/nav-research-2026-10-01')/filename).write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--legacy-reentry', action='store_true')
    main(parser.parse_args().legacy_reentry)
