<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { activityApi } from '@/api'

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const saving = ref(false)
const formRef = ref()

const form = reactive({
  title: '',
  cover: '',
  description: '',
  location: '',
  startTime: '',
  endTime: '',
  signupStart: '',
  signupEnd: '',
  quota: 50,
  status: 'DRAFT'
})

const rules = {
  title: [
    { required: true, message: '请填写活动标题', trigger: 'blur' },
    { max: 64, message: '最长 64 个字符', trigger: 'blur' }
  ],
  startTime: [{ required: true, message: '请选择活动开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择活动结束时间', trigger: 'change' }],
  signupStart: [{ required: true, message: '请选择报名开始时间', trigger: 'change' }],
  signupEnd: [{ required: true, message: '请选择报名截止时间', trigger: 'change' }],
  quota: [{ required: true, message: '请填写名额', trigger: 'blur' }]
}

async function load() {
  if (!isEdit.value) return
  loading.value = true
  try {
    const data = await activityApi.detail(route.params.id)
    Object.assign(form, {
      title: data.title,
      cover: data.cover || '',
      description: data.description || '',
      location: data.location || '',
      startTime: data.startTime,
      endTime: data.endTime,
      signupStart: data.signupStart,
      signupEnd: data.signupEnd,
      quota: data.quota,
      status: data.status
    })
  } finally {
    loading.value = false
  }
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (form.endTime < form.startTime) {
    ElMessage.error('活动结束时间不能早于开始时间')
    return
  }
  if (form.signupStart > form.signupEnd) {
    ElMessage.error('报名开始时间不能晚于报名截止时间')
    return
  }
  if (form.signupEnd > form.startTime) {
    ElMessage.error('报名截止时间不能晚于活动开始时间')
    return
  }

  saving.value = true
  try {
    if (isEdit.value) {
      await activityApi.update(route.params.id, form)
      ElMessage.success('活动已更新')
    } else {
      await activityApi.create(form)
      ElMessage.success('活动已创建')
    }
    router.push('/admin/activities')
  } catch {
    // 提示已由拦截器处理
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">{{ isEdit ? '编辑活动' : '新建活动' }}</h1>
        <p class="page-sub">保存成草稿后，学生端不会看到；改成已发布才会出现在活动列表里。</p>
      </div>
      <el-button @click="router.push('/admin/activities')">返回列表</el-button>
    </div>

    <div class="surface form-card" v-loading="loading">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="活动标题" prop="title">
          <el-input v-model="form.title" placeholder="例如：人工智能前沿讲座" maxlength="64" show-word-limit />
        </el-form-item>

        <el-form-item label="活动地点">
          <el-input v-model="form.location" placeholder="例如：图书馆报告厅" />
        </el-form-item>

        <el-form-item label="活动说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            placeholder="写清楚活动内容、面向对象和注意事项"
          />
        </el-form-item>

        <div class="form-grid">
          <el-form-item label="活动开始" prop="startTime">
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择时间"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="活动结束" prop="endTime">
            <el-date-picker
              v-model="form.endTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择时间"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="报名开始" prop="signupStart">
            <el-date-picker
              v-model="form.signupStart"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择时间"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="报名截止" prop="signupEnd">
            <el-date-picker
              v-model="form.signupEnd"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择时间"
              style="width: 100%"
            />
          </el-form-item>
        </div>

        <div class="form-grid">
          <el-form-item label="名额" prop="quota">
            <el-input-number v-model="form.quota" :min="1" :max="10000" style="width: 100%" />
          </el-form-item>

          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="草稿（学生看不到）" value="DRAFT" />
              <el-option label="已发布（学生可报名）" value="PUBLISHED" />
              <el-option label="已结束" value="CLOSED" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="submit">
            {{ isEdit ? '保存修改' : '创建活动' }}
          </el-button>
          <el-button @click="router.push('/admin/activities')">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.form-card {
  max-width: 780px;
  padding: 26px 26px 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
