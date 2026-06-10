<template>
  <div>
    <el-card>
      <template #header>审计日志</template>
      <el-form :model="queryForm" inline>
        <el-form-item label="操作类型">
          <el-select v-model="queryForm.operationType" placeholder="全部" clearable>
            <el-option label="重试征信" value="RETRY_CREDIT" />
            <el-option label="跳过节点" value="SKIP_NODE" />
            <el-option label="修改规则测试" value="MODIFY_RULE_TEST" />
            <el-option label="部署" value="DEPLOY" />
            <el-option label="挂起" value="SUSPEND" />
            <el-option label="激活" value="ACTIVATE" />
            <el-option label="终止" value="TERMINATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作模块">
          <el-select v-model="queryForm.operationModule" placeholder="全部" clearable>
            <el-option label="监控" value="MONITOR" />
            <el-option label="流程" value="WORKFLOW" />
            <el-option label="审批" value="APPROVAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="queryForm.operator" placeholder="操作人" clearable />
        </el-form-item>
        <el-form-item label="目标ID">
          <el-input v-model="queryForm.targetId" placeholder="目标ID" clearable />
        </el-form-item>
        <el-form-item label="是否成功">
          <el-select v-model="queryForm.success" placeholder="全部" clearable>
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="operationType" label="操作类型" width="140">
          <template #default="{ row }">
            <el-tag :type="opTypeTag(row.operationType)">{{ row.operationType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operationModule" label="操作模块" width="100" />
        <el-table-column prop="operationDesc" label="操作描述" width="200" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="targetId" label="目标ID" width="180" show-overflow-tooltip />
        <el-table-column prop="targetType" label="目标类型" width="140" />
        <el-table-column prop="clientIp" label="客户端IP" width="140" />
        <el-table-column prop="success" label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.success === 1 ? 'success' : 'danger'">
              {{ row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时(ms)" width="100" />
        <el-table-column prop="operationTime" label="操作时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryForm.page"
        v-model:page-size="queryForm.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="handleSearch"
        style="margin-top: 16px; justify-content: flex-end"
      />
    </el-card>

    <el-dialog v-model="detailDialog" title="审计日志详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="操作类型">{{ currentLog.operationType }}</el-descriptions-item>
        <el-descriptions-item label="操作模块">{{ currentLog.operationModule }}</el-descriptions-item>
        <el-descriptions-item label="操作描述" :span="2">{{ currentLog.operationDesc }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ currentLog.operator }}</el-descriptions-item>
        <el-descriptions-item label="客户端IP">{{ currentLog.clientIp }}</el-descriptions-item>
        <el-descriptions-item label="目标ID">{{ currentLog.targetId }}</el-descriptions-item>
        <el-descriptions-item label="目标类型">{{ currentLog.targetType }}</el-descriptions-item>
        <el-descriptions-item label="是否成功">
          <el-tag :type="currentLog.success === 1 ? 'success' : 'danger'">
            {{ currentLog.success === 1 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentLog.costMs }} ms</el-descriptions-item>
        <el-descriptions-item label="操作时间" :span="2">{{ currentLog.operationTime }}</el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2">
          <pre class="json-pre">{{ formatJson(currentLog.requestParams) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="响应结果" :span="2">
          <pre class="json-pre">{{ formatJson(currentLog.responseResult) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog.errorMsg" label="错误信息" :span="2">
          <span style="color: #F56C6C">{{ currentLog.errorMsg }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { queryAuditLogs } from '../api/monitor'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const detailDialog = ref(false)
const currentLog = ref({})

const queryForm = reactive({
  operationType: '',
  operationModule: '',
  operator: '',
  targetId: '',
  success: undefined,
  page: 1,
  size: 10,
})

function opTypeTag(type) {
  const map = { RETRY_CREDIT: 'warning', SKIP_NODE: 'danger', MODIFY_RULE_TEST: 'primary', DEPLOY: 'success', SUSPEND: 'warning', ACTIVATE: 'success', TERMINATE: 'danger' }
  return map[type] || 'info'
}

function formatJson(str) {
  if (!str) return '-'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

function viewDetail(row) {
  currentLog.value = row
  detailDialog.value = true
}

async function handleSearch() {
  loading.value = true
  try {
    const params = { ...queryForm }
    if (params.success === undefined || params.success === '') delete params.success
    const res = await queryAuditLogs(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleReset() {
  Object.assign(queryForm, { operationType: '', operationModule: '', operator: '', targetId: '', success: undefined, page: 1, size: 10 })
  handleSearch()
}

onMounted(() => handleSearch())
</script>

<style scoped>
.json-pre {
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>
