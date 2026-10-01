
export type TechnicalAction = 'BUY' | 'REDUCE' | 'HOLD' | 'WATCH' | 'UNAVAILABLE'
export interface TechnicalEvidence { label: string; value: string; explanation: string; tone: 'bull' | 'bear' | 'neutral' }
export const currentTechnicalVersion = 'NAV-TA v3.1-trend' as const
export const currentTechnicalRuleId = 'T70-B2-F20-S18-I3'
export interface TechnicalAdvice {
  ruleVersion?: string
  ruleId?: string
  rules?: string[]
  targetWeight?: number
  executionReady?: boolean
  executionStatus?: string
  action: TechnicalAction
  title: string
  explanation: string
  asOf: string
  evaluatedAt: string
  sampleCount: number
  source: string
  evidence: TechnicalEvidence[]
  blockers: string[]
  notes: string[]
}
export const technicalRules = [
  '固定使用最近 120 个有效日净值，以公布的日收益率反推前复权；不受主图周期、范围或均线开关影响。',
  '买入：净值 > MA20 > MA60，MA5 > MA10 > MA20，MA20 高于 5 个净值点前；DIF > DEA 且 DIF > 0；最近完整周上涨、低点及收盘抬高；RSI14 在 45–70，净值高于 MA20 不超过 5%。须全部满足。',
  '减仓：净值 < MA20 < MA60，MA5 < MA10 < MA20，MA20 低于 5 个净值点前；DIF < DEA 且 DIF < 0；最近完整周下跌、高点及收盘降低。须全部满足。',
  '趋势向上但偏热或确认不足时持有、不追涨；其余信号不一致时观望。周 K 仅比较当前自然周之前的两个相邻周，每周至少 3 个净值点；它是日净值聚合，不是盘中成交 K 线。',
  '数据门槛：120 个连续可复权净值点、最近数据距今不超过 4 个自然日且最多落后 1 个工作日、样本内间隔不超过 14 天；交易日核对采用系统 A 股日历。假期后或 QDII 披露较慢时可能保守地暂停建议。',
  '上述联合条件、120 点窗口与 5% 乖离门槛是本功能公开的启发式规则，可通过历史模拟检验，但单次回测不代表已通过样本外验证或保证未来收益。'
]

export function shanghaiDateTime(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).formatToParts(now)
  const p = Object.fromEntries(parts.map(part => [part.type, part.value]))
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}:${p.second}`
}
