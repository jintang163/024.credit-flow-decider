import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: { title: '监控总览' },
  },
  {
    path: '/process-instances',
    name: 'ProcessInstances',
    component: () => import('../views/ProcessInstances.vue'),
    meta: { title: '流程实例查询' },
  },
  {
    path: '/todo-tasks',
    name: 'TodoTasks',
    component: () => import('../views/TodoTasks.vue'),
    meta: { title: '待办任务' },
  },
  {
    path: '/process-diagram',
    name: 'ProcessDiagram',
    component: () => import('../views/ProcessDiagram.vue'),
    meta: { title: '流程图监控' },
  },
  {
    path: '/rule-stats',
    name: 'RuleStats',
    component: () => import('../views/RuleStats.vue'),
    meta: { title: '规则命中统计' },
  },
  {
    path: '/credit-percentile',
    name: 'CreditPercentile',
    component: () => import('../views/CreditPercentile.vue'),
    meta: { title: '征信耗时监控' },
  },
  {
    path: '/limit-distribution',
    name: 'LimitDistribution',
    component: () => import('../views/LimitDistribution.vue'),
    meta: { title: '额度分布' },
  },
  {
    path: '/operations',
    name: 'Operations',
    component: () => import('../views/Operations.vue'),
    meta: { title: '运维操作' },
  },
  {
    path: '/audit-logs',
    name: 'AuditLogs',
    component: () => import('../views/AuditLogs.vue'),
    meta: { title: '审计日志' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  document.title = `${to.meta.title || '监控平台'} - 信贷审批工作流`
  next()
})

export default router
