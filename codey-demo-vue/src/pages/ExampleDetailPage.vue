<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { AiChatWorkspace, closeSystemAiAssistant } from 'codey-chat-workspace'
import { useAiAssistantHandlers } from '../composables/useAiAssistantHandlers'
import { getExampleById } from '../examples/exampleRegistry'

const route = useRoute()
const router = useRouter()
const { aiAssistantHandlers } = useAiAssistantHandlers()

const activeExample = computed(() => getExampleById(route.params.exampleId))

// AI 助手面板显隐控制
const aiPanelVisible = ref(false)

// 路由变动时关闭助手
watch(
  () => route.params.exampleId,
  () => {
    if (aiPanelVisible.value) {
      closeSystemAiAssistant()
      aiPanelVisible.value = false
    }
  },
)

function backToHome() {
  router.push('/')
}

function handleAiPanelVisibilityChange(visible) {
  aiPanelVisible.value = visible
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

      <!-- 内容区 + AI 面板并排 -->
      <div
        class="detail-body"
        :class="{ 'has-ai-panel': aiPanelVisible }"
      >
        <div class="detail-content-panel">
          <KeepAlive>
            <component :is="activeExample.component" :key="activeExample.id" />
          </KeepAlive>
        </div>
        <div
          class="detail-ai-panel"
          :class="{ 'is-visible': aiPanelVisible }"
        >
          <AiChatWorkspace
            v-bind="aiAssistantHandlers"
            title="codey"
            welcome-message="您好！我是您的AI助手，可以帮您填写和审查采购申请单。请问有什么可以帮助您的"
            subtitle="可结合当前页面上下文协助问答、分析和处理业务。"
            :compact-header="true"
            :show-working-directory="false"
            @visibility-change="handleAiPanelVisibilityChange"
           >
            <template #toolbar>
              <button class="ai-close-btn" @click="aiPanelVisible = false">关闭</button>
            </template>
          </AiChatWorkspace>
        </div>
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
  flex-shrink: 0;
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

/* 内容区 + AI 面板并排布局 */
.detail-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 10px;
  overflow: hidden;
}

.detail-content-panel {
  flex: 1;
  min-width: 0;
  padding: 8px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  overflow-y: auto;
}

.detail-ai-panel {
  width: 0;
  flex: 0 0 0;
  overflow: hidden;
  border-radius: 12px;
  background: #ffffff;
  border: none;
  transition: width 0.3s ease, flex-basis 0.3s ease;
}

.detail-ai-panel.is-visible {
  width: 35%;
  flex: 0 0 35%;
  border: 1px solid #e5e7eb;
}

.ai-close-btn {
  padding: 2px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #ffffff;
  color: #606266;
  font-size: 12px;
  cursor: pointer;
}

.ai-close-btn:hover {
  color: #409eff;
  border-color: #c6e2ff;
  background: #ecf5ff;
}

@media (max-width: 900px) {
  .detail-header {
    flex-direction: column;
  }

  .detail-tags {
    justify-content: flex-start;
  }

  .detail-body {
    flex-direction: column;
  }

  .detail-ai-panel.is-visible {
    width: 100%;
    flex: 0 0 auto;
    height: 460px;
  }
}
</style>
