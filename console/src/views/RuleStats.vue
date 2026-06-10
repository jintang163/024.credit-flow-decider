<template>
  <div>
    <el-card>
      <template #header>反欺诈规则命中统计</template>
      <el-form inline>
        <el-form-item label="开始日期">
          <el-date-picker v-model="startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" />
        </el-form-item>
        <el-form-item label="规则组">
          <el-select v-model="ruleGroup" placeholder="全部" clearable>
            <el-option label="默认组" value="default" />
            <el-option label="A组" value="A" />
            <el-option label="B组" value="B" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadStats">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="statsData" border stripe v-loading="loading" default-sort="{ prop: 'hitCount', order: 'descending' }">
        <el-table-column prop="ruleCode" label="规则编码" width="140" />
        <el-table-column prop="ruleName" label="规则名称" width="160" />
        <el-table-column prop="ruleGroup" label="规则组" width="100" />
        <el-table-column prop="executeCount" label="执行次数" width="120" sortable />
        <el-table-column prop="hitCount" label="命中次数" width="120" sortable />
        <el-table-column prop="hitRate" label="命中率(%)" width="120" sortable>
          <template #default="{ row }">
            <el-progress :percentage="parseFloat(row.hitRate) || 0" :stroke-width="14" :text-inside="true"
              :color="row.hitRate > 50 ? '#F56C6C' : row.hitRate > 20 ? '#E6A23C' : '#67C23A'" />
          </template>
        </el-table-column>
        <el-table-column prop="avgScore" label="平均分值" width="100" />
        <el-table-column prop="rejectCount" label="拒绝次数" width="100" sortable />
        <el-table-column prop="alertCount" label="告警次数" width="100" sortable />
        <el-table-column prop="statsDate" label="统计日期" width="120" />
      </el-table>
    </el-card>

    <el-card style="margin-top: 20px">
      <template #header>规则命中排行</template>
      <v-chart :option="chartOption" style="height: 400px" autoresize />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent } from 'echarts/components'
import { getRuleHitStats } from '../api/monitor'

use([CanvasRenderer, BarChart, TitleComponent, TooltipComponent, GridComponent])

const loading = ref(false)
const statsData = ref([])
const startDate = ref('')
const endDate = ref('')
const ruleGroup = ref('')

const chartOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  legend: { data: ['命中次数', '拒绝次数', '告警次数'] },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: { type: 'category', data: statsData.value.map(r => r.ruleName), axisLabel: { rotate: 30 } },
  yAxis: { type: 'value' },
  series: [
    { name: '命中次数', type: 'bar', data: statsData.value.map(r => r.hitCount), itemStyle: { color: '#E6A23C' } },
    { name: '拒绝次数', type: 'bar', data: statsData.value.map(r => r.rejectCount), itemStyle: { color: '#F56C6C' } },
    { name: '告警次数', type: 'bar', data: statsData.value.map(r => r.alertCount), itemStyle: { color: '#409EFF' } },
  ]
}))

async function loadStats() {
  loading.value = true
  try {
    const res = await getRuleHitStats({
      startDate: startDate.value,
      endDate: endDate.value,
      ruleGroup: ruleGroup.value,
    })
    statsData.value = res.data || []
  } finally {
    loading.value = false
  }
}

onMounted(() => loadStats())
</script>
