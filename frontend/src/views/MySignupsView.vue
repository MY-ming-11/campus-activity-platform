<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { signupApi } from '@/api'
import { formatWhen } from '@/utils/format'

const router = useRouter()
const loading = ref(true)
const signups = ref([])

const STATUS = {
  SIGNED: { label: '已报名', type: 'success' },
  CHECKED: { label: '已签到', type: 'primary' },
  CANCELED: { label: '已取消', type: 'info' }
}

async function load() {
  loading.value = true
  try {
    signups.value = await signupApi.mine()
  } finally {
    loading.value = false
  }
}

async function cancel(item) {
  try {
    await ElMessageBox.confirm(`确定要取消「${item.title}」的报名吗？名额会退回给其他人。`, '取消报名', {
      confirmButtonText: '取消报名',
      cancelButtonText: '再想想',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await signupApi.cancel(item.activityId)
    ElMessage.success('已取消报名')
    await load()
  } catch {
    // 提示已由拦截器处理
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">我的报名</h1>
        <p class="page-sub">这里是你报过的所有活动，取消后名额会立刻退回去。</p>
      </div>
    </div>

    <el-skeleton v-if="loading" :rows="4" animated />

    <div v-else-if="signups.length === 0" class="empty surface">
      <p class="empty__title">你还没有报名任何活动</p>
      <p class="empty__hint">
        去 <router-link to="/activities" class="empty__link">活动列表</router-link> 看看有什么正在报名。
      </p>
    </div>

    <div v-else class="list">
      <article v-for="item in signups" :key="item.signupId" class="row surface">
        <div class="row__main">
          <div class="row__when tnum">{{ formatWhen(item.startTime) }}</div>
          <h2 class="row__title">
            <router-link :to="`/activities/${item.activityId}`">{{ item.title }}</router-link>
          </h2>
          <p class="row__where">{{ item.location || '地点待定' }}</p>
        </div>

        <div class="row__side">
          <el-tag :type="STATUS[item.status]?.type || 'info'" effect="light" round>
            {{ STATUS[item.status]?.label || item.status }}
          </el-tag>
          <el-button v-if="item.status === 'SIGNED'" link type="primary" @click="router.push('/checkin')">
            去签到
          </el-button>
          <el-button v-if="item.status === 'SIGNED'" link type="danger" @click="cancel(item)">
            取消报名
          </el-button>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.list {
  display: grid;
  gap: 12px;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 20px;
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

.row__title a {
  color: inherit;
  text-decoration: none;
}

.row__title a:hover {
  color: var(--el-color-primary-light-3);
}

.row__where {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
}

.row__side {
  display: flex;
  align-items: center;
  gap: 14px;
  flex: none;
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
    align-items: flex-start;
  }
}
</style>
