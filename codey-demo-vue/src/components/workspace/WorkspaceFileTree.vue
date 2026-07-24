<script setup>
import { Document, Folder, MoreFilled } from '@element-plus/icons-vue'

const props = defineProps({
  entries: {
    type: Array,
    default: () => [],
  },
  fileCount: {
    type: Number,
    default: 0,
  },
  activeFileKey: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['select', 'create-file', 'create-directory', 'rename'])

function buildNodePayload(node = null) {
  return node && typeof node === 'object' ? { ...node } : {}
}

function handleNodeClick(node) {
  emit('select', buildNodePayload(node))
}

function handleCreateFile(parentNode = null) {
  emit('create-file', buildNodePayload(parentNode))
}

function handleCreateDirectory(parentNode = null) {
  emit('create-directory', buildNodePayload(parentNode))
}

function handleRename(node) {
  emit('rename', buildNodePayload(node))
}

function handleActionCommand(node, command) {
  if (command === 'rename') {
    handleRename(node)
    return
  }
  if (command === 'create-directory') {
    handleCreateDirectory(node)
    return
  }
  if (command === 'create-file') {
    handleCreateFile(node)
  }
}
</script>

<template>
  <el-card shadow="never" class="sidebar-card">
    <template #header>
      <div class="card-header-row">      
        <div class="header-actions">
          <el-button size="small" @click="handleCreateDirectory()">新建目录</el-button>
          <el-button size="small" type="primary" @click="handleCreateFile()">新建文件</el-button>
        </div>
      </div>
    </template>
    <div class="sidebar-body">
      <el-empty v-if="!props.entries.length" description="当前没有可展示文件" />
      <el-scrollbar v-else class="sidebar-tree-scroll">
        <el-tree
          :data="props.entries"
          node-key="fileKey"
          default-expand-all
          :expand-on-click-node="false"
          @node-click="handleNodeClick"
        >
          <template #default="{ data }">
            <div
              class="tree-node"
              :class="{
                active: activeFileKey === data.fileKey,
                directory: data.directory,
                file: !data.directory,
              }"
            >
              <div class="tree-main">
                <el-icon class="tree-icon" :class="data.directory ? 'directory' : 'file'">
                  <Folder v-if="data.directory" />
                  <Document v-else />
                </el-icon>
                <span class="tree-label">{{ data.label }}</span>
              </div>
              <div class="tree-actions">
                <el-dropdown
                  trigger="click"
                  placement="bottom-end"
                  @command="(command) => handleActionCommand(data, command)"
                >
                  <el-button size="small" text class="tree-action-trigger" @click.stop>
                    操作
                    <el-icon><MoreFilled /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="rename">重命名</el-dropdown-item>
                      <el-dropdown-item v-if="data.creatable" command="create-directory">新建目录</el-dropdown-item>
                      <el-dropdown-item v-if="data.creatable" command="create-file">新建文件</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
          </template>
        </el-tree>
      </el-scrollbar>
    </div>
  </el-card>
</template>

<style scoped>
.sidebar-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.card-header-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.header-title-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.header-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.sidebar-body {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.sidebar-tree-scroll {
  flex: 1;
  min-height: 0;
}

.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 36px;
  padding: 6px 8px;
  border-radius: 8px;
  gap: 8px;
  box-sizing: border-box;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.tree-node:hover {
  background: #f5f7fa;
}

.tree-node.active {
  background: #ecf5ff;
  color: #409eff;
  box-shadow: inset 0 0 0 1px #bfdbfe;
}

.tree-main {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  gap: 8px;
}

.tree-icon {
  flex: 0 0 auto;
  font-size: 16px;
}

.tree-icon.directory {
  color: #d97706;
}

.tree-icon.file {
  color: #6b7280;
}

.tree-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 1.4;
  color: #374151;
}

.tree-node.directory .tree-label {
  font-weight: 600;
  color: #1f2937;
}

.tree-node.file .tree-label {
  font-weight: 500;
}

.tree-node.active .tree-label {
  color: inherit;
}

.tree-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  opacity: 0;
  pointer-events: none;
  transform: translateX(4px);
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.tree-node:hover .tree-actions,
.tree-node:focus-within .tree-actions {
  opacity: 1;
  pointer-events: auto;
  transform: translateX(0);
}

.tree-action-trigger {
  height: 24px;
  padding: 0 6px;
  border-radius: 6px;
  font-size: 12px;
  color: #64748b;
}

.tree-action-trigger:hover {
  background: #eef2ff;
  color: #2563eb;
}

.sidebar-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.sidebar-card :deep(.el-card__header) {
  padding: 12px 16px;
}

.sidebar-card :deep(.el-scrollbar) {
  height: 100%;
}

.sidebar-card :deep(.el-scrollbar__wrap) {
  scrollbar-width: thin;
  scrollbar-color: #c0c4cc transparent;
}

.sidebar-card :deep(.el-scrollbar__wrap)::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.sidebar-card :deep(.el-scrollbar__wrap)::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 999px;
}

.sidebar-card :deep(.el-scrollbar__wrap)::-webkit-scrollbar-track {
  background: transparent;
}
</style>
