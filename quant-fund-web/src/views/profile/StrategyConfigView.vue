<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Close, Switch } from '@element-plus/icons-vue'
import { quantApi } from '@/api/quant'
import type { StrategyConfig, StrategyConfigRequest } from '@/types/domain'
import DisclaimerBar from '@/components/common/DisclaimerBar.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

type StrategyForm = {
  configName: string
  strategyType: string
  fundType: string
  enabled: boolean
  profitActivationPct: number
  lightDrawdownPct: number
  mediumDrawdownPct: number
  heavyDrawdownPct: number
  equityLimitPct: number
  singleFundLimitPct: number
  largeRisePct: number
}

const configs = ref<StrategyConfig[]>([])
const activeType = ref('DRAWDOWN_STOP_PROFIT')
const loading = ref(true)
const saving = ref(false)

const form = reactive<StrategyForm>({
  configName: '主动基金回撤止盈',
  strategyType: 'DRAWDOWN_STOP_PROFIT',
  fundType: 'ACTIVE_EQUITY',
  enabled: true,
  profitActivationPct: 15,
  lightDrawdownPct: 3,
  mediumDrawdownPct: 5,
  heavyDrawdownPct: 8,
  equityLimitPct: 70,
  singleFundLimitPct: 25,
  largeRisePct: 2
})

const strategyTabs = [
  { label: '回撤止盈', value: 'DRAWDOWN_STOP_PROFIT' },
  { label: '梯度止盈', value: 'DYNAMIC_LADDER_STOP_PROFIT' },
  { label: '仓位监控', value: 'POSITION_MONITOR' }
]

const activeConfig = computed(() => configs.value.find((item) => item.strategyType === activeType.value))
const enabledCount = computed(() => configs.value.filter((item) => item.enabled).length)

onMounted(loadConfigs)

async function loadConfigs() {
  try {
    configs.value = await quantApi.strategyConfigs()
    if (configs.value[0]) {
      selectConfig(configs.value[0].strategyType)
    }
  } finally {
    loading.value = false
  }
}

function readNumber(params: Record<string, unknown>, key: string, fallback: number) {
  const value = Number(params[key])
  return Number.isFinite(value) ? value : fallback
}

function selectConfig(strategyType: string) {
  activeType.value = strategyType
  const config = configs.value.find((item) => item.strategyType === strategyType)
  if (!config) return
  let params: Record<string, unknown> = {}
  try {
    params = JSON.parse(config.paramsJson || '{}')
  } catch {
    params = {}
  }
  form.configName = config.configName
  form.strategyType = config.strategyType
  form.fundType = config.fundType || ''
  form.enabled = config.enabled
  form.profitActivationPct = readNumber(params, 'profitActivationPct', form.profitActivationPct)
  form.lightDrawdownPct = readNumber(params, 'lightDrawdownPct', form.lightDrawdownPct)
  form.mediumDrawdownPct = readNumber(params, 'mediumDrawdownPct', form.mediumDrawdownPct)
  form.heavyDrawdownPct = readNumber(params, 'heavyDrawdownPct', form.heavyDrawdownPct)
  form.equityLimitPct = readNumber(params, 'equityLimitPct', form.equityLimitPct)
  form.singleFundLimitPct = readNumber(params, 'singleFundLimitPct', form.singleFundLimitPct)
  form.largeRisePct = readNumber(params, 'largeRisePct', form.largeRisePct)
}

function buildParamsJson() {
  if (form.strategyType === 'POSITION_MONITOR') {
    return JSON.stringify({
      equityLimitPct: form.equityLimitPct,
      singleFundLimitPct: form.singleFundLimitPct,
      largeRisePct: form.largeRisePct
    })
  }
  if (form.strategyType === 'DYNAMIC_LADDER_STOP_PROFIT') {
    return JSON.stringify({
      ladder: [
        { profitPct: 10, sellRatio: 10 },
        { profitPct: 20, sellRatio: 20 },
        { profitPct: 30, sellRatio: 30 },
        { profitPct: 50, sellRatio: 40 }
      ],
      equityLimitPct: form.equityLimitPct
    })
  }
  return JSON.stringify({
    profitActivationPct: form.profitActivationPct,
    lightDrawdownPct: form.lightDrawdownPct,
    mediumDrawdownPct: form.mediumDrawdownPct,
    heavyDrawdownPct: form.heavyDrawdownPct
  })
}

async function saveStrategy() {
  saving.value = true
  try {
    const request: StrategyConfigRequest = {
      configName: form.configName,
      strategyType: form.strategyType,
      fundType: form.fundType || null,
      paramsJson: buildParamsJson(),
      enabled: form.enabled
    }
    const saved = await quantApi.saveStrategyConfig(request)
    const index = configs.value.findIndex((item) => item.id === saved.id || item.strategyType === saved.strategyType)
    if (index >= 0) configs.value.splice(index, 1, saved)
    else configs.value.unshift(saved)
    ElMessage.success('策略参数已保存')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <LoadingState v-if="loading" text="正在加载策略参数" />
  <EmptyState v-else-if="!configs.length" title="暂无策略配置" description="后端会在初始化后生成默认策略参数。" />
  <div v-else class="screen-grid">
    <div class="metric-row">
      <section class="metric-tile">
        <div class="metric-label">已启用策略</div>
        <div class="metric-value metric-info">{{ enabledCount }}</div>
        <div class="metric-sub"><span>共 {{ configs.length }} 条策略规则</span></div>
      </section>
      <section class="metric-tile">
        <div class="metric-label">权益仓位上限</div>
        <div class="metric-value metric-warning">{{ form.equityLimitPct }}%</div>
        <div class="metric-sub"><span>超过后不再建议加仓</span></div>
      </section>
      <section class="metric-tile">
        <div class="metric-label">单基金集中度</div>
        <div class="metric-value metric-warning">{{ form.singleFundLimitPct }}%</div>
        <div class="metric-sub"><span>触发集中度风险提醒</span></div>
      </section>
      <section class="metric-tile">
        <div class="metric-label">日涨幅追涨阈值</div>
        <div class="metric-value metric-fall">{{ form.largeRisePct }}%</div>
        <div class="metric-sub"><span>涨幅过大进入观察</span></div>
      </section>
      <section class="metric-tile">
        <div class="metric-label">当前策略</div>
        <div class="metric-value metric-info">{{ activeConfig?.enabled ? 'ON' : 'OFF' }}</div>
        <div class="metric-sub"><span>{{ activeConfig?.configName }}</span></div>
      </section>
      <section class="metric-tile">
        <div class="metric-label">最近更新</div>
        <div class="metric-value metric-neutral">{{ activeConfig?.updateTime.slice(5, 10) }}</div>
        <div class="metric-sub"><span>{{ activeConfig?.updateTime.slice(11, 19) }}</span></div>
      </section>
    </div>

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">策略参数配置</h2>
        <button class="primary-button" :disabled="saving" @click="saveStrategy">
          <el-icon><Check /></el-icon>
          保存配置
        </button>
      </div>
      <div class="panel-body">
        <div class="toolbar-row">
          <div class="segmented" aria-label="策略类型">
            <button
              v-for="item in strategyTabs"
              :key="item.value"
              :class="{ active: activeType === item.value }"
              @click="selectConfig(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
          <label class="switch-row">
            <input v-model="form.enabled" type="checkbox" />
            <el-icon><Switch /></el-icon>
            <span>{{ form.enabled ? '启用' : '停用' }}</span>
          </label>
        </div>

        <div class="config-grid strategy-form">
          <label>
            策略名称
            <input v-model="form.configName" class="form-control" />
          </label>
          <label>
            基金类型
            <select v-model="form.fundType" class="form-control">
              <option value="">全部类型</option>
              <option value="ACTIVE_EQUITY">主动权益</option>
              <option value="INDEX_FUND">指数基金</option>
              <option value="ETF">ETF</option>
              <option value="BOND_FUND">债券基金</option>
            </select>
          </label>
          <label>
            止盈启动收益率
            <input v-model.number="form.profitActivationPct" class="form-control" type="number" min="0" step="0.5" />
          </label>
          <label>
            轻度回撤减仓
            <input v-model.number="form.lightDrawdownPct" class="form-control" type="number" min="0" step="0.5" />
          </label>
          <label>
            中度回撤减仓
            <input v-model.number="form.mediumDrawdownPct" class="form-control" type="number" min="0" step="0.5" />
          </label>
          <label>
            重度回撤减仓
            <input v-model.number="form.heavyDrawdownPct" class="form-control" type="number" min="0" step="0.5" />
          </label>
          <label>
            权益基金仓位上限
            <input v-model.number="form.equityLimitPct" class="form-control" type="number" min="0" max="100" step="1" />
          </label>
          <label>
            单基金最大仓位
            <input v-model.number="form.singleFundLimitPct" class="form-control" type="number" min="0" max="100" step="1" />
          </label>
          <label>
            当日涨幅追涨阈值
            <input v-model.number="form.largeRisePct" class="form-control" type="number" min="0" step="0.1" />
          </label>
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">策略清单</h2>
        <span class="item-meta">参数保存到后端后由策略引擎使用</span>
      </div>
      <div class="panel-body">
        <table class="terminal-table">
          <thead><tr><th>策略</th><th>类型</th><th>适用基金</th><th>状态</th><th>更新时间</th></tr></thead>
          <tbody>
            <tr v-for="item in configs" :key="item.id" @click="selectConfig(item.strategyType)">
              <td>{{ item.configName }}</td>
              <td>{{ item.strategyType }}</td>
              <td>{{ item.fundType || '全部' }}</td>
              <td>
                <span class="action-tag" :class="item.enabled ? 'tag-success' : 'tag-neutral'">
                  <el-icon><component :is="item.enabled ? Check : Close" /></el-icon>
                  {{ item.enabled ? '启用' : '停用' }}
                </span>
              </td>
              <td>{{ item.updateTime }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <DisclaimerBar />
  </div>
</template>
