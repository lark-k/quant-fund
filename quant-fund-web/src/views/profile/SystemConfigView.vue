<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { quantApi } from '@/api/quant'
import type {
  ApiCallLog,
  AiRuntimeConfig,
  DataSourceConfig,
  DataSourceConfigRequest,
  DataSourceHealth,
  OperationLog,
  PageResponse,
  RiskLevel,
  RiskProfile
} from '@/types/domain'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingState from '@/components/common/LoadingState.vue'

const configs = ref<DataSourceConfig[]>([])
const dataSourceHealth = ref<DataSourceHealth[]>([])
const aiRuntimeConfig = ref<AiRuntimeConfig | null>(null)
const riskProfile = ref<RiskProfile | null>(null)
const selectedDataSourceId = ref<number | null>(null)
const operationLogPage = ref<PageResponse<OperationLog>>()
const apiCallLogPage = ref<PageResponse<ApiCallLog>>()
const loading = ref(true)
const loadingLogs = ref(false)
const savingRisk = ref(false)
const savingDataSource = ref(false)

const riskForm = reactive({
  aiModel: 'deepseek-v4-flash',
  riskLevel: 'MEDIUM' as RiskLevel,
  maxEquityPositionRate: 70,
  maxSingleFundPositionRate: 25,
  drawdownAlertRate: 8,
  dailyRiseAlertRate: 2,
  dailyFallAlertRate: 2.5
})

const dataSourceForm = reactive<DataSourceConfigRequest>({
  sourceName: 'EAST_MONEY',
  baseUrl: 'https://api.fund.eastmoney.com',
  timeoutMs: 5000,
  refreshIntervalSeconds: 120,
  rateLimitPerMinute: 60,
  enabled: true,
  priority: 10,
  configJson: '{}'
})

const logFilter = reactive({
  module: '',
  provider: '',
  success: '' as '' | 'true' | 'false'
})

const selectedDataSource = computed(() => configs.value.find((item) => item.id === selectedDataSourceId.value))
const logSuccessValue = computed(() => logFilter.success === '' ? undefined : logFilter.success === 'true')
const aiStatusTagClass = computed(() => aiRuntimeConfig.value?.ready ? 'tag-success' : 'tag-risk')
const aiStatusText = computed(() => aiRuntimeConfig.value?.ready ? '真实调用就绪' : '需要检查配置')

onMounted(loadConfig)

async function loadConfig() {
  loading.value = true
  try {
    const [sourceList, healthList, profile, aiConfig] = await Promise.all([
      quantApi.dataSources(),
      quantApi.dataSourceHealth(),
      quantApi.riskProfile(),
      quantApi.aiRuntimeConfig()
    ])
    configs.value = sourceList
    dataSourceHealth.value = healthList
    aiRuntimeConfig.value = aiConfig
    applyRiskProfile(profile)
    if (sourceList[0]) selectDataSource(sourceList[0])
    await loadLogs()
  } finally {
    loading.value = false
  }
}

async function loadLogs() {
  loadingLogs.value = true
  try {
    const [operationLogs, apiLogs] = await Promise.all([
      quantApi.operationLogs({
        pageNo: 1,
        pageSize: 8,
        module: logFilter.module || undefined,
        success: logSuccessValue.value
      }),
      quantApi.apiCallLogs({
        pageNo: 1,
        pageSize: 8,
        provider: logFilter.provider || undefined,
        success: logSuccessValue.value
      })
    ])
    operationLogPage.value = operationLogs
    apiCallLogPage.value = apiLogs
  } finally {
    loadingLogs.value = false
  }
}

function applyRiskProfile(profile: RiskProfile) {
  riskProfile.value = profile
  riskForm.riskLevel = profile.riskLevel
  riskForm.maxEquityPositionRate = profile.maxEquityPositionRate
  riskForm.maxSingleFundPositionRate = profile.maxSingleFundPositionRate
  riskForm.drawdownAlertRate = profile.drawdownAlertRate
  riskForm.dailyRiseAlertRate = profile.dailyRiseAlertRate
  riskForm.dailyFallAlertRate = profile.dailyFallAlertRate
  try {
    const config = JSON.parse(profile.configJson || '{}') as { aiModel?: string }
    riskForm.aiModel = config.aiModel || riskForm.aiModel
  } catch {
    riskForm.aiModel = 'deepseek-v4-flash'
  }
}

function selectDataSource(config: DataSourceConfig) {
  selectedDataSourceId.value = config.id
  dataSourceForm.sourceName = config.sourceName
  dataSourceForm.baseUrl = config.baseUrl
  dataSourceForm.timeoutMs = config.timeoutMs
  dataSourceForm.refreshIntervalSeconds = config.refreshIntervalSeconds
  dataSourceForm.rateLimitPerMinute = config.rateLimitPerMinute
  dataSourceForm.enabled = config.enabled
  dataSourceForm.priority = config.priority
  dataSourceForm.configJson = config.configJson || '{}'
}

function newDataSource() {
  selectedDataSourceId.value = null
  dataSourceForm.sourceName = 'CUSTOM_SOURCE'
  dataSourceForm.baseUrl = 'https://'
  dataSourceForm.timeoutMs = 5000
  dataSourceForm.refreshIntervalSeconds = 120
  dataSourceForm.rateLimitPerMinute = 60
  dataSourceForm.enabled = false
  dataSourceForm.priority = 50
  dataSourceForm.configJson = '{}'
}

async function saveRiskProfile() {
  savingRisk.value = true
  try {
    const saved = await quantApi.saveRiskProfile({
      riskLevel: riskForm.riskLevel,
      maxEquityPositionRate: riskForm.maxEquityPositionRate,
      maxSingleFundPositionRate: riskForm.maxSingleFundPositionRate,
      drawdownAlertRate: riskForm.drawdownAlertRate,
      dailyRiseAlertRate: riskForm.dailyRiseAlertRate,
      dailyFallAlertRate: riskForm.dailyFallAlertRate,
      configJson: JSON.stringify({ aiModel: riskForm.aiModel })
    })
    applyRiskProfile(saved)
    await loadLogs()
    ElMessage.success('风险偏好已保存')
  } finally {
    savingRisk.value = false
  }
}

async function saveDataSource() {
  savingDataSource.value = true
  try {
    const saved = selectedDataSourceId.value
      ? await quantApi.updateDataSource(selectedDataSourceId.value, dataSourceForm)
      : await quantApi.saveDataSource(dataSourceForm)
    const index = configs.value.findIndex((item) => item.id === saved.id)
    if (index >= 0) configs.value.splice(index, 1, saved)
    else configs.value.unshift(saved)
    selectDataSource(saved)
    await loadLogs()
    ElMessage.success('数据源配置已保存')
  } finally {
    savingDataSource.value = false
  }
}
</script>

<template>
  <LoadingState v-if="loading" text="正在加载系统配置" />
  <div v-else class="screen-grid">
    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">系统配置</h2>
        <button class="primary-button" :disabled="savingRisk" @click="saveRiskProfile">
          {{ savingRisk ? '保存中' : '保存风险偏好' }}
        </button>
      </div>
      <div class="panel-body config-grid">
        <label>AI 模型<input v-model="riskForm.aiModel" class="form-control" /></label>
        <label>
          风险偏好
          <select v-model="riskForm.riskLevel" class="form-control">
            <option value="LOW">LOW</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="HIGH">HIGH</option>
          </select>
        </label>
        <label>权益仓位上限<input v-model.number="riskForm.maxEquityPositionRate" class="form-control" type="number" min="0" max="100" step="1" /></label>
        <label>单基金仓位上限<input v-model.number="riskForm.maxSingleFundPositionRate" class="form-control" type="number" min="0" max="100" step="1" /></label>
        <label>回撤预警阈值<input v-model.number="riskForm.drawdownAlertRate" class="form-control" type="number" min="0" max="100" step="0.5" /></label>
        <label>日涨幅预警<input v-model.number="riskForm.dailyRiseAlertRate" class="form-control" type="number" min="0" max="100" step="0.1" /></label>
        <label>日跌幅预警<input v-model.number="riskForm.dailyFallAlertRate" class="form-control" type="number" min="0" max="100" step="0.1" /></label>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">AI 配置诊断</h2>
        <span class="action-tag" :class="aiStatusTagClass">{{ aiStatusText }}</span>
      </div>
      <div v-if="aiRuntimeConfig" class="panel-body ai-diagnosis-grid">
        <div class="insight-item">
          <span>Provider</span>
          <strong>{{ aiRuntimeConfig.provider }}</strong>
        </div>
        <div class="insight-item">
          <span>Model</span>
          <strong>{{ aiRuntimeConfig.model }}</strong>
        </div>
        <div class="insight-item">
          <span>Base URL</span>
          <strong>{{ aiRuntimeConfig.baseUrl }}</strong>
        </div>
        <div class="insight-item">
          <span>DEEPSEEK_ENABLED</span>
          <strong>{{ aiRuntimeConfig.enabled ? 'true' : 'false' }}</strong>
        </div>
        <div class="insight-item">
          <span>DEEPSEEK_MOCK_ENABLED</span>
          <strong>{{ aiRuntimeConfig.mockEnabled ? 'true' : 'false' }}</strong>
        </div>
        <div class="insight-item">
          <span>DEEPSEEK_API_KEY</span>
          <strong>{{ aiRuntimeConfig.keyPresent ? '已配置' : '未配置' }}</strong>
        </div>
        <p class="diagnosis-text">{{ aiRuntimeConfig.diagnosis }}</p>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">数据源配置</h2>
        <div class="toolbar-row">
          <button class="ghost-button" @click="newDataSource">新增</button>
          <button class="primary-button" :disabled="savingDataSource" @click="saveDataSource">
            {{ savingDataSource ? '保存中' : '保存数据源' }}
          </button>
        </div>
      </div>
      <div class="panel-body">
        <div class="config-grid">
          <label>名称<input v-model="dataSourceForm.sourceName" class="form-control" /></label>
          <label>地址<input v-model="dataSourceForm.baseUrl" class="form-control" /></label>
          <label>超时(ms)<input v-model.number="dataSourceForm.timeoutMs" class="form-control" type="number" min="1000" max="60000" /></label>
          <label>刷新频率(s)<input v-model.number="dataSourceForm.refreshIntervalSeconds" class="form-control" type="number" min="10" max="86400" /></label>
          <label>限流(/min)<input v-model.number="dataSourceForm.rateLimitPerMinute" class="form-control" type="number" min="1" max="6000" /></label>
          <label>优先级<input v-model.number="dataSourceForm.priority" class="form-control" type="number" min="1" max="9999" /></label>
          <label>配置 JSON<input v-model="dataSourceForm.configJson" class="form-control" /></label>
          <label class="switch-row">
            <input v-model="dataSourceForm.enabled" type="checkbox" />
            <span>{{ dataSourceForm.enabled ? '启用' : '停用' }}</span>
          </label>
        </div>

        <table v-if="configs.length" class="terminal-table">
          <thead><tr><th>名称</th><th>地址</th><th>超时</th><th>频率</th><th>限流</th><th>状态</th></tr></thead>
          <tbody>
            <tr
              v-for="item in configs"
              :key="item.id"
              :class="{ selected: selectedDataSource?.id === item.id }"
              @click="selectDataSource(item)"
            >
              <td>{{ item.sourceName }}</td>
              <td>{{ item.baseUrl }}</td>
              <td>{{ item.timeoutMs }}ms</td>
              <td>{{ item.refreshIntervalSeconds }}s</td>
              <td>{{ item.rateLimitPerMinute }}/min</td>
              <td>
                <span class="action-tag" :class="item.enabled ? 'tag-success' : 'tag-neutral'">
                  {{ item.enabled ? '启用' : '停用' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-else title="暂无数据源配置" description="可以新增一个用户级数据源，外部 API 不可用时仍可使用 mock 降级。" />
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">数据源健康</h2>
        <span class="item-meta">按最近 300 条 API 日志聚合</span>
      </div>
      <div class="panel-body">
        <table v-if="dataSourceHealth.length" class="terminal-table">
          <thead><tr><th>来源</th><th>接口</th><th>状态</th><th>最近调用</th><th>最近成功</th><th>失败原因</th><th>耗时</th></tr></thead>
          <tbody>
            <tr v-for="item in dataSourceHealth" :key="`${item.provider}-${item.apiName}`">
              <td>{{ item.provider }}</td>
              <td>{{ item.apiName }}</td>
              <td>
                <span class="action-tag" :class="item.healthy ? 'tag-success' : item.delayed ? 'tag-warning' : 'tag-risk'">
                  {{ item.statusText }}
                </span>
              </td>
              <td>{{ item.lastCallTime || '--' }}</td>
              <td>{{ item.lastSuccessTime || '--' }}</td>
              <td>{{ item.lastFailureReason || '--' }}</td>
              <td>{{ item.lastCostTimeMs ?? '--' }}ms</td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-else title="暂无数据源健康记录" description="搜索基金、刷新估值或同步净值后会产生健康记录。" />
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <h2 class="panel-title">运维日志</h2>
        <button class="ghost-button" :disabled="loadingLogs" @click="loadLogs">
          {{ loadingLogs ? '刷新中' : '刷新日志' }}
        </button>
      </div>
      <div class="panel-body">
        <div class="toolbar-row log-filter-row">
          <select v-model="logFilter.success" class="form-control">
            <option value="">全部结果</option>
            <option value="true">成功</option>
            <option value="false">失败</option>
          </select>
          <input v-model="logFilter.module" class="form-control" placeholder="操作模块，如 auth / system" />
          <input v-model="logFilter.provider" class="form-control" placeholder="API 来源，如 EAST_MONEY" />
        </div>

        <div class="insight-grid log-grid">
          <section>
            <div class="panel-header compact-header">
              <h3 class="panel-title">操作日志</h3>
              <span class="item-meta">共 {{ operationLogPage?.total || 0 }} 条</span>
            </div>
            <table v-if="operationLogPage?.records.length" class="terminal-table">
              <thead><tr><th>时间</th><th>模块</th><th>动作</th><th>路径</th><th>耗时</th><th>状态</th></tr></thead>
              <tbody>
                <tr v-for="item in operationLogPage.records" :key="item.id">
                  <td>{{ item.createTime }}</td>
                  <td>{{ item.module }}</td>
                  <td>{{ item.action }}</td>
                  <td>{{ item.requestUri }}</td>
                  <td>{{ item.costTimeMs }}ms</td>
                  <td>
                    <span class="action-tag" :class="item.success ? 'tag-success' : 'tag-risk'">
                      {{ item.success ? '成功' : '失败' }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
            <EmptyState v-else title="暂无操作日志" description="完成登录、保存配置或模拟交易后会产生记录。" />
          </section>

          <section>
            <div class="panel-header compact-header">
              <h3 class="panel-title">API 调用日志</h3>
              <span class="item-meta">共 {{ apiCallLogPage?.total || 0 }} 条</span>
            </div>
            <table v-if="apiCallLogPage?.records.length" class="terminal-table">
              <thead><tr><th>时间</th><th>来源</th><th>接口</th><th>耗时</th><th>降级</th><th>状态</th></tr></thead>
              <tbody>
                <tr v-for="item in apiCallLogPage.records" :key="item.id">
                  <td>{{ item.callTime }}</td>
                  <td>{{ item.provider }}</td>
                  <td>{{ item.apiName }}</td>
                  <td>{{ item.costTimeMs }}ms</td>
                  <td>{{ item.fallbackUsed ? '是' : '否' }}</td>
                  <td>
                    <span class="action-tag" :class="item.success ? 'tag-success' : 'tag-risk'">
                      {{ item.success ? '成功' : '失败' }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
            <EmptyState v-else title="暂无 API 日志" description="刷新基金估值、同步净值或 AI 分析后会产生记录。" />
          </section>
        </div>
      </div>
    </section>
  </div>
</template>
