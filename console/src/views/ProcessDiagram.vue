<template>
  <div>
    <el-card>
      <template #header>流程图实时高亮监控</template>
      <el-form inline>
        <el-form-item label="流程实例ID">
          <el-input v-model="processInstanceId" placeholder="请输入流程实例ID" style="width: 300px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadDiagram" :loading="loading">查看流程图</el-button>
        </el-form-item>
      </el-form>

      <div v-if="diagramInfo.status" class="diagram-info">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="流程实例ID">{{ diagramInfo.processInstanceId }}</el-descriptions-item>
          <el-descriptions-item label="业务Key">{{ diagramInfo.businessKey }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="diagramInfo.status === 'running' ? 'success' : 'info'">
              {{ diagramInfo.status === 'running' ? '运行中' : '已完成' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="当前活动节点" :span="3">
            <el-tag v-for="id in (diagramInfo.activeActivityIds || [])" :key="id" type="warning" style="margin-right: 8px">
              {{ id }}
            </el-tag>
            <span v-if="!diagramInfo.activeActivityIds?.length">-</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div v-if="diagramUrl" class="diagram-container">
        <img :src="diagramUrl" alt="流程图" class="diagram-image" />
      </div>

      <el-empty v-if="!diagramUrl && !loading" description="请输入流程实例ID查看流程图" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getProcessDiagram } from '../api/monitor'

const route = useRoute()
const loading = ref(false)
const processInstanceId = ref('')
const diagramInfo = ref({})
const diagramUrl = ref('')

async function loadDiagram() {
  if (!processInstanceId.value) return
  loading.value = true
  try {
    const res = await getProcessDiagram(processInstanceId.value)
    diagramInfo.value = res.data || {}
    diagramUrl.value = `/credit-flow/api/workflow/diagram/${processInstanceId.value}`
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (route.query.processInstanceId) {
    processInstanceId.value = route.query.processInstanceId
    loadDiagram()
  }
})
</script>

<style scoped>
.diagram-info { margin-bottom: 20px; }
.diagram-container {
  text-align: center;
  padding: 20px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
.diagram-image { max-width: 100%; height: auto; }
</style>
