<script setup>
import { computed, onMounted, ref } from 'vue'
import { dashboardApi } from '@/api'

const loading = ref(true)
const data = ref(null)

const trend = computed(() => data.value?.trend || [])
const activities = computed(() => data.value?.activities || [])
const maxTrend = computed(() => Math.max(1, ...trend.value.map((p) => p.count)))
const maxSigned = computed(() => Math.max(1, ...activities.value.map((a) => a.signedCount)))

async function load() {
  loading.value = true
  try {
    data.value = await dashboardApi.overview()
  } finally {
    loading.value = false
  }
}

/** 柱子高度按当天报名数占最高的比例，0 的那天留一条极细的底，方便看出这一天存在但没有数据 */
function barHeight(count) {
  if (!count) return '2px'
  return `${Math.max(8, (count / maxTrend.value) * 100)}%`
}

/** 外层条长 = 该活动报名数占报名最多活动的比例 */
function signedWidth(activity) {
  if (!activity.signedCount) return '0%'
  return `${Math.max(4, (activity.signedCount / maxSigned.value) * 100)}%`
}

/** 内层绿色 = 报名的人里有多少真到场了 */
function checkedWidth(activity) {
  if (!activity.signedCount) return '0%'
  return `${(activity.checkedCount / activity.signedCount) * 100}%`
}

function shortDate(date) {
  return date?.slice(5) ?? ''
}

function rateClass(rate) {
  if (rate >= 80) return 'rate--good'
  if (rate < 50) return 'rate--low'
  return ''
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">数据看板</h1>
        <p class="page-sub">全部活动的整体情况。单个活动的现场名单在各自的核销台里。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-skeleton v-if="loading && !data" :rows="8" animated />

    <template v-else-if="data">
      <section class="stats">
        <div class="stat surface">
          <p class="stat__label">活动总数</p>
          <p class="stat__value tnum">{{ data.overview.activityTotal }}</p>
          <p class="stat__foot">其中已发布 {{ data.overview.publishedTotal }} 个</p>
        </div>
        <div class="stat surface">
          <p class="stat__label">报名总数</p>
          <p class="stat__value tnum">{{ data.overview.signupTotal }}</p>
          <p class="stat__foot">已取消的不计入</p>
        </div>
        <div class="stat surface">
          <p class="stat__label">已签到</p>
          <p class="stat__value stat__value--ok tnum">{{ data.overview.checkinTotal }}</p>
          <p class="stat__foot">含扫码与手动核销</p>
        </div>
        <div class="stat surface">
          <p class="stat__label">整体到场率</p>
          <p class="stat__value tnum">{{ data.overview.attendRate }}%</p>
          <p class="stat__foot">签到人数 ÷ 报名人数</p>
        </div>
      </section>

      <section class="panel surface">
        <div class="panel__head">
          <h2 class="panel__title">最近 14 天报名趋势</h2>
          <span class="label">最高的一天 {{ maxTrend }} 人</span>
        </div>

        <div class="trend">
          <div v-for="point in trend" :key="point.date" class="trend__col" :title="`${point.date}：${point.count} 人`">
            <span class="trend__value tnum">{{ point.count || '' }}</span>
            <div class="trend__track">
              <div
                class="trend__bar"
                :class="{ 'trend__bar--empty': !point.count }"
                :style="{ height: barHeight(point.count) }"
              ></div>
            </div>
            <span class="trend__date tnum">{{ shortDate(point.date) }}</span>
          </div>
        </div>
      </section>

      <section class="panel surface">
        <div class="panel__head">
          <h2 class="panel__title">活动报名排行</h2>
          <span class="legend">
            <span class="legend__item"><i class="legend__dot legend__dot--signed"></i>报名</span>
            <span class="legend__item"><i class="legend__dot legend__dot--checked"></i>到场</span>
          </span>
        </div>

        <div v-if="activities.length === 0" class="empty">还没有创建过活动。</div>

        <div v-else class="rank">
          <div v-for="activity in activities" :key="activity.id" class="rank__row">
            <div class="rank__head">
              <span class="rank__title">{{ activity.title }}</span>
              <span class="rank__meta">
                <span class="tnum">{{ activity.signedCount }}</span> 人报名 ·
                <span class="tnum">{{ activity.checkedCount }}</span> 人到场 ·
                <span class="tnum" :class="rateClass(activity.attendRate)">{{ activity.attendRate }}%</span>
              </span>
            </div>
            <div class="rank__track">
              <div class="rank__signed" :style="{ width: signedWidth(activity) }">
                <div class="rank__checked" :style="{ width: checkedWidth(activity) }"></div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.stat {
  padding: 20px 22px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat__label {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
}

.stat__value {
  margin: 0;
  font-size: 32px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.15;
}

.stat__value--ok {
  color: var(--ok);
}

.stat__foot {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
}

.panel {
  padding: 20px 22px 22px;
  margin-bottom: 16px;
}

.panel__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}

.panel__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.trend {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 190px;
}

.trend__col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  height: 100%;
}

.trend__value {
  font-size: 11px;
  color: var(--ink-soft);
  height: 15px;
}

.trend__track {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
}

.trend__bar {
  width: 100%;
  border-radius: 3px 3px 0 0;
  background: var(--ink);
  transition: height 0.3s ease;
}

.trend__bar--empty {
  background: var(--line);
}

.trend__date {
  font-size: 11px;
  color: var(--muted);
  white-space: nowrap;
}

.legend {
  display: flex;
  gap: 14px;
}

.legend__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--muted);
}

.legend__dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}

.legend__dot--signed {
  background: #d5dce5;
}

.legend__dot--checked {
  background: var(--ok);
}

.rank {
  display: grid;
  gap: 18px;
}

.rank__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}

.rank__title {
  font-size: 14px;
  font-weight: 600;
}

.rank__meta {
  font-size: 12px;
  color: var(--muted);
  flex: none;
}

.rank__track {
  height: 14px;
  background: #f2f5f8;
  border-radius: 7px;
  overflow: hidden;
}

.rank__signed {
  height: 100%;
  background: #d5dce5;
  border-radius: 7px;
  overflow: hidden;
  transition: width 0.35s ease;
}

.rank__checked {
  height: 100%;
  background: var(--ok);
  border-radius: 7px;
  transition: width 0.35s ease;
}

.rate--good {
  color: var(--ok);
  font-weight: 600;
}

.rate--low {
  color: var(--signal);
  font-weight: 600;
}

.empty {
  padding: 28px 4px;
  font-size: 13px;
  color: var(--muted);
}

@media (max-width: 900px) {
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
  .trend {
    gap: 3px;
  }
  .trend__date {
    transform: rotate(-60deg);
    transform-origin: center;
  }
}
</style>
