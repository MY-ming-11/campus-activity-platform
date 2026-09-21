<script setup>
import { defineAsyncComponent, reactive, ref } from 'vue'
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

/**
 * 演示账号快捷填充只在开发模式加载。
 *
 * 注意不能只写成 <div v-if="import.meta.env.DEV">：
 * v-if 是运行时判断，Vue 编译出来是个三元表达式，压缩器摇不掉，
 * 密码字符串照样会留在 JS 产物里，扒一下就能看到。
 *
 * 用动态 import 就不一样了：构建时 import.meta.env.DEV 被替换成 false，
 * 整个 import 落进死分支，Rollup 会连带模块一起摇掉，chunk 根本不会生成。
 */
const DemoAccounts = import.meta.env.DEV
  ? defineAsyncComponent(() => import('@/components/DemoAccounts.vue'))
  : null
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

        <component :is="DemoAccounts" v-if="DemoAccounts" @pick="fillDemo" />
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
