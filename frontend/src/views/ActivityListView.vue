<script setup>
import { onMounted, ref, watch } from 'vue'
import { activityApi } from '@/api'
import QuotaBar from '@/components/QuotaBar.vue'
import { formatWhen } from '@/utils/format'

const loading = ref(true)
const activities = ref([])
const keyword = ref('')
const total = ref(0)
const page = ref(1)
const pageSize = 9

async function load() {
  loading.value = true
  try {
    const data = await activityApi.list({
      page: page.value,
      size: pageSize,
      keyword: keyword.value || undefined
    })
    activities.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

let searchTimer
watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    load()
  }, 300)
})

watch(page, load)
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">正在报名</h1>
        <p class="page-sub">名额有限，先到先得。点开活动可以看到详情和剩余名额。</p>
      </div>
      <el-input
        v-model="keyword"
        placeholder="搜索活动名称"
        clearable
        style="width: 240px"
      />
    </div>

    <div v-if="loading" class="grid">
      <div v-for="n in 6" :key="n" class="card card--skeleton surface">
        <el-skeleton :rows="3" animated />
      </div>
    </div>

    <div v-else-if="activities.length === 0" class="empty surface">
      <p class="empty__title">这里还没有活动</p>
      <p class="empty__hint">
        {{ keyword ? '换个关键词试试。' : '等活动组织方发布之后，就会出现在这里。' }}
      </p>
    </div>

    <div v-else class="grid">
      <router-link
        v-for="activity in activities"
        :key="activity.id"
        class="card surface"
        :to="`/activities/${activity.id}`"
      >
        <div class="card__when tnum">{{ formatWhen(activity.startTime) }}</div>
        <h2 class="card__title">{{ activity.title }}</h2>
        <p class="card__where">{{ activity.location || '地点待定' }}</p>
        <QuotaBar :remaining="activity.remaining" :quota="activity.quota" />
      </router-link>
    </div>

    <div v-if="total > pageSize" class="pager">
      <el-pagination
        v-model:current-page="page"
        layout="prev, pager, next"
        :page-size="pageSize"
        :total="total"
        background
      />
    </div>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(310px, 1fr));
  gap: 14px;
}

.card {
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: 20px;
  text-decoration: none;
  color: inherit;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.card:hover {
  border-color: #c9d2dd;
  box-shadow: 0 2px 4px rgba(18, 35, 58, 0.04), 0 10px 24px rgba(18, 35, 58, 0.07);
  transform: translateY(-1px);
}

.card--skeleton {
  pointer-events: none;
}

.card__when {
  font-size: 12px;
  font-weight: 600;
  color: var(--signal);
  letter-spacing: 0.01em;
}

.card__title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.45;
  letter-spacing: -0.01em;
}

.card__where {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
  flex: 1;
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

.pager {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}
</style>
