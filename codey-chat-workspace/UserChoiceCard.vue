<script setup>
// 选择卡片只负责界面和交互事件，不关心外层消息流细节。
const props = defineProps({
  title: {
    type: String,
    default: '请选择',
  },
  description: {
    type: String,
    default: '',
  },
  options: {
    type: Array,
    default: () => [],
  },
  modelValue: {
    type: String,
    default: '',
  },
  disabled: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:modelValue', 'select'])

function handleNoteInput(event) {
  emit('update:modelValue', event?.target?.value || '')
}

function handleSelect(optionLabel) {
  if (props.disabled || !optionLabel) {
    return
  }
  emit('select', optionLabel)
}
</script>

<template>
  <div class="ai-user-choice-card">
    <div class="ai-card-header ai-user-choice-header">
      <svg viewBox="0 0 24 24" width="1em" height="1em" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="10" />
        <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
        <line x1="12" y1="17" x2="12.01" y2="17" />
      </svg>
      <span>{{ title }}</span>
    </div>

    <div v-if="description" class="ai-user-choice-description">
      {{ description }}
    </div>

    <div class="ai-user-choice-options">
      <button
        v-for="opt in options"
        :key="opt.key || opt.label"
        class="ai-user-choice-btn"
        :disabled="disabled"
        @click="handleSelect(opt.label)"
      >
        <span class="ai-user-choice-key">{{ opt.key }}</span>
        <span class="ai-user-choice-label">{{ opt.label }}</span>
        <span v-if="opt.description" class="ai-user-choice-desc">{{ opt.description }}</span>
      </button>
    </div>

    <div class="ai-user-choice-note">
      <textarea
        :value="modelValue"
        class="ai-user-choice-note-input"
        :disabled="disabled"
        rows="2"
        placeholder="补充说明（可选）"
        @input="handleNoteInput"
      />
    </div>
  </div>
</template>

<style scoped>
.ai-user-choice-card {
  background: #ffffff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 8px;
}

.ai-user-choice-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #1d4ed8;
  background: #eff6ff;
  border-bottom: 1px solid #bfdbfe;
}

.ai-user-choice-description {
  padding: 10px 12px;
  color: #475569;
  font-size: 13px;
  line-height: 1.65;
  border-bottom: 1px solid #e2e8f0;
}

.ai-user-choice-options {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.ai-user-choice-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 14px;
  background: none;
  border: none;
  border-bottom: 1px solid #e2e8f0;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: background 0.15s ease;
}

.ai-user-choice-btn:last-child {
  border-bottom: none;
}

.ai-user-choice-btn:hover:not(:disabled) {
  background: #eff6ff;
}

.ai-user-choice-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ai-user-choice-key {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.ai-user-choice-btn:hover:not(:disabled) .ai-user-choice-key {
  background: #1d4ed8;
  color: #ffffff;
}

.ai-user-choice-label {
  color: #1e293b;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
}

.ai-user-choice-desc {
  display: block;
  width: 100%;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 400;
  margin-top: 1px;
}

.ai-user-choice-note {
  padding: 10px 12px;
  border-top: 1px solid #e2e8f0;
}

.ai-user-choice-note-input {
  width: 100%;
  padding: 6px 10px;
  font-size: 13px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  outline: none;
  color: #334155;
  background: #f8fafc;
  font-family: inherit;
  box-sizing: border-box;
  transition: border-color 0.2s;
  resize: vertical;
  min-height: 56px;
  line-height: 1.5;
}

.ai-user-choice-note-input:focus {
  border-color: #93c5fd;
  background: #ffffff;
}

.ai-user-choice-note-input::placeholder {
  color: #94a3b8;
}

.ai-user-choice-note-input:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
