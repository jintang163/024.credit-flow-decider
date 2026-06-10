<template>
  <div>
    <el-card>
      <template #header>待办任务列表（人工复核）</template>
      <el-form inline>
        <el-form-item label="处理人">
          <el-input v-model="assignee" placeholder="请输入处理人" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadTasks">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="taskList" border stripe v-loading="loading">
        <el-table-column prop="taskId" label="任务ID" width="180" />
        <el-table-column prop="taskName" label="任务名称" width="140" />
        <el-table-column prop="taskKey" label="任务Key" width="160" />
        <el-table-column prop="businessKey" label="申请单号" width="160" />
        <el-table-column prop="processDefinitionName" label="流程名称" width="140" />
        <el-table-column prop="assignee" label="处理人" width="120" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewTask(row)">查看</el-button>
            <el-button link type="success" @click="handleApprove(row)">审批</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadTasks"
        style="margin-top: 16px; justify-content: flex-end"
      />
    </el-card>

    <el-dialog v-model="approveDialog" title="审批操作" width="500px">
      <el-form :model="approveForm" label-width="80px">
        <el-form-item label="审批结果">
          <el-select v-model="approveForm.result">
            <el-option label="通过" value="PASS" />
            <el-option label="拒绝" value="REJECT" />
            <el-option label="退回" value="RETURN" />
          </el-select>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input v-model="approveForm.opinion" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="审批金额">
          <el-input-number v-model="approveForm.approveAmount" :min="0" :precision="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialog = false">取消</el-button>
        <el-button type="primary" @click="submitApprove">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { queryTodoTasks } from '../api/monitor'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const taskList = ref([])
const total = ref(0)
const assignee = ref('')
const page = ref(1)
const size = ref(10)
const approveDialog = ref(false)
const approveForm = reactive({ taskId: '', result: 'PASS', opinion: '', approveAmount: null })

async function loadTasks() {
  loading.value = true
  try {
    const res = await queryTodoTasks({ assignee: assignee.value, page: page.value, size: size.value })
    taskList.value = res.data || []
    total.value = res.data?.length || 0
  } finally {
    loading.value = false
  }
}

function viewTask(row) {
  ElMessage.info(`任务详情: ${row.taskName}`)
}

function handleApprove(row) {
  approveForm.taskId = row.taskId
  approveDialog.value = true
}

function submitApprove() {
  ElMessage.success('审批操作已提交')
  approveDialog.value = false
}

onMounted(() => loadTasks())
</script>
