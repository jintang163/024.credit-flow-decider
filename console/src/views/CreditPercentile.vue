<template>
  <div>
    <el-card>
      <template #header>征信耗时百分位监控</template>
      <el-form inline>
        <el-form-item label="开始日期">
          <el-date-picker v-model="startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" />
        </el-form-item>
        <el-form-item label="数据源">
          <el-select v-model="dataSource" placeholder="全部" clearable>
            <el-option label="央行征信" value="PBOC" />
            <el-option label="百行征信" value="BAIHANG" />
            <el-option label="社保" value="SOCIAL_SECURITY" />
            <el-option label="公积金" value="HOUSING_FUND" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadPercentile">查询</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="20" class="percentile-cards">
        <el-col :span="4">
          <el-card shadow="hover" class="p-card">
            <div class="p-label">P50</div>
            <div class="p-value">{{ percentile.p50 || 0 }} ms</div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="hover" class="p-card">
            <div class="p-label">P90</div>
            <div class="p-value warning">{{ percentile.p90 || 0 }} ms</div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="hover" class="p-card">
            <div class="p-label">P95</div>
            <div class="p-value danger">{{ percentile.p95 || 0 }} ms</div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="hover" class="p-card">
            <div class="p-label">P99</div>
            <div class="p-value critical">{{ percentile.p99 || 0 }} ms</div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="hover" class="p-card">
            <div class="p-label">平均耗时</div>
            <div class="p-value">{{ percentile.avgCostMs || 0 }} ms</div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="hover" class="p-card">
            <div class="p-label">总查询数</div>
            <div class="p-value">{{ percentile.totalCount || 0 }}</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card style="margin-top: 20px">
      <template #header>SLA达标情况</template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="P90 SLA (≤3000ms)">
          <el-tag :type="(percentile.p90 || 0) <= 3000 ? 'success' : 'danger'">
            {{ (percentile.p90 || 0) <= 3000 ? '达标' : '未达标' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="P95 SLA (≤5000ms)">
          <el-tag :type="(percentile.p95 || 0) <= 5000 ? 'success' : 'danger'">
            {{ (percentile.p95 || 0) <= 5000 ? '达标' : '未达标' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCreditQueryPercentile } from '../api/monitor'

const startDate = ref('')
const endDate = ref('')
const dataSource = ref('')
const percentile = ref({})

async function loadPercentile() {
  try {
    const res = await getCreditQueryPercentile({
      startDate: startDate.value,
      endDate: endDate.value,
      dataSource: dataSource.value,
    })
    percentile.value = res.data || {}
  } catch (e) { /* ignore */ }
}

onMounted(() => loadPercentile())
</script>

<style scoped>
.percentile-cards { margin: 20px 0; }
.p-card { text-align: center; padding: 10px; }
.p-label { font-size: 14px; color: #909399; margin-bottom: 8px; }
.p-value { font-size: 24px; font-weight: 700; color: #303133; }
.p-value.warning { color: #E6A23C; }
.p-value.danger { color: #F56C6C; }
.p-value.critical { color: #c45656; }
</style>
