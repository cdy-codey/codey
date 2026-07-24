<script setup>
import { Compartment, EditorState } from '@codemirror/state'
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'
import { indentOnInput, bracketMatching, foldGutter } from '@codemirror/language'
import { EditorView, drawSelection, highlightActiveLine, keymap, lineNumbers, placeholder } from '@codemirror/view'
import { css } from '@codemirror/lang-css'
import { html } from '@codemirror/lang-html'
import { javascript } from '@codemirror/lang-javascript'
import { json } from '@codemirror/lang-json'
import { markdown } from '@codemirror/lang-markdown'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: '',
  },
  language: {
    type: String,
    default: 'plaintext',
  },
  readonly: {
    type: Boolean,
    default: false,
  },
  placeholder: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['update:modelValue'])

const editorRootRef = ref(null)
let editorView = null

// 通过 compartment 动态切换语言和只读状态，避免每次都重建编辑器。
const languageCompartment = new Compartment()
const editableCompartment = new Compartment()
const readOnlyCompartment = new Compartment()

function resolveLanguageExtension(language) {
  switch (language) {
    case 'html':
    case 'vue':
      return html()
    case 'javascript':
      return javascript()
    case 'typescript':
      return javascript({ typescript: true })
    case 'css':
      return css()
    case 'json':
      return json()
    case 'markdown':
      return markdown()
    default:
      return []
  }
}

function createEditorTheme() {
  return EditorView.theme({
    '&.cm-focused': {
      outline: 'none',
    },
    '.cm-content': {
      minHeight: '100%',
      fontFamily: "Consolas, 'Courier New', monospace",
      fontSize: '14px',
      lineHeight: '1.7',
      caretColor: '#409eff',
    },
    '.cm-scroller': {
      overflow: 'auto',
      fontFamily: "Consolas, 'Courier New', monospace",
    },
    '.cm-gutters': {
      backgroundColor: '#f1ebdd',
      color: '#909399',
      borderRight: '1px solid #ebeef5',
    },
    '.cm-activeLine': {
      backgroundColor: '#f5f9ff',
    },
    '.cm-activeLineGutter': {
      backgroundColor: '#eef5ff',
    },
    '.cm-placeholder': {
      color: '#a8abb2',
    },
    '&': {
      height: '100%',
      backgroundColor: '#f6f1e4',
    },
  })
}

function createExtensions() {
  return [
    lineNumbers(),
    history(),
    drawSelection(),
    indentOnInput(),
    bracketMatching(),
    foldGutter(),
    highlightActiveLine(),
    keymap.of([...defaultKeymap, ...historyKeymap]),
    EditorView.lineWrapping,
    languageCompartment.of(resolveLanguageExtension(props.language)),
    editableCompartment.of(EditorView.editable.of(!props.readonly)),
    readOnlyCompartment.of(EditorState.readOnly.of(props.readonly)),
    EditorView.updateListener.of((update) => {
      if (!update.docChanged) {
        return
      }
      emit('update:modelValue', update.state.doc.toString())
    }),
    placeholder(props.placeholder || ''),
    createEditorTheme(),
  ]
}

function createEditor() {
  if (!editorRootRef.value) {
    return
  }
  editorView = new EditorView({
    state: EditorState.create({
      doc: props.modelValue || '',
      extensions: createExtensions(),
    }),
    parent: editorRootRef.value,
  })
}

function updateEditorDocument(value) {
  if (!editorView) {
    return
  }
  const currentValue = editorView.state.doc.toString()
  if (currentValue === (value || '')) {
    return
  }
  editorView.dispatch({
    changes: {
      from: 0,
      to: currentValue.length,
      insert: value || '',
    },
  })
}

function updateEditorLanguage(language) {
  if (!editorView) {
    return
  }
  editorView.dispatch({
    effects: languageCompartment.reconfigure(resolveLanguageExtension(language)),
  })
}

function updateReadonly(readonly) {
  if (!editorView) {
    return
  }
  editorView.dispatch({
    effects: [
      editableCompartment.reconfigure(EditorView.editable.of(!readonly)),
      readOnlyCompartment.reconfigure(EditorState.readOnly.of(readonly)),
    ],
  })
}

function getValue() {
  return editorView ? editorView.state.doc.toString() : (props.modelValue || '')
}

defineExpose({
  getValue,
})

onMounted(() => {
  createEditor()
})

onBeforeUnmount(() => {
  editorView?.destroy()
})

watch(
  () => props.modelValue,
  (value) => {
    updateEditorDocument(value)
  }
)

watch(
  () => props.language,
  (value) => {
    updateEditorLanguage(value)
  }
)

watch(
  () => props.readonly,
  (value) => {
    updateReadonly(value)
  }
)
</script>

<template>
  <div ref="editorRootRef" class="code-editor-root" />
</template>

<style scoped>
.code-editor-root {
  height: 100%;
  min-height: 0;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
  background: #f6f1e4;
}

.code-editor-root :deep(.cm-editor) {
  height: 100%;
}

.code-editor-root :deep(.cm-scroller) {
  scrollbar-width: thin;
  scrollbar-color: #c0c4cc transparent;
}

.code-editor-root :deep(.cm-scroller)::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.code-editor-root :deep(.cm-scroller)::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 999px;
}

.code-editor-root :deep(.cm-scroller)::-webkit-scrollbar-track {
  background: transparent;
}
</style>
