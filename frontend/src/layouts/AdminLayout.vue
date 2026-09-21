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
        <router-link to="/admin/dashboard" class="side__link">数据看板</router-link>
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

  /* 侧边栏在手机上变成两行工具栏：
     第一行「品牌 …… 回到前台」，第二行导航。
     挤成一行的话「数据看板」会被压成一列一个字。 */
  .side {
    width: 100%;
    flex-direction: row;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
  }

  .side__brand {
    padding: 0;
    font-size: 14px;
    white-space: nowrap;
  }

  .side__nav {
    /* 强制换到第二行。用 flex: 0 0 100% 而不是 width: 100%，
       因为它是 flex 子项，flex-basis 才是决定换行的那个值 */
    flex: 0 0 100%;
    display: flex;
    gap: 4px;
  }

  .side__link {
    padding: 6px 10px;
    font-size: 13px;
    /* 不加这句，导航文字会被挤成竖排 */
    white-space: nowrap;
  }

  .side__back {
    margin-left: auto;
    padding: 0;
    font-size: 12px;
    white-space: nowrap;
  }

  .admin__top {
    height: 48px;
    padding: 0 14px;
    gap: 12px;
  }
}
</style>
