<template>
  <div class="dashboard">
    <el-row :gutter="20" class="metric-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value">{{ metrics.totalInstances || 0 }}</div>
          <div class="metric-label">流程实例总数</div>
          <el-icon class="metric-icon" :size="40" color="#409EFF"><Files /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value running">{{ metrics.runningInstances || 0 }}</div>
          <div class="metric-label">运行中实例</div>
          <el-icon class="metric-icon" :size="40" color="#67C23A"><VideoPlay /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value completed">{{ metrics.completedInstances || 0 }}</div>
          <div class="metric-label">已完成实例</div>
          <el-icon class="metric-icon" :size="40" color="#E6A23C"><CircleCheck /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value rate">{{ metrics.completionRate || '0' }}%</div>
          <div class="metric-label">完成率</div>
          <el-icon class="metric-icon" :size="40" color="#F56C6C"><TrendCharts /></el-icon>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="metric-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value">{{ metrics.todayStarted || 0 }}</div>
          <div class="metric-label">今日启动</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value">{{ metrics.todayCompleted || 0 }}</div>
          <div class="metric-label">今日完成</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value">{{ metrics.suspendedInstances || 0 }}</div>
          <div class="metric-label">挂起实例</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-value">{{ formatDuration(metrics.avgDurationMs) }}</div>
          <div class="metric-label">平均耗时</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card>
          <template #header>额度分布</template>
          <v-chart :option="limitChartOption" style="height: 300px" autoresize />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>反欺诈规则命中排行</template>
          <v-chart :option="ruleHitChartOption" style="height: 300px" autoresize />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { getWorkflowMetrics, getLimitDistribution, getRuleHitStats } from '../api/monitor'

use([CanvasRenderer, BarChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const metrics = ref({})
const limitData = ref([])
const ruleData = ref([])

const limitChartOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { orient: 'vertical', left: 'left' },
  series: [{
    type: 'pie',
    radius: ['40%', '70%'],
    avoidLabelOverlap: false,
    itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
    label: { show: true, formatter: '{b}\n{d}%' },
    data: limitData.value.map(item => ({
      name: item.range,
      value: item.count
    }))
  }]
}))

const ruleHitChartOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: { type: 'value' },
  yAxis: {
    type: 'category',
    data: ruleData.value.slice(0, 8).map(r => r.ruleName).reverse(),
    axisLabel: { width: 80, overflow: 'truncate' }
  },
  series: [{
    type: 'bar',
    data: ruleData.value.slice(0, 8).map(r => r.hitCount).reverse(),
    itemStyle: {
      color: (params) => {
        const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#00d2ff', '#7b68ee', '#ff6384']
        return colors[params.dataIndex % colors.length]
      }
    }
  }]
}))

function formatDuration(ms) {
  if (!ms) return '-'
  const seconds = Math.floor(ms / 1000)
  if (seconds < 60) return `${seconds}秒`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}分${seconds % 60}秒`
  const hours = Math.floor(minutes / 60)
  return `${hours}时${minutes % 60}分`
}

async function loadMetrics() {
  try {
    const res = await getWorkflowMetrics()
    metrics.value = res.data || {}
  } catch (e) { /* ignore */ }
}

async function loadLimitData() {
  try {
    const res = await getLimitDistribution()
    limitData.value = res.data || []
  } catch (e) { /* ignore */ }
}

async function loadRuleData() {
  try {
    const res = await getRuleHitStats({})
    ruleData.value = res.data || []
  } catch (e) { /* ignore */ }
}

onMounted(() => {
  loadMetrics()
  loadLimitData()
  loadRuleData()
})
</script>

<style scoped>
.dashboard { max-width: 1400px; }
.metric-cards { margin-bottom: 20px; }
.metric-card {
  position: relative;
  overflow: hidden;
  text-align: center;
  padding: 10px;
}
.metric-value {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
}
.metric-value.running { color: #67C23A; }
.metric-value.completed { color: #E6A23C; }
.metric-value.rate { color: #409EFF; }
.metric-label { font-size: 14px; color: #909399; margin-top: 4px; }
.metric-icon { position: absolute; right: 16px; top: 16px; opacity: 0.2; }
</style>
