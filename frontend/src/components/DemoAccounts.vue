<script setup>
/**
 * 登录页的演示账号快捷填充。
 *
 * 这个组件只在开发模式下被动态引入（见 LoginView.vue），
 * 生产构建时引用它的那句 import 落在死分支里，会被整个摇掉，
 * 连 chunk 都不会生成。所以部署到公网后，JS 产物里搜不到任何演示密码。
 */
const emit = defineEmits(['pick'])

const accounts = [
  { label: '学生 student / 123456', username: 'student', password: '123456' },
  { label: '管理员 admin / admin123', username: 'admin', password: 'admin123' }
]
</script>

<template>
  <div class="demo">
    <span class="label">演示账号（仅开发环境显示）</span>
    <div class="demo__row">
      <button
        v-for="account in accounts"
        :key="account.username"
        type="button"
        class="demo__btn"
        @click="emit('pick', account.username, account.password)"
      >
        {{ account.label }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.demo {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed var(--line);
}

.demo__row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 9px;
}

.demo__btn {
  border: 1px solid var(--line);
  background: #fbfcfe;
  border-radius: 7px;
  padding: 6px 11px;
  font-size: 12px;
  color: var(--ink-soft);
  cursor: pointer;
  font-family: inherit;
}

.demo__btn:hover {
  border-color: var(--signal);
  color: var(--signal);
}
</style>
