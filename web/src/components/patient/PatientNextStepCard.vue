<template>
  <el-card shadow="hover" class="next-step-card" :class="[`is-${toneClass}`]">
    <div class="next-step-card__hero">
      <div class="next-step-card__content">
        <div class="next-step-card__eyebrow">患者下一步卡</div>
        <h2>{{ nextStep.title }}</h2>
        <p>{{ nextStep.action }}</p>
      </div>

      <div class="next-step-card__badges">
        <el-tag effect="dark" :type="urgencyTagType">{{ urgencyLabel }}</el-tag>
        <el-tag effect="plain">{{ stageLabel }}</el-tag>
      </div>
    </div>

    <div class="next-step-card__grid">
      <article class="next-step-card__item">
        <span>当前阶段</span>
        <strong>{{ stageLabel }}</strong>
      </article>
      <article class="next-step-card__item">
        <span>建议位置</span>
        <strong>{{ nextStep.locationHint || '请留意现场屏幕与导诊指引' }}</strong>
      </article>
      <article class="next-step-card__item">
        <span>是否需要靠近诊室</span>
        <strong>{{ proximityLabel }}</strong>
        <small>{{ proximityHint }}</small>
      </article>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PatientNextStep } from '@/types/patient-queue'

const props = defineProps<{
  nextStep: PatientNextStep
}>()

const stageMap: Record<string, string> = {
  NONE: '暂无排队',
  REGISTERED: '已挂号',
  ARRIVED: '已到诊',
  TRIAGED: '已分诊',
  WAITING: '排队中',
  CALLING: '叫号中',
  MISSED: '已过号',
  IN_TREATMENT: '就诊中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

const stageLabel = computed(() => stageMap[props.nextStep.stage] || props.nextStep.stage || '当前状态')

const urgencyLabel = computed(() => {
  switch (props.nextStep.urgency) {
    case 'IMMEDIATE':
      return '请立即处理'
    case 'HIGH':
      return '请尽快处理'
    case 'LOW':
      return '流程已结束'
    default:
      return '按指引进行'
  }
})

const urgencyTagType = computed<'danger' | 'warning' | 'info' | 'success'>(() => {
  switch (props.nextStep.urgency) {
    case 'IMMEDIATE':
      return 'danger'
    case 'HIGH':
      return 'warning'
    case 'LOW':
      return 'success'
    default:
      return 'info'
  }
})

const toneClass = computed(() => {
  switch (props.nextStep.urgency) {
    case 'IMMEDIATE':
      return 'immediate'
    case 'HIGH':
      return 'high'
    case 'LOW':
      return 'low'
    default:
      return 'normal'
  }
})

const proximityLabel = computed(() => {
  switch (props.nextStep.urgency) {
    case 'IMMEDIATE':
      return '现在就去'
    case 'HIGH':
      return '请尽快靠近'
    case 'LOW':
      return '无需继续候诊'
    default:
      return props.nextStep.stage === 'WAITING' ? '暂时不必靠近' : '按当前区域等待'
  }
})

const proximityHint = computed(() => {
  switch (props.nextStep.urgency) {
    case 'IMMEDIATE':
      return '请不要离开当前就诊区域'
    case 'HIGH':
      return '建议优先处理当前步骤'
    case 'LOW':
      return '如有疑问可咨询现场工作人员'
    default:
      return '留意现场屏幕、广播与工作人员通知'
  }
})
</script>

<style scoped>
.next-step-card {
  position: relative;
  border: none;
  border-radius: 30px;
  overflow: hidden;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(242, 251, 253, 0.96));
  box-shadow: 0 22px 52px rgba(148, 163, 184, 0.14);
}

.next-step-card::before {
  content: "";
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at top right, rgba(34, 211, 238, 0.18), transparent 14rem),
    radial-gradient(circle at bottom left, rgba(16, 185, 129, 0.12), transparent 16rem);
  pointer-events: none;
}

.next-step-card::after {
  content: "";
  position: absolute;
  inset: auto -44px -54px auto;
  width: 164px;
  height: 164px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.42), transparent 70%);
  pointer-events: none;
}

.next-step-card :deep(.el-card__body) {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 18px;
  padding: 24px;
}

.next-step-card__hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.next-step-card__content {
  max-width: 760px;
}

.next-step-card__eyebrow {
  display: inline-flex;
  padding: 6px 12px;
  margin-bottom: 10px;
  border-radius: 999px;
  background: rgba(8, 145, 178, 0.08);
  border: 1px solid rgba(8, 145, 178, 0.12);
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--primary-strong, #0f766e);
}

.next-step-card h2 {
  margin: 0;
  font-size: clamp(30px, 3.1vw, 38px);
  line-height: 1.08;
  letter-spacing: -0.04em;
  color: var(--title-color, #0f172a);
}

.next-step-card p {
  margin: 12px 0 0;
  font-size: 15px;
  line-height: 1.8;
  color: var(--text-color, #475569);
}

.next-step-card__badges {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.next-step-card__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.next-step-card__item {
  position: relative;
  overflow: hidden;
  padding: 18px 18px 20px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.next-step-card__item::after {
  content: "";
  position: absolute;
  inset: auto -24px -26px auto;
  width: 88px;
  height: 88px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.12), transparent 72%);
  pointer-events: none;
}

.next-step-card__item span {
  display: block;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--muted-color, #64748b);
}

.next-step-card__item strong {
  display: block;
  margin-top: 8px;
  font-size: 18px;
  line-height: 1.4;
  color: #0f172a;
}

.next-step-card__item small {
  display: block;
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--muted-color, #64748b);
}

.next-step-card.is-normal {
  border: 1px solid rgba(8, 145, 178, 0.12);
}

.next-step-card.is-high {
  background:
    linear-gradient(135deg, rgba(255, 251, 235, 0.98), rgba(255, 247, 237, 0.96));
  box-shadow: 0 22px 52px rgba(245, 158, 11, 0.14);
  border: 1px solid rgba(245, 158, 11, 0.18);
}

.next-step-card.is-immediate {
  background:
    linear-gradient(135deg, rgba(255, 241, 242, 0.98), rgba(255, 247, 237, 0.96));
  box-shadow: 0 24px 56px rgba(239, 68, 68, 0.16);
  border: 1px solid rgba(248, 113, 113, 0.22);
}

.next-step-card.is-low {
  background:
    linear-gradient(135deg, rgba(240, 253, 244, 0.98), rgba(236, 253, 245, 0.96));
  border: 1px solid rgba(34, 197, 94, 0.16);
}

@media (max-width: 840px) {
  .next-step-card__hero {
    flex-direction: column;
  }

  .next-step-card__badges {
    justify-content: flex-start;
  }

  .next-step-card__grid {
    grid-template-columns: 1fr;
  }

  .next-step-card h2 {
    font-size: 26px;
  }

  .next-step-card :deep(.el-card__body) {
    padding: 20px 18px;
  }
}
</style>
