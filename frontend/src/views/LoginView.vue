<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const user = useUserStore()
const router = useRouter()
const route = useRoute()

const tab = ref('login')
const loading = ref(false)
const loginRef = ref()
const registerRef = ref()

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', nickname: '', phone: '' })

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{4,16}$/, message: '4-16 位字母、数字或下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' }
  ]
}

async function submitLogin() {
  const valid = await loginRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await user.login(loginForm)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/activities')
  } catch {
    // 失败提示已经由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function submitRegister() {
  const valid = await registerRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await user.register(registerForm)
    ElMessage.success('注册成功，已经帮你登录了')
    router.push(route.query.redirect || '/activities')
  } catch {
    // 同上
  } finally {
    loading.value = false
  }
}

function fillDemo(username, password) {
  tab.value = 'login'
  loginForm.username = username
  loginForm.password = password
}
</script>

<template>
  <div class="login">
    <div class="login__panel">
      <div class="login__intro">
        <span class="login__mark" aria-hidden="true"></span>
        <h1 class="login__title">校园活动报名</h1>
        <p class="login__desc">找到想去的活动，抢一个名额，现场扫码签到。</p>
      </div>

      <div class="login__card surface">
        <el-tabs v-model="tab">
          <el-tab-pane label="登录" name="login">
            <el-form
              ref="loginRef"
              :model="loginForm"
              :rules="loginRules"
              label-position="top"
              @submit.prevent="submitLogin"
            >
              <el-form-item label="用户名" prop="username">
                <el-input v-model="loginForm.username" placeholder="请输入用户名" size="large" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  placeholder="请输入密码"
                  size="large"
                  show-password
                  @keyup.enter="submitLogin"
                />
              </el-form-item>
              <el-button
                type="primary"
                size="large"
                class="login__submit"
                :loading="loading"
                @click="submitLogin"
              >
                登录
              </el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="注册" name="register">
            <el-form
              ref="registerRef"
              :model="registerForm"
              :rules="registerRules"
              label-position="top"
              @submit.prevent="submitRegister"
            >
              <el-form-item label="用户名" prop="username">
                <el-input v-model="registerForm.username" placeholder="4-16 位字母、数字或下划线" size="large" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="registerForm.password"
                  type="password"
                  placeholder="至少 6 位"
                  size="large"
                  show-password
                />
              </el-form-item>
              <el-form-item label="昵称">
                <el-input v-model="registerForm.nickname" placeholder="选填" size="large" />
              </el-form-item>
              <el-button
                type="primary"
                size="large"
                class="login__submit"
                :loading="loading"
                @click="submitRegister"
              >
                注册并登录
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>

        <div class="login__demo">
          <span class="label">演示账号</span>
          <div class="login__demo-row">
            <button type="button" class="login__demo-btn" @click="fillDemo('student', '123456')">
              学生 student / 123456
            </button>
            <button type="button" class="login__demo-btn" @click="fillDemo('admin', 'admin123')">
              管理员 admin / admin123
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login {
  min-height: 100%;
  display: grid;
  place-items: center;
  padding: 40px 20px;
  background:
    radial-gradient(900px 420px at 12% -10%, #e8eef6 0%, rgba(232, 238, 246, 0) 60%),
    var(--paper);
}

.login__panel {
  width: 100%;
  max-width: 920px;
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: 56px;
  align-items: center;
}

.login__mark {
  display: block;
  width: 20px;
  height: 20px;
  border-radius: 4px;
  background: var(--signal);
  margin-bottom: 22px;
}

.login__title {
  margin: 0 0 12px;
  font-size: 34px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.login__desc {
  margin: 0;
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.7;
  max-width: 22em;
}

.login__card {
  padding: 26px 26px 22px;
  box-shadow: 0 1px 2px rgba(18, 35, 58, 0.04), 0 12px 32px rgba(18, 35, 58, 0.07);
}

.login__submit {
  width: 100%;
  margin-top: 4px;
}

.login__demo {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed var(--line);
}

.login__demo-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 9px;
}

.login__demo-btn {
  border: 1px solid var(--line);
  background: #fbfcfe;
  border-radius: 7px;
  padding: 6px 11px;
  font-size: 12px;
  color: var(--ink-soft);
  cursor: pointer;
  font-family: inherit;
}

.login__demo-btn:hover {
  border-color: var(--signal);
  color: var(--signal);
}

@media (max-width: 860px) {
  .login__panel {
    grid-template-columns: 1fr;
    gap: 28px;
    max-width: 420px;
  }
  .login__title {
    font-size: 26px;
  }
}
</style>
