"""Render a local, reviewable report from saved numerical results."""
import json
from pathlib import Path
from statistics import mean, median
from datetime import datetime
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
from matplotlib.font_manager import FontProperties

OUT = Path('../output/nav-research-2026-10-01')
NAMES = {'016874':'广发远见智选混合C','017811':'东方人工智能主题混合C','012922':'易方达全球成长精选混合(QDII)C',
         '025833':'天弘电网设备特高压指数C','021528':'财通成长优选混合C','013403':'华夏恒生科技ETF联接(QDII)C','021180':'易方达产业机遇混合C'}


def main():
    summary=json.loads((OUT/'review-candidate.json').read_text(encoding='utf-8'))
    results=summary['results']
    plt.rcParams.update({'font.family':FontProperties(fname='C:/Windows/Fonts/msyh.ttc').get_name(), 'axes.unicode_minus':False,
                         'figure.facecolor':'#101923','axes.facecolor':'#101923','text.color':'#eaf2fc','axes.labelcolor':'#d5e2f2',
                         'xtick.color':'#b9cadb','ytick.color':'#b9cadb','axes.edgecolor':'#456078','font.size':10})
    fig,axes=plt.subplots(4,2,figsize=(16,15),layout='constrained')
    for ax,(code,name) in zip(axes.flat,NAMES.items()):
        candidate=json.loads((OUT/f'{code}-review.json').read_text(encoding='utf-8'))
        base=json.loads((OUT/f'{code}-v2-baseline.json').read_text(encoding='utf-8'))
        dates=[datetime.fromisoformat(p['date']) for p in candidate['curve']]
        ax.plot(dates,[p['returnRate'] for p in candidate['curve']],color='#75c7ff',lw=2.4,label='v3.1 候选')
        ax.plot(dates,[p['returnRate'] for p in base['curve']],color='#c3a8ed',lw=1.7,label='v2 基线')
        ax.plot(dates,[p['benchmarkReturnRate'] for p in candidate['curve']],color='#f3cf77',lw=1.5,label='买入持有')
        ax.axhline(0,color='#6d859b',lw=.7)
        ax.grid(alpha=.13);ax.set_title(f'{code}  {name}',loc='left',fontweight='bold',fontsize=11)
        ax.xaxis.set_major_locator(mdates.AutoDateLocator(minticks=3,maxticks=5));ax.xaxis.set_major_formatter(mdates.DateFormatter('%Y-%m'))
        ax.set_ylabel('累计收益 %')
        m=candidate['metrics'];b=base['metrics']
        ax.text(.02,.97,f"v3.1 收益 {m['totalReturn']:.2f}% / 回撤 {m['maxDrawdown']:.2f}%\nv2 收益 {b['totalReturn']:.2f}% / 回撤 {b['maxDrawdown']:.2f}%",transform=ax.transAxes,va='top',fontsize=9,
                bbox=dict(facecolor='#101923',alpha=.85,edgecolor='none'))
    axes.flat[-1].axis('off')
    axes.flat[-1].text(.04,.86,'审核候选：MA70 · 18% 高位回撤保护\n\n期初 100% · 买入现金上限 50% · 减仓份额上限 50%\n\n蓝：v3.1  /  紫：v2  /  黄：买入持有\n\n所有曲线均为扣已发生费用后的历史模拟。\n当前基金池存在选择偏差，参数已见过这些历史数据。\n天弘电网历史较短，实际起点顺延至 2026-06-01。\n\n没有修改线上“今日交易推荐”。',va='top',fontsize=12,linespacing=1.7)
    fig.suptitle('7 只基金逐只对照 · 同资金与费用假设 · 待审核',fontsize=19,fontweight='bold')
    fig.savefig(OUT/'review-curves.png',dpi=145)
    plt.close(fig)
    lines=['# NAV-TA v3.1 趋势仓位候选：审核稿','',
           '**状态：仅离线实验，未接入或切换“今日交易推荐”。需要用户审核后才实施线上版本。**','',
           '## 建议结论','',
           '建议审核 `T70-B2-F20-S18-I3`。它在本轮探索中更适合“提高上涨参与度，同时控制过大回撤”的目标，但不能称为未来最优策略。相较更激进的 22% 回撤保护方案，牺牲部分历史收益，换取较小的典型回撤。',
           '', '当前 7 只基金在约一年区间的单基金收益简单平均：**v2 31.00% → 候选 61.73%**。最大回撤的跨基金中位数：**17.97% → 22.46%**；最差单基金最大回撤：**31.07% → 27.87%**。平均数不是实际持仓组合收益，且天弘电网可用区间较短。收益提升伴随多数基金更大的回撤，不能宣传为全面优于 v2。','',
           '## 可执行规则与具体参数','',
           '|项目|候选设置|','|---|---|',
           '|版本|NAV-TA v3.1-trend-research，配置 T70-B2-F20-S18-I3|',
           '|趋势均线|MA70，基于公布日收益率构造的复权日净值|',
           '|趋势确认|最近连续 3 个净值点均高于各自 MA70 的 102%，且最新 MA70 高于 5 点前|',
           '|确认转弱|连续 3 点低于各自 MA70 的 98%，且 MA70 低于 5 点前|',
           '|进攻 / 防守目标|100% / 20%，是模拟总资产中的目标基金占比|',
           '|高位回撤保护|最新复权净值较最近 60 个已披露净值点的最高值下跌 ≥18%，且低于 MA20，则切换到 20% 目标|',
           '|恢复买入|普通趋势转弱后须再次满足趋势确认；触发高位保护后还须连续 3 点站上各自 MA20，且 MA20 较 5 点前上升|',
           '|趋势未破坏|保持上一次目标；不因 RSI 偏高或一次 MACD 死叉单独减仓|',
           '|调仓容差|已披露净值估算的仓位与目标相差超过 5 个百分点才调仓|',
           '|成交间隔|规则成交间隔至少 3 个净值观察日；未成交不消耗间隔|',
           '|买入上限|每次最多使用可用现金 50%，同时不超过恢复目标所需金额|',
           '|减仓上限|每次最多卖出已确认份额 50%，同时不超过降至目标所需份额|',
           '|资金与初始配置|每基金 10,000 元，期初模拟建仓 100%；不等于建议实际账户满仓|',
           '|数据预热|至少 120 个日净值点；保留数据质量门槛|',
           '|披露 / 确认 / 到账|境内披露 1、QDII 披露 2、确认 1、到账 3 个净值观察日；实际可卖不早于净值可知，实际到账不早于披露|',
           '|费用假设|申购 0.15%；赎回不足 7 天 1.5%、7–29 天 0.5%、≥30 天 0%；需按实际产品核对|','',
           '18% 是触发防守的观察阈值，不是最大回撤保证。披露滞后、分批减仓、估值变动和成交间隔都可能让实际模拟回撤超过 18%。','',
           '## 逐基金结果','',
           '名义区间 2025-10-01 至 2026-09-30，起点按有净值且有足够预热数据顺延。对照为用户指定的 v2、100% 期初、50% 买入、50% 减仓；费用与披露假设一致。',
           '', '|基金|实际起点|v2 收益|候选收益|v2 最大回撤|候选最大回撤|买入持有收益|候选规则成交|','|---|---|---:|---:|---:|---:|---:|---:|']
    for code,name in NAMES.items():
        x=results[code]['FULL'];a=x['v2']['metrics'];b=x['candidate']['metrics']
        lines.append(f"|{code} {name}|{x['candidate']['start']}|{a['totalReturn']:.2f}%|{b['totalReturn']:.2f}%|{a['maxDrawdown']:.2f}%|{b['maxDrawdown']:.2f}%|{b['benchmarkReturn']:.2f}%|{b['signalTradeCount']}|")
    lines += ['', '**必须保留的负面结果：**013403 仍亏损；025833 收益与回撤均退步，其 206 个总净值点仅支持约 4 个月回测，不能据此确认适用。021180 这一段没有规则成交，候选表现实际上是买入持有，不能归功于精准择时。其余多只基金虽然收益增加，回撤也变大。','',
              '![逐基金收益曲线](review-curves.png)','', '## 不同起点及成本敏感性','',
              '以下仍为跨基金简单平均收益 / 最大回撤中位数，不是组合收益。各段均重新按相同初始资金配置开始；新基金起点继续按可用数据顺延。','',
              '|场景|v2 平均收益|候选平均收益|v2 回撤中位数|候选回撤中位数|','|---|---:|---:|---:|---:|']
    labels={'FULL':'2025-10 起点','JAN_START':'2026-01 起点','APR_START':'2026-04 起点','JUL_START':'2026-07 起点','RISE':'2026-04 至 06 月','COST_STRESS':'费用加倍、到账延后至 5 日','CASH_START':'期初全现金（其余参数一致）'}
    for key,label in labels.items():
        a=[r[key]['v2']['metrics'] for r in results.values()];b=[r[key]['candidate']['metrics'] for r in results.values()]
        lines.append(f"|{label}|{mean(m['totalReturn'] for m in a):.2f}%|{mean(m['totalReturn'] for m in b):.2f}%|{median(m['maxDrawdown'] for m in a):.2f}%|{median(m['maxDrawdown'] for m in b):.2f}%|")
    lines += ['', '成本压力场景：申购 0.30%，赎回 3.0% / 1.0% / 0.1%，到账延后到 5 个观察日。以上结果没有人为降低费用来提高收益。','',
              '## 实验过程与选择依据','',
              '1. 先在 2025-04 至 2026-03 的较早区间筛选 20 组配置；训练段得分最高的 MA80、4% 缓冲、无高位保护方案，在后续检查中出现约 47.54% 的最大回撤，未采用。',
              '2. 对趋势目标与回撤保护做 42 组探索，再对“保护后须恢复确认”机制做同样 42 组探索。所有结果保留，没有只保留收益最好的一只基金。',
              '3. 历史综合得分最高的配置为 MA70 / 22% 高位保护 / 3 日间隔：平均收益约 67.20%，回撤中位数约 25.95%。本审核候选改用 18%：平均收益约 61.73%，回撤中位数约 22.46%。这是可解释的收益与风险取舍，不是宣称单一数学最优。',
              '4. 复核不同起点、上涨段、下跌段、费用压力及期初全现金。期初仓位相同的比较已排除“只因初始满仓才提高收益”的单一解释，但改善仍主要来自更久参与趋势、承担更高市场暴露。',
              '', '**验证边界：**后续历史在前期诊断中已经看过，而且第二轮机制是看过第一轮结果后改进的，因此最终方案属于探索性历史拟合，不是真正未见数据的盲测。当前持仓池也有选择和存续偏差；不能据此保证未来最大收益。需要冻结这套参数后用后续新净值做前瞻记录。',
              '', '数据使用系统配置 A 股日历，不是完整逐基金历史开放日；没有实际披露时刻、限购赎回暂停、真实分红到账、产品逐笔费率。QDII 延迟仅为近似。历史源数据也可能修订。',
              '', '[Investor.gov 对历史回测与业绩宣传的说明](https://www.investor.gov/introduction-investing/general-resources/news-alerts/alerts-bulletins/investor-bulletins-47)。',
              '', '## 复现和审核材料','',
              '- `review-candidate.json`：候选参数、资金假设、完整统计和审核状态。',
              '- `*-review.json` / `*-v2-baseline.json`：逐日曲线、信号、仓位目标、委托预算、成交依据和费用。',
              '- `round2-grid.json` / `exploratory-grid.json`：两轮完整配置比较。',
              '- `nav-snapshot.csv`：本次只读导出的公开基金净值快照，不包含账户或凭据。',
              '- 研究代码位于 `quant-engine/research/`，没有被生产接口导入。',
              '', '运行目录为 `quant-engine`：','', '```powershell',
              'python -m research.compare_grid --legacy-reentry', 'python -m research.compare_grid', 'python -m research.audit_candidate',
              'python -m pytest research/test_trend.py app/tests/test_nav_technical.py -q', '```','',
              f"输入快照 SHA256：`{summary['sourceHash']}`。", '',
              '测试覆盖：研究回放在无候选策略时与生产回放完全一致、资金与费用守恒、减仓后重新入场、持续恢复仓位、成交间隔、当日未披露净值不能影响委托、QDII 未披露成交份额不能泄漏到仓位计算、数据缺失阻断。',
              '', '**审核范围：**是否接受上述具体参数及回撤取舍。审核前不切换“今日交易推荐”，不操作真实持仓或交易。']
    (OUT/'REVIEW.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')


if __name__ == '__main__': main()
