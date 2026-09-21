<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { checkinApi } from '@/api'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const activityId = Number(route.params.id)

const loading = ref(true)
const board = ref(null)
const current = ref({ code: '', secondsLeft: 0 })
const qrDataUrl = ref('')
const checkingId = ref(null)

let timer = null
let refreshing = false

const attendRate = computed(() => {
  const signed = board.value?.signedCount || 0
  if (!signed) return 0
  return Math.round(((board.value.checkedCount || 0) / signed) * 100)
})

async function loadCode() {
  if (refreshing) return
  refreshing = true
  try {
    const data = await checkinApi.code(activityId)
    current.value = data
    // 二维码里放的是一条带参数的链接，学生用手机相机扫一下就直达签到页，
    // 不用手动敲 6 位数字
    const url = `${window.location.origin}/checkin?activityId=${activityId}&code=${data.code}`
    qrDataUrl.value = await QRCode.toDataURL(url, {
      width: 220,
      margin: 1,
      color: { dark: '#12233a', light: '#ffffff' }
    })
  } finally {
    refreshing = false
  }
}

async function loadBoard() {
  board.value = await checkinApi.board(activityId)
}

async function manual(row) {
  checkingId.value = row.signupId
  try {
    const res = await checkinApi.manual(activityId, row.signupId)
    ElMessage[res.success ? 'success' : 'info'](`${row.nickname}：${res.message}`)
    await loadBoard()
  } catch {
    // 提示由拦截器处理
  } finally {
    checkingId.value = null
  }
}

function refreshAll() {
  return Promise.all([loadBoard(), loadCode()])
}

onMounted(async () => {
  loading.value = true
  try {
    await refreshAll()
  } finally {
    loading.value = false
  }
  // 本地倒计时，归零时再去后端换新码。这样不用每秒打一次接口。
  timer = setInterval(() => {
    if (current.value.secondsLeft > 0) {
      current.value.secondsLeft -= 1
    }
    if (current.value.secondsLeft <= 0) {
      loadCode()
    }
  }, 1000)
})

onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <router-link to="/admin/activities" class="back">← 返回活动列表</router-link>
        <h1 class="page-title">{{ board?.title || '核销台' }}</h1>
        <p class="page-sub">把二维码投到大屏上让学生扫，或者念出数字让他们手动输入。</p>
      </div>
      <el-button :loading="loading" @click="refreshAll">刷新</el-button>
    </div>

    <div class="console">
      <section class="code-card surface" v-loading="loading">
        <p class="label">现场签到码</p>
        <p class="code tnum">{{ current.code || '------' }}</p>
        <p class="code__hint">
          <span class="dot" aria-hidden="true"></span>
          {{ current.secondsLeft }} 秒后自动更换
        </p>

        <img v-if="qrDataUrl" :src="qrDataUrl" alt="签到二维码" class="qr" />
        <p class="qr__hint">扫码直达签到页，过期的二维码会提示签到码不正确</p>
      </section>

      <section class="stats">
        <div class="stat surface">
          <p class="stat__label">名额</p>
          <p class="stat__value tnum">{{ board?.quota ?? '—' }}</p>
        </div>
        <div class="stat surface">
          <p class="stat__label">已报名</p>
          <p class="stat__value tnum">{{ board?.signedCount ?? '—' }}</p>
        </div>
        <div class="stat surface">
          <p class="stat__label">已签到</p>
          <p class="stat__value stat__value--ok tnum">{{ board?.checkedCount ?? '—' }}</p>
        </div>
        <div class="stat surface">
          <p class="stat__label">到场率</p>
          <p class="stat__value tnum">{{ attendRate }}%</p>
        </div>
      </section>
    </div>

    <section class="roster surface">
      <div class="roster__head">
        <h2 class="roster__title">报名名单</h2>
        <span class="label">没带手机的同学可以用右边的「手动核销」</span>
      </div>

      <el-table :data="board?.records || []" v-loading="loading" style="width: 100%">
        <el-table-column label="姓名" min-width="150">
          <template #default="{ row }">
            <div class="cell-title">{{ row.nickname }}</div>
            <div class="cell-sub">@{{ row.username }}</div>
          </template>
        </el-table-column>

        <el-table-column label="手机号" width="140">
          <template #default="{ row }">
            <span class="cell-sub">{{ row.phone || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="报名时间" width="170">
          <template #default="{ row }">
            <span class="cell-sub tnum">{{ formatDateTime(row.signupTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="签到时间" width="170">
          <template #default="{ row }">
            <span class="cell-sub tnum">{{ row.checkTime ? formatDateTime(row.checkTime) : '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.checkTime" type="success" effect="light" round>
              {{ row.checkType === 'MANUAL' ? '手动已签' : '已签到' }}
            </el-tag>
            <el-tag v-else type="info" effect="light" round>未签到</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" align="right">
          <template #default="{ row }">
            <el-button
              v-if="!row.checkTime"
              link
              type="primary"
              :loading="checkingId === row.signupId"
              @click="manual(row)"
            >
              手动核销
            </el-button>
            <span v-else class="cell-sub">—</span>
          </template>
        </el-table-column>

        <template #empty>
          <div class="table-empty">还没有人报名这个活动。</div>
        </template>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.back {
  display: inline-block;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--muted);
  text-decoration: none;
}

.back:hover {
  color: var(--ink);
}

.console {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 16px;
  align-items: start;
  margin-bottom: 20px;
}

.code-card {
  padding: 26px;
  text-align: center;
}

.code {
  margin: 10px 0 6px;
  /* 用 clamp 跟着视口缩放：手机上 58px 的六位数加字距会顶出卡片。
     13vw 在 390px 宽的屏幕上约 50px，到桌面端封顶 58px。 */
  font-size: clamp(38px, 13vw, 58px);
  font-weight: 600;
  letter-spacing: 0.12em;
  line-height: 1.1;
  color: var(--signal);
}

.code__hint {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin: 0 0 20px;
  font-size: 12px;
  color: var(--muted);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--signal);
  animation: pulse 1.4s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.25;
  }
}

.qr {
  display: block;
  width: 200px;
  height: 200px;
  margin: 0 auto 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
}

.qr__hint {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
  line-height: 1.6;
}

.stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.stat {
  padding: 22px 24px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  min-height: 106px;
}

.stat__label {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
}

.stat__value {
  margin: 0;
  font-size: 34px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.stat__value--ok {
  color: var(--ok);
}

.roster {
  padding: 6px 10px 10px;
}

.roster__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 10px 10px;
}

.roster__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.cell-title {
  font-size: 14px;
  font-weight: 600;
}

.cell-sub {
  font-size: 12px;
  color: var(--muted);
}

.table-empty {
  padding: 26px;
  color: var(--muted);
  font-size: 13px;
}

@media (max-width: 900px) {
  .console {
    grid-template-columns: 1fr;
  }
}
</style>
