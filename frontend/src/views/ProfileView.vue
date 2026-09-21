<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'

const user = useUserStore()
const router = useRouter()

const saving = ref(false)
const formRef = ref()
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const rules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再输一次新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function submitPassword() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    await authApi.changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    })
    // 改完直接退出登录：一是让用户确认新密码能登进去，
    // 二是避免旧 token 还挂在浏览器里造成困惑
    ElMessage.success('密码已修改，请用新密码重新登录')
    user.logout()
    router.push('/login')
  } catch {
    // 提示已由请求拦截器统一处理
  } finally {
    saving.value = false
  }
}

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

    <div class="cards">
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

      <div class="card surface">
        <h2 class="card__title">修改密码</h2>
        <p class="card__hint">改完之后需要用新密码重新登录。</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="当前密码" prop="oldPassword">
            <el-input v-model="form.oldPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="form.newPassword"
              type="password"
              show-password
              placeholder="6-32 位"
            />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" show-password />
          </el-form-item>
          <el-button type="primary" :loading="saving" @click="submitPassword">
            修改密码
          </el-button>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cards {
  max-width: 520px;
  display: grid;
  gap: 16px;
}

.card {
  padding: 24px;
}

.card__title {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
}

.card__hint {
  margin: 0 0 18px;
  font-size: 13px;
  color: var(--muted);
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
