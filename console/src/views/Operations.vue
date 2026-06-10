<template>
  <div>
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card>
          <template #header>
            <span><el-icon><Refresh /></el-icon> 重试征信查询</span>
          </template>
          <el-form :model="retryForm" label-width="100px">
            <el-form-item label="流程实例ID">
              <el-input v-model="retryForm.processInstanceId" placeholder="请输入流程实例ID" />
            </el-form-item>
            <el-form-item label="操作人">
              <el-input v-model="retryForm.operator" placeholder="请输入操作人" />
            </el-form-item>
            <el-form-item>
              <el-button type="warning" @click="handleRetry" :loading="retryLoading">执行重试</el-button>
            </el-form-item>
          </el-form>
          <el-alert type="warning" :closable="false" show-icon>
            此操作将重新触发征信查询，请确认流程实例当前处于征信查询节点。
          </el-alert>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card>
          <template #header>
            <span><el-icon><Right /></el-icon> 跳过节点</span>
          </template>
          <el-form :model="skipForm" label-width="100px">
            <el-form-item label="流程实例ID">
              <el-input v-model="skipForm.processInstanceId" placeholder="请输入流程实例ID" />
            </el-form-item>
            <el-form-item label="目标节点ID">
              <el-input v-model="skipForm.targetNodeId" placeholder="跳转目标节点ID" />
            </el-form-item>
            <el-form-item label="跳过原因">
              <el-input v-model="skipForm.reason" type="textarea" :rows="2" placeholder="请说明跳过原因" />
            </el-form-item>
            <el-form-item label="操作人">
              <el-input v-model="skipForm.operator" placeholder="请输入操作人" />
            </el-form-item>
            <el-form-item>
              <el-button type="danger" @click="handleSkip" :loading="skipLoading">执行跳过</el-button>
            </el-form-item>
          </el-form>
          <el-alert type="error" :closable="false" show-icon>
            跳过节点为高风险操作，将直接完成当前任务并跳转到目标节点。所有操作将被审计记录。
          </el-alert>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card>
          <template #header>
            <span><el-icon><Edit /></el-icon> 修改规则测试结果</span>
          </template>
          <el-form :model="ruleTestForm" label-width="100px">
            <el-form-item label="流程实例ID">
              <el-input v-model="ruleTestForm.processInstanceId" placeholder="请输入流程实例ID" />
            </el-form-item>
            <el-form-item label="规则编码">
              <el-input v-model="ruleTestForm.targetNodeId" placeholder="规则编码如 FRAUD_001" />
            </el-form-item>
            <el-form-item label="测试数据">
              <el-input v-model="ruleTestForm.testData" type="textarea" :rows="3" placeholder='JSON格式如 {"hit":false,"score":0}' />
            </el-form-item>
            <el-form-item label="操作人">
              <el-input v-model="ruleTestForm.operator" placeholder="请输入操作人" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleModifyRule" :loading="ruleLoading">执行修改</el-button>
            </el-form-item>
          </el-form>
          <el-alert type="info" :closable="false" show-icon>
            此操作将覆盖指定规则的测试结果，仅用于测试验证，所有修改将被审计记录。
          </el-alert>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { retryCreditQuery, skipNode, modifyRuleTestResult } from '../api/monitor'
import { ElMessage, ElMessageBox } from 'element-plus'

const retryLoading = ref(false)
const skipLoading = ref(false)
const ruleLoading = ref(false)

const retryForm = reactive({ processInstanceId: '', operator: '' })
const skipForm = reactive({ processInstanceId: '', targetNodeId: '', reason: '', operator: '' })
const ruleTestForm = reactive({ processInstanceId: '', targetNodeId: '', testData: '', operator: '' })

async function handleRetry() {
  if (!retryForm.processInstanceId) return ElMessage.warning('请输入流程实例ID')
  try {
    await ElMessageBox.confirm('确认要重试征信查询吗？', '操作确认', { type: 'warning' })
    retryLoading.value = true
    await retryCreditQuery(retryForm)
    ElMessage.success('征信查询重试已触发')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  } finally {
    retryLoading.value = false
  }
}

async function handleSkip() {
  if (!skipForm.processInstanceId || !skipForm.targetNodeId) return ElMessage.warning('请填写必填项')
  try {
    await ElMessageBox.confirm('确认要跳过当前节点吗？此操作不可逆！', '危险操作确认', { type: 'error' })
    skipLoading.value = true
    await skipNode(skipForm)
    ElMessage.success('节点跳过成功')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  } finally {
    skipLoading.value = false
  }
}

async function handleModifyRule() {
  if (!ruleTestForm.processInstanceId || !ruleTestForm.targetNodeId) return ElMessage.warning('请填写必填项')
  try {
    await ElMessageBox.confirm('确认要修改规则测试结果吗？', '操作确认', { type: 'warning' })
    ruleLoading.value = true
    await modifyRuleTestResult(ruleTestForm)
    ElMessage.success('规则测试结果已修改')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  } finally {
    ruleLoading.value = false
  }
}
</script>
