<script setup>
import { onMounted, ref, watch, onBeforeUnmount, shallowRef } from 'vue'
import { EditorView, basicSetup } from 'codemirror'
import { sql, MySQL, PostgreSQL, StandardSQL } from '@codemirror/lang-sql'
import { EditorState } from '@codemirror/state'
import { keymap } from '@codemirror/view'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '输入SQL语句...' },
  readonly: { type: Boolean, default: false },
  tables: { type: Array, default: () => [] },
  fields: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])
const editorEl = ref(null)
const editorView = shallowRef(null)

function buildCompletions() {
  const completions = []
  if (props.tables && props.tables.length) {
    props.tables.forEach(t => {
      completions.push({ label: t, type: 'table' })
    })
  }
  if (props.fields && props.fields.length) {
    props.fields.forEach(f => {
      completions.push({ label: f, type: 'field' })
    })
  }
  return completions
}

function createEditor() {
  if (!editorEl.value) return

  const completions = buildCompletions()

  const extensions = [
    basicSetup,
    sql({
      dialect: StandardSQL,
      upperCaseKeywords: true
    }),
    EditorView.updateListener.of(update => {
      if (update.docChanged) {
        const value = update.state.doc.toString()
        emit('update:modelValue', value)
      }
    }),
    EditorView.lineWrapping,
    EditorState.readOnly.of(props.readonly),
    keymap.of([])
  ]

  const state = EditorState.create({
    doc: props.modelValue || '',
    extensions
  })

  const view = new EditorView({
    state,
    parent: editorEl.value
  })

  editorView.value = view
}

watch(() => props.modelValue, (newVal) => {
  const view = editorView.value
  if (view && newVal !== view.state.doc.toString()) {
    view.dispatch({
      changes: { from: 0, to: view.state.doc.length, insert: newVal || '' }
    })
  }
})

watch(() => props.readonly, (ro) => {
  const view = editorView.value
  if (view) {
    view.dispatch({
      effects: EditorView.editable.of(!ro)
    })
  }
})

onMounted(() => {
  createEditor()
})

onBeforeUnmount(() => {
  if (editorView.value) {
    editorView.value.destroy()
  }
})
</script>

<template>
  <div class="sql-editor-wrapper">
    <div ref="editorEl" class="sql-editor"></div>
  </div>
</template>

<style scoped>
.sql-editor-wrapper {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  overflow: hidden;
}
.sql-editor {
  font-size: 14px;
}
.sql-editor :deep(.cm-editor) {
  height: 300px;
}
.sql-editor :deep(.cm-editor .cm-scroller) {
  overflow: auto;
}
.sql-editor :deep(.cm-editor.cm-focused) {
  outline: none;
}
.sql-editor :deep(.cm-content) {
  padding: 8px;
  font-family: 'Courier New', Consolas, monospace;
}
.sql-editor :deep(.cm-line) {
  line-height: 1.6;
}
</style>
