import request from '../utils/request'

export function queryProcessInstances(data) {
  return request.post('/api/monitor/process-instances', data)
}

export function queryTodoTasks(params) {
  return request.get('/api/monitor/todo-tasks', { params })
}

export function getProcessDiagram(processInstanceId) {
  return request.get(`/api/monitor/diagram/${processInstanceId}`)
}

export function getRuleHitStats(params) {
  return request.get('/api/monitor/rule-hit-stats', { params })
}

export function getCreditQueryPercentile(params) {
  return request.get('/api/monitor/credit-query-percentile', { params })
}

export function getLimitDistribution() {
  return request.get('/api/monitor/limit-distribution')
}

export function getWorkflowMetrics() {
  return request.get('/api/monitor/workflow-metrics')
}

export function retryCreditQuery(data) {
  return request.post('/api/ops/retry-credit', data)
}

export function skipNode(data) {
  return request.post('/api/ops/skip-node', data)
}

export function modifyRuleTestResult(data) {
  return request.post('/api/ops/modify-rule-test', data)
}

export function queryAuditLogs(params) {
  return request.get('/api/ops/audit-logs', { params })
}
