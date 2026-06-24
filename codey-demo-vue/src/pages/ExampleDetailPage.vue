<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getExampleById } from '../examples/exampleRegistry'

const route = useRoute()
const router = useRouter()

const activeExample = computed(() => getExampleById(route.params.exampleId))

function backToHome() {
  router.push('/')
}
</script>

<template>
  <div class="detail-page">
    <template v-if="activeExample">
      <header class="detail-header">
        <div class="detail-header-left">
          <el-button text size="small" @click="backToHome">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
          <div>
            <h1 class="detail-title">{{ activeExample.title }}</h1>
          </div>
        </div>
        <div class="detail-tags">
          <span v-for="tag in activeExample.tags" :key="tag" class="detail-tag">{{ tag }}</span>
        </div>
      </header>

      <div class="detail-stage">
        <KeepAlive>
          <component :is="activeExample.component" :key="activeExample.id" />
        </KeepAlive>
      </div>
    </template>

    <el-empty v-else description="未找到对应示例">
      <el-button type="primary" @click="backToHome">返回导航页</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  height: 100%;
  padding: 8px;
  box-sizing: border-box;
  background: #f5f7fa;
  overflow: hidden;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.detail-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.detail-title {
  margin: 0;
  color: #111827;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.2;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px;
}

.detail-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  border-radius: 4px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
}

.detail-stage {
  flex: 1;
  min-height: 0;
  padding: 8px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  overflow: hidden;
}

.detail-stage :deep(> *) {
  height: 100%;
}

@media (max-width: 900px) {
  .detail-header {
    flex-direction: column;
  }

  .detail-tags {
    justify-content: flex-start;
  }
}
</style>
