<script setup>
import { computed } from 'vue'

/**
 * 余量刻度条——这个项目的签名元素。
 *
 * 它是全站唯一真正重要的数字的可视化：名额还剩多少。
 * 所以它既是装饰也是信息，不额外做别的花活。
 */
const props = defineProps({
  remaining: { type: Number, default: 0 },
  quota: { type: Number, default: 0 }
})

const percent = computed(() => {
  if (!props.quota) return 0
  return Math.max(0, Math.min(100, (props.remaining / props.quota) * 100))
})

const state = computed(() => {
  if (props.remaining <= 0) return 'sold-out'
  return percent.value <= 20 ? 'tight' : 'open'
})

const text = computed(() => {
  if (props.remaining <= 0) return '名额已满'
  if (state.value === 'tight') return `只剩 ${props.remaining} 个`
  return `还剩 ${props.remaining} / ${props.quota}`
})
</script>

<template>
  <div class="quota" :class="`quota--${state}`">
    <div class="quota__track">
      <div class="quota__fill" :style="{ width: `${percent}%` }"></div>
    </div>
    <span class="quota__text tnum">{{ text }}</span>
  </div>
</template>

<style scoped>
.quota {
  display: flex;
  align-items: center;
  gap: 10px;
}

.quota__track {
  flex: 1;
  height: 4px;
  border-radius: 2px;
  background: #eef1f5;
  overflow: hidden;
}

.quota__fill {
  height: 100%;
  border-radius: 2px;
  background: var(--ok);
  transition: width 0.35s ease;
}

.quota__text {
  flex: none;
  font-size: 12px;
  color: var(--muted);
}

.quota--tight .quota__fill {
  background: var(--signal);
}

.quota--tight .quota__text {
  color: var(--signal);
  font-weight: 600;
}

.quota--sold-out .quota__fill {
  background: #c7cdd6;
}

.quota--sold-out .quota__text {
  color: var(--muted);
}
</style>
