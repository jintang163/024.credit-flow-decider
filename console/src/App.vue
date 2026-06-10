<template>
  <el-container class="app-container">
    <el-aside width="220px" class="app-aside">
      <div class="logo-section">
        <el-icon :size="28" color="#409EFF"><Monitor /></el-icon>
        <span class="logo-title">信贷审批监控</span>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#1d1e1f"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>监控总览</span>
        </el-menu-item>
        <el-menu-item index="/process-instances">
          <el-icon><Search /></el-icon>
          <span>流程实例查询</span>
        </el-menu-item>
        <el-menu-item index="/todo-tasks">
          <el-icon><List /></el-icon>
          <span>待办任务</span>
        </el-menu-item>
        <el-menu-item index="/process-diagram">
          <el-icon><Share /></el-icon>
          <span>流程图监控</span>
        </el-menu-item>
        <el-sub-menu index="stats">
          <template #title>
            <el-icon><TrendCharts /></el-icon>
            <span>统计分析</span>
          </template>
          <el-menu-item index="/rule-stats">规则命中统计</el-menu-item>
          <el-menu-item index="/credit-percentile">征信耗时监控</el-menu-item>
          <el-menu-item index="/limit-distribution">额度分布</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="ops">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>运维管理</span>
          </template>
          <el-menu-item index="/operations">运维操作</el-menu-item>
          <el-menu-item index="/audit-logs">审计日志</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-tag type="success" effect="dark" size="small">系统在线</el-tag>
          <el-dropdown>
            <span class="user-info">
              <el-icon><User /></el-icon> 管理员
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>个人设置</el-dropdown-item>
                <el-dropdown-item divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
</script>

<style>
html, body, #app {
  margin: 0;
  padding: 0;
  height: 100%;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif;
}

.app-container {
  height: 100vh;
}

.app-aside {
  background-color: #1d1e1f;
  overflow-y: auto;
}

.logo-section {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 0;
  border-bottom: 1px solid #333;
}

.logo-title {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  margin-left: 8px;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e6e6e6;
  background: #fff;
  padding: 0 20px;
  height: 56px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #606266;
  font-size: 14px;
}

.app-main {
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
</style>
