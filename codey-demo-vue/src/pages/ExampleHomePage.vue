<script setup>
import { useRouter } from 'vue-router'
import { exampleRegistry } from '../examples/exampleRegistry'

const router = useRouter()

function openExample(example) {
  router.push(example.routePath)
}
</script>

<template>
  <div class="landing-page">
    <el-card shadow="never" class="content-card">
      <template #header>
        <div class="section-header">
          <div>
            <div class="section-eyebrow">Examples</div>
            <div class="section-title">示例导航</div>
          </div>
          <el-tag type="info">{{ exampleRegistry.length }} 个示例</el-tag>
        </div>
      </template>
      <div class="example-grid">
        <button
          v-for="example in exampleRegistry"
          :key="example.id"
          type="button"
          class="example-card"
          @click="openExample(example)"
        >
          <div class="example-category">{{ example.category }}</div>
          <div class="example-title">{{ example.title }}</div>
          <div class="example-summary">{{ example.summary }}</div>
          <div class="example-tags">
            <span v-for="tag in example.tags" :key="tag" class="example-tag">{{ tag }}</span>
          </div>
          <div class="example-enter">进入示例</div>
        </button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.landing-page {
  width: 100%;
  height: 100%;
  padding: 8px;
  box-sizing: border-box;
  overflow: auto;
}

.content-card {
  height: 100%;
  border-radius: 16px;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.section-eyebrow {
  margin-bottom: 4px;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.section-title {
  color: #111827;
  font-size: 20px;
  font-weight: 700;
}

.example-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
}

.example-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.example-card:hover {
  transform: translateY(-2px);
  border-color: #93c5fd;
  box-shadow: 0 16px 28px rgba(37, 99, 235, 0.1);
}

.example-category {
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.example-title {
  color: #111827;
  font-size: 18px;
  font-weight: 700;
}

.example-summary {
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
}

.example-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.example-tag {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
}

.example-enter {
  margin-top: auto;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
}

</style>
