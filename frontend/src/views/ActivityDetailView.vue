<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { activityApi, signupApi } from '@/api'
import { useUserStore } from '@/stores/user'
import QuotaBar from '@/components/QuotaBar.vue'
import { formatRange } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const activityId = Number(route.params.id)
const loading = ref(true)
const submitting = ref(false)
const activity = ref(null)
const mySignup = ref(null)

const signedUp = computed(() => mySignup.value && mySignup.value.status !== 'CANCELED')

const closed = computed(() => {
  const item = activity.value
  if (!item) return false
  return item.status !== 'PUBLISHED' || item.remaining <= 0
})

const actionText = computed(() => {
  if (signedUp.value) return '取消报名'
  if (activity.value?.remaining <= 0) return '名额已满'
  if (activity.value?.status !== 'PUBLISHED') return '尚未开放'
  return '我要报名'
})

async function load() {
  loading.value = true
  try {
    activity.value = await activityApi.detail(activityId)
    if (user.logged) {
      const mine = await signupApi.mine()
      mySignup.value = mine.find((item) => item.activityId === activityId) || null
    }
  } finally {
    loading.value = false
  }
}

async function onAction() {
  if (!user.logged) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  submitting.value = true
  try {
    if (signedUp.value) {
      await signupApi.cancel(activityId)
      ElMessage.success('已取消报名，名额已经退回')
      // 取消是同步的：Redis 释放和数据库状态更新都在这个请求里完成，直接重查就行
      await load()
    } else {
      const result = await signupApi.signup(activityId)
      if (result.success) {
        ElMessage.success(`报名成功，还剩 ${result.remaining} 个名额`)
      } else {
        ElMessage.info(result.message)
      }
      // 报名落库是异步的（消息表每 200ms 消费一批），不能立刻回查"我的报名"，
      // 否则会查到还没落库的空结果，按钮就会从"我要报名"跳成"名额已满"。
      // 服务端已经明确告诉我们名额抢到了，界面直接按这个结果更新，再单独刷一次余量。
      mySignup.value = { activityId, status: 'SIGNED' }
      activity.value = await activityApi.detail(activityId)
    }
  } catch {
    // 名额已满之类的失败提示由拦截器统一弹出，这里刷新一下保证余量是最新的
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else-if="activity">
      <router-link to="/activities" class="back">← 返回活动列表</router-link>

      <div class="detail">
        <div class="detail__main">
          <div class="detail__when tnum">{{ formatRange(activity.startTime, activity.endTime) }}</div>
          <h1 class="detail__title">{{ activity.title }}</h1>

          <dl class="facts">
            <div class="facts__row">
              <dt class="label">活动地点</dt>
              <dd>{{ activity.location || '待定' }}</dd>
            </div>
            <div class="facts__row">
              <dt class="label">报名时间</dt>
              <dd class="tnum">{{ formatRange(activity.signupStart, activity.signupEnd) }}</dd>
            </div>
          </dl>

          <section class="detail__section">
            <h2 class="detail__h2">活动说明</h2>
            <p class="detail__body">
              {{ activity.description || '组织方还没有填写活动说明。' }}
            </p>
          </section>
        </div>

        <aside class="detail__aside surface">
          <QuotaBar :remaining="activity.remaining" :quota="activity.quota" />
          <p class="detail__total tnum">
            共 {{ activity.quota }} 个名额
          </p>

          <el-button
            v-if="signedUp"
            class="detail__action"
            size="large"
            :loading="submitting"
            @click="onAction"
          >
            取消报名
          </el-button>
          <el-button
            v-else
            class="detail__action"
            type="primary"
            size="large"
            :disabled="closed"
            :loading="submitting"
            @click="onAction"
          >
            {{ actionText }}
          </el-button>

          <p v-if="signedUp" class="detail__signed">
            你已经报名，记得按时到场签到。
          </p>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.back {
  display: inline-block;
  margin-bottom: 18px;
  font-size: 13px;
  color: var(--muted);
  text-decoration: none;
}

.back:hover {
  color: var(--ink);
}

.detail {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 32px;
  align-items: start;
}

.detail__when {
  font-size: 13px;
  font-weight: 600;
  color: var(--signal);
  margin-bottom: 10px;
}

.detail__title {
  margin: 0 0 24px;
  font-size: 30px;
  font-weight: 600;
  line-height: 1.3;
  letter-spacing: -0.02em;
}

.facts {
  margin: 0;
  padding: 18px 20px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  display: grid;
  gap: 14px;
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
  color: var(--ink);
}

.detail__section {
  margin-top: 34px;
}

.detail__h2 {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-soft);
}

.detail__body {
  margin: 0;
  font-size: 15px;
  line-height: 1.85;
  color: var(--ink-soft);
  white-space: pre-wrap;
}

.detail__aside {
  position: sticky;
  top: 84px;
  padding: 22px;
}

.detail__total {
  margin: 12px 0 18px;
  font-size: 13px;
  color: var(--muted);
}

.detail__action {
  width: 100%;
}

.detail__signed {
  margin: 14px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ok);
}

@media (max-width: 860px) {
  .detail {
    grid-template-columns: 1fr;
  }
  .detail__aside {
    position: static;
  }
  .detail__title {
    font-size: 23px;
  }
}
</style>
