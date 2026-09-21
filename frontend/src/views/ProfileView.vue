<script setup>
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const user = useUserStore()
const router = useRouter()

function logout() {
  user.logout()
  ElMessage.success('已经退出登录')
  router.push('/login')
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">个人中心</h1>
        <p class="page-sub">账号信息。</p>
      </div>
    </div>

    <div class="card surface">
      <dl class="facts">
        <div class="facts__row">
          <dt class="label">昵称</dt>
          <dd>{{ user.user?.nickname || '—' }}</dd>
        </div>
        <div class="facts__row">
          <dt class="label">用户名</dt>
          <dd>{{ user.user?.username || '—' }}</dd>
        </div>
        <div class="facts__row">
          <dt class="label">手机号</dt>
          <dd>{{ user.user?.phone || '未填写' }}</dd>
        </div>
        <div class="facts__row">
          <dt class="label">角色</dt>
          <dd>{{ user.isAdmin ? '管理员' : '学生' }}</dd>
        </div>
      </dl>

      <div class="actions">
        <el-button v-if="user.isAdmin" type="primary" @click="router.push('/admin/activities')">
          进入管理端
        </el-button>
        <el-button @click="logout">退出登录</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.card {
  max-width: 520px;
  padding: 24px;
}

.facts {
  margin: 0;
  display: grid;
  gap: 16px;
}

.facts__row {
  display: grid;
  grid-template-columns: 76px 1fr;
  gap: 12px;
  align-items: baseline;
}

.facts dt,
.facts dd {
  margin: 0;
}

.facts dd {
  font-size: 14px;
}

.actions {
  margin-top: 26px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
  display: flex;
  gap: 10px;
}
</style>
