<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const user = useUserStore()
const router = useRouter()

function onCommand(command) {
  if (command === 'logout') {
    user.logout()
    router.push('/login')
  } else if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'admin') {
    router.push('/admin/activities')
  }
}
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <div class="topbar__inner">
        <router-link to="/activities" class="brand">
          <span class="brand__mark" aria-hidden="true"></span>
          <span class="brand__name">校园活动</span>
        </router-link>

        <nav class="nav">
          <router-link to="/activities" class="nav__link">找活动</router-link>
          <router-link v-if="user.logged" to="/my-signups" class="nav__link">我的报名</router-link>
        </nav>

        <div class="account">
          <template v-if="user.logged">
            <el-dropdown @command="onCommand">
              <button class="account__trigger" type="button">
                {{ user.displayName }}
                <span v-if="user.isAdmin" class="account__badge">管理员</span>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                  <el-dropdown-item v-if="user.isAdmin" command="admin">进入管理端</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <el-button v-else type="primary" @click="router.push('/login')">登录</el-button>
        </div>
      </div>
    </header>

    <main>
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--line);
}

.topbar__inner {
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 24px;
  height: 60px;
  display: flex;
  align-items: center;
  gap: 28px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  text-decoration: none;
  color: var(--ink);
}

.brand__mark {
  width: 14px;
  height: 14px;
  border-radius: 3px;
  background: var(--signal);
}

.brand__name {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.nav {
  display: flex;
  gap: 4px;
  flex: 1;
}

.nav__link {
  padding: 7px 12px;
  border-radius: 7px;
  font-size: 14px;
  color: var(--ink-soft);
  text-decoration: none;
  transition: background 0.15s ease, color 0.15s ease;
}

.nav__link:hover {
  background: #f0f3f7;
  color: var(--ink);
}

.nav__link.router-link-active {
  color: var(--ink);
  font-weight: 600;
  background: #eef1f5;
}

.account__trigger {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 8px;
  padding: 7px 13px;
  font-size: 14px;
  color: var(--ink);
  cursor: pointer;
  font-family: inherit;
}

.account__trigger:hover {
  border-color: #c9d2dd;
}

.account__badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--signal-soft);
  color: var(--signal);
}

main {
  flex: 1;
}

@media (max-width: 640px) {
  .topbar__inner {
    padding: 0 14px;
    gap: 14px;
  }
  .nav {
    gap: 0;
  }
  .nav__link {
    padding: 7px 8px;
    font-size: 13px;
  }
}
</style>
