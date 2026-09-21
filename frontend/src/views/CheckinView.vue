<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { checkinApi, signupApi } from '@/api'
import { formatWhen } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const submitting = ref(false)
const signups = ref([])
const codes = reactive({})
const result = ref(null)

/**
 * 签到窗口和后端保持一致：活动开始前 1 小时开放，结束后 1 小时关闭。
 * 这里只是提前告诉学生"还不能签"，真正的判定仍然以后端为准，
 * 前端改不改都绕不过去。
 */
function isOpen(item) {
  const start = new Date(String(item.startTime).replace(' ', 'T')).getTime()
  const end = new Date(String(item.endTime).replace(' ', 'T')).getTime()
  const now = Date.now()
  return now >= start - 3600_000 && now <= end + 3600_000
}

async function load() {
  loading.value = true
  try {
    const mine = await signupApi.mine()
    // 只有还没签到的才需要出现在这个页面
    signups.value = mine.filter((item) => item.status === 'SIGNED')
    // 现在能签的排在最前面，省得学生在一堆活动里找
    signups.value.sort((a, b) => Number(isOpen(b)) - Number(isOpen(a)))
  } finally {
    loading.value = false
  }
}

async function submit(activityId, title) {
  const code = String(codes[activityId] || '').trim()
  if (!/^\d{6}$/.test(code)) {
    ElMessage.warning('签到码是 6 位数字')
    return
  }

  submitting.value = true
  try {
    const res = await checkinApi.checkin(activityId, code)
    result.value = { success: res.success, message: res.message, title: res.activityTitle || title }
    ElMessage[res.success ? 'success' : 'info'](res.message)
    if (res.success) {
      await load()
    }
  } catch {
    // 码不对、没报名、不在签到时间，提示都由拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await load()

  // 扫现场大屏二维码进来时带着 activityId 和 code，直接帮他签掉
  const { activityId, code } = route.query
  if (activityId && code) {
    codes[activityId] = code
    const target = signups.value.find((item) => String(item.activityId) === String(activityId))
    await submit(Number(activityId), target?.title)
    // 把参数从地址栏去掉，避免刷新时重复提交
    router.replace({ path: '/checkin' })
  }
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">现场签到</h1>
        <p class="page-sub">输入现场大屏上显示的 6 位签到码。签到码每 30 秒换一次，截图发给别人会失效。</p>
      </div>
    </div>

    <div v-if="result" class="result surface" :class="result.success ? 'result--ok' : 'result--info'">
      <p class="result__title">{{ result.message }}</p>
      <p class="result__sub">{{ result.title }}</p>
    </div>

    <el-skeleton v-if="loading" :rows="3" animated />

    <div v-else-if="signups.length === 0" class="empty surface">
      <p class="empty__title">没有待签到的活动</p>
      <p class="empty__hint">
        先 <router-link to="/activities" class="empty__link">报名一个活动</router-link>，活动开始前 1 小时这里会出现签到入口。
      </p>
    </div>

    <div v-else class="list">
      <article v-for="item in signups" :key="item.signupId" class="row surface">
        <div class="row__main">
          <div class="row__when tnum">{{ formatWhen(item.startTime) }}</div>
          <h2 class="row__title">{{ item.title }}</h2>
          <p class="row__where">{{ item.location || '地点待定' }}</p>
        </div>

        <div class="row__form">
          <template v-if="isOpen(item)">
            <el-input
              v-model="codes[item.activityId]"
              placeholder="6 位签到码"
              maxlength="6"
              size="large"
              class="row__input"
              @keyup.enter="submit(item.activityId, item.title)"
            />
            <el-button
              type="primary"
              size="large"
              :loading="submitting"
              @click="submit(item.activityId, item.title)"
            >
              签到
            </el-button>
          </template>
          <span v-else class="row__pending">活动开始前 1 小时开放签到</span>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.result {
  padding: 20px 22px;
  margin-bottom: 16px;
  border-left: 3px solid var(--ok);
}

.result--info {
  border-left-color: var(--muted);
}

.result__title {
  margin: 0 0 4px;
  font-size: 17px;
  font-weight: 600;
}

.result__sub {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
}

.list {
  display: grid;
  gap: 12px;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 20px;
}

.row__when {
  font-size: 12px;
  font-weight: 600;
  color: var(--signal);
  margin-bottom: 6px;
}

.row__title {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
}

.row__where {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
}

.row__form {
  display: flex;
  gap: 10px;
  flex: none;
}

.row__input {
  width: 150px;
}

.row__pending {
  font-size: 13px;
  color: var(--muted);
}

.empty {
  padding: 64px 24px;
  text-align: center;
}

.empty__title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
}

.empty__hint {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

.empty__link {
  color: var(--signal);
}

@media (max-width: 640px) {
  .row {
    flex-direction: column;
    align-items: stretch;
  }
  .row__input {
    flex: 1;
    width: auto;
  }
}
</style>
