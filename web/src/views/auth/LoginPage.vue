<template>
  <div class="login-page" :class="`login-page--${portalTheme}`">
    <section class="login-page__brand">
      <div class="login-page__eyebrow">{{ portalLabel }}</div>
      <h1>患者智能排队分诊系统</h1>
      <p>{{ portalLead }}</p>
      <div class="login-page__tips">
        <el-tag>管理后台</el-tag>
        <el-tag>导诊建档</el-tag>
        <el-tag>分诊评估</el-tag>
        <el-tag>诊室叫号</el-tag>
        <el-tag>候诊大屏</el-tag>
      </div>
      <div class="login-page__support">
        <strong>统一登录入口</strong>
        <p>请使用已分配的账号登录对应端口，系统将按岗位自动进入管理后台、工作台或候诊展示页面。</p>
      </div>
    </section>

    <el-card class="login-card">
      <div class="login-card__meta">
        <div class="login-card__eyebrow">账号登录</div>
        <h2>欢迎使用</h2>
        <p>请输入用户名和密码，进入当前系统工作界面。</p>
      </div>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-button type="primary" :loading="submitting" @click="handleLogin">登录系统</el-button>
      </el-form>
      <div class="login-card__support">如遇账号或权限问题，请联系系统管理员或门诊信息支持人员。</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPortalAccessDeniedMessage, portalDisplayName, portalSurface, resolvePortalHomeRoute } from '@/config/portal'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const submitting = ref(false)
const portalLabel = portalDisplayName
const portalTheme = portalSurface
const portalLeadMap = {
  admin: '面向医院运营、分诊治理与规则维护的管理入口，强调秩序、质量和全局态势。',
  workstation: '面向导诊台、分诊护士和诊室医生的协同入口，强调效率、确认和闭环操作。',
  screen: '面向候诊公示与诊室提示的大屏入口，强调远距可读、清晰导视和就诊秩序。',
  all: '覆盖管理、工作台与候诊展示的统一入口，便于联调与综合演示。'
} as const
const portalLead = portalLeadMap[portalSurface]

const form = reactive({
  username: '',
  password: ''
})

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  submitting.value = true
  try {
    await authStore.login(form)
    const targetRoute = resolvePortalHomeRoute(authStore.profile)
    if (targetRoute === '/login') {
      authStore.reset()
      ElMessage.error(getPortalAccessDeniedMessage())
      return
    }
    ElMessage.success('登录成功')
    await router.replace(targetRoute)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败，请检查后端服务和登录信息')
  } finally {
    submitting.value = false
  }
}
</script>
