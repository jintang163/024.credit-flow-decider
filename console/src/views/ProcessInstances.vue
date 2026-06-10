<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>流程实例查询</span>
        </div>
      </template>
      <el-form :model="queryForm" inline>
        <el-form-item label="申请单号">
          <el-input v-model="queryForm.applicationNo" placeholder="请输入申请单号" clearable />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="queryForm.idCard" placeholder="请输入身份证号" clearable />
        </el-form-item>
        <el-form-item label="流程状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable>
            <el-option label="运行中" value="running" />
            <el-option label="已挂起" value="suspended" />
            <el-option label="已完成" value="completed" />
            <el-option label="已终止" value="terminated" />
          </el-select>
        </el-form-item>
        <el-form-item label="流程Key">
          <el-input v-model="queryForm.processKey" placeholder="流程定义Key" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe style="width: 100%" v-loading="loading">
        <el-table-column prop="processInstanceId" label="流程实例ID" width="180" />
        <el-table-column prop="processDefinitionName" label="流程名称" width="160" />
        <el-table-column prop="businessKey" label="申请单号" width="160" />
        <el-table-column prop="currentActivityId" label="当前节点" width="160" />
        <el-table-column prop="startTime" label="启动时间" width="180" />
        <el-table-column prop="endTime" label="结束时间" width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDiagram(row)">流程图</el-button>
            <el-button link type="primary" @click="viewVariables(row)">变量</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryForm.page"
        v-model:page-size="queryForm.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="handleSearch"
        style="margin-top: 16px; justify-content: flex-end"
      />
    </el-card>

    <el-dialog v-model="variablesDialog" title="流程变量" width="600px">
      <el-descriptions :column="1" border>
        <el-descriptions-item v-for="(value, key) in currentVariables" :key="key" :label="key">
          {{ JSON.stringify(value) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { queryProcessInstances } from '../api/monitor'

const router = useRouter()
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const variablesDialog = ref(false)
const currentVariables = ref({})

const queryForm = reactive({
  applicationNo: '',
  idCard: '',
  status: '',
  processKey: '',
  page: 1,
  size: 10,
})

function statusTagType(status) {
  const map = { running: 'success', suspended: 'warning', completed: 'info', terminated: 'danger' }
  return map[status] || 'info'
}

function statusLabel(status) {
  const map = { running: '运行中', suspended: '已挂起', completed: '已完成', terminated: '已终止' }
  return map[status] || status
}

async function handleSearch() {
  loading.value = true
  try {
    const res = await queryProcessInstances(queryForm)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleReset() {
  Object.assign(queryForm, { applicationNo: '', idCard: '', status: '', processKey: '', page: 1, size: 10 })
  handleSearch()
}

function viewDiagram(row) {
  router.push({ path: '/process-diagram', query: { processInstanceId: row.processInstanceId } })
}

function viewVariables(row) {
  currentVariables.value = row.variables || {}
  variablesDialog.value = true
}

handleSearch()
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
