<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const user = useUserStore()
const router = useRouter()

function logout() {
  user.logout()
  router.push('/login')
}
</script>

<template>
  <div class="admin">
    <aside class="side">
      <div class="side__brand">
        <span class="side__mark" aria-hidden="true"></span>
        管理后台
      </div>
      <nav class="side__nav">
        <router-link to="/admin/activities" class="side__link">活动管理</router-link>
      </nav>
      <router-link to="/activities" class="side__back">← 回到前台</router-link>
    </aside>

    <div class="admin__main">
      <header class="admin__top">
        <span class="admin__user">
          {{ user.displayName }}
          <span class="admin__role">管理员</span>
        </span>
        <el-button link type="primary" @click="logout">退出登录</el-button>
      </header>
      <router-view />
    </div>
  </div>
</template>

<style scoped>
.admin {
  min-height: 100%;
  display: flex;
}

.side {
  width: 210px;
  flex: none;
  background: var(--ink);
  color: #c8d2de;
  padding: 22px 16px;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.side__brand {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  padding: 0 8px;
}

.side__mark {
  width: 13px;
  height: 13px;
  border-radius: 3px;
  background: var(--signal);
}

.side__nav {
  flex: 1;
}

.side__link {
  display: block;
  padding: 9px 12px;
  border-radius: 7px;
  color: #c8d2de;
  text-decoration: none;
  font-size: 14px;
}

.side__link:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

.side__link.router-link-active {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  font-weight: 600;
}

.side__back {
  color: #8c9cae;
  text-decoration: none;
  font-size: 13px;
  padding: 0 8px;
}

.side__back:hover {
  color: #fff;
}

.admin__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.admin__top {
  height: 58px;
  flex: none;
  background: #fff;
  border-bottom: 1px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 18px;
  padding: 0 26px;
}

.admin__user {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 14px;
}

.admin__role {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--signal-soft);
  color: var(--signal);
}

@media (max-width: 720px) {
  .admin {
    flex-direction: column;
  }
  .side {
    width: 100%;
    flex-direction: row;
    align-items: center;
    gap: 14px;
    padding: 12px 14px;
  }
  .side__nav {
    flex: 0;
  }
  .side__back {
    margin-left: auto;
  }
}
</style>
