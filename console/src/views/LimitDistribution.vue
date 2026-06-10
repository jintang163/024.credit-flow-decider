<template>
  <div>
    <el-card>
      <template #header>额度分布仪表盘</template>
      <el-row :gutter="20">
        <el-col :span="12">
          <v-chart :option="pieOption" style="height: 400px" autoresize />
        </el-col>
        <el-col :span="12">
          <v-chart :option="barOption" style="height: 400px" autoresize />
        </el-col>
      </el-row>
    </el-card>

    <el-card style="margin-top: 20px">
      <template #header>额度分布明细</template>
      <el-table :data="distributionData" border stripe>
        <el-table-column prop="range" label="额度区间" width="160" />
        <el-table-column prop="count" label="数量" width="120" sortable />
        <el-table-column prop="percentage" label="占比(%)" width="160">
          <template #default="{ row }">
            <el-progress :percentage="parseFloat(row.percentage) || 0" :stroke-width="14" :text-inside="true" />
          </template>
        </el-table-column>
        <el-table-column prop="avgAmount" label="平均额度(元)" width="180">
          <template #default="{ row }">
            {{ formatAmount(row.avgAmount) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { getLimitDistribution } from '../api/monitor'

use([CanvasRenderer, BarChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const distributionData = ref([])

const pieOption = computed(() => ({
  title: { text: '额度区间分布', left: 'center' },
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { orient: 'vertical', left: 'left', top: 'middle' },
  series: [{
    type: 'pie',
    radius: ['35%', '65%'],
    center: ['55%', '55%'],
    avoidLabelOverlap: false,
    itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
    label: { show: true, formatter: '{b}\n{d}%' },
    data: distributionData.value.map(item => ({
      name: item.range,
      value: item.count
    }))
  }]
}))

const barOption = computed(() => ({
  title: { text: '额度区间数量', left: 'center' },
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: distributionData.value.map(d => d.range) },
  yAxis: { type: 'value' },
  series: [{
    type: 'bar',
    data: distributionData.value.map(d => d.count),
    itemStyle: {
      color: (params) => {
        const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#7b68ee']
        return colors[params.dataIndex % colors.length]
      }
    },
    label: { show: true, position: 'top' }
  }]
}))

function formatAmount(amount) {
  if (!amount) return '-'
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(amount)
}

async function loadDistribution() {
  try {
    const res = await getLimitDistribution()
    distributionData.value = res.data || []
  } catch (e) { /* ignore */ }
}

onMounted(() => loadDistribution())
</script>
