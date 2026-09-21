<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { activityApi } from '@/api'
import QuotaBar from '@/components/QuotaBar.vue'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const loading = ref(true)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const keyword = ref('')
const status = ref('')

const STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  PUBLISHED: { label: '已发布', type: 'success' },
  CLOSED: { label: '已结束', type: 'warning' }
}

async function load() {
  loading.value = true
  try {
    const data = await activityApi.adminList({
      page: page.value,
      size: pageSize,
      keyword: keyword.value || undefined,
      status: status.value || undefined
    })
    rows.value = data.records
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

watch(status, () => {
  page.value = 1
  load()
})

watch(page, load)

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `删除「${row.title}」之后无法恢复，确定继续吗？`,
      '删除活动',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await activityApi.remove(row.id)
    ElMessage.success('活动已删除')
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
        <h1 class="page-title">活动管理</h1>
        <p class="page-sub">草稿状态的活动不会出现在学生端，确认没问题再改成已发布。</p>
      </div>
      <el-button type="primary" @click="router.push('/admin/activities/new')">
        新建活动
      </el-button>
    </div>

    <div class="filters">
      <el-input v-model="keyword" placeholder="搜索活动名称" clearable style="width: 240px" />
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 150px">
        <el-option label="草稿" value="DRAFT" />
        <el-option label="已发布" value="PUBLISHED" />
        <el-option label="已结束" value="CLOSED" />
      </el-select>
    </div>

    <div class="surface table-wrap">
      <el-table :data="rows" v-loading="loading" style="width: 100%">
        <el-table-column label="活动" min-width="220">
          <template #default="{ row }">
            <div class="cell-title">{{ row.title }}</div>
            <div class="cell-sub">{{ row.location || '地点待定' }}</div>
          </template>
        </el-table-column>

        <el-table-column label="活动时间" min-width="180">
          <template #default="{ row }">
            <div class="cell-sub tnum">{{ formatDateTime(row.startTime) }}</div>
            <div class="cell-sub tnum">至 {{ formatDateTime(row.endTime) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="报名截止" min-width="160">
          <template #default="{ row }">
            <span class="cell-sub tnum">{{ formatDateTime(row.signupEnd) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="名额" min-width="170">
          <template #default="{ row }">
            <QuotaBar :remaining="row.remaining" :quota="row.quota" />
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS[row.status]?.type || 'info'" effect="light" round>
              {{ STATUS[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="190" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/admin/activities/${row.id}/checkins`)">
              核销台
            </el-button>
            <el-button link type="primary" @click="router.push(`/admin/activities/${row.id}/edit`)">
              编辑
            </el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <div class="table-empty">
            {{ keyword || status ? '没有符合条件的活动。' : '还没有创建过活动。' }}
          </div>
        </template>
      </el-table>
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
.filters {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.table-wrap {
  padding: 6px 10px;
}

.cell-title {
  font-weight: 600;
  font-size: 14px;
}

.cell-sub {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.7;
}

.table-empty {
  padding: 26px;
  color: var(--muted);
  font-size: 13px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
