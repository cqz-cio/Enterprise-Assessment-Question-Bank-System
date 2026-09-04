<template>
  <el-tree-select
    :model-value="modelValue"
    :data="departments"
    :props="{ label: 'deptName', value: 'id', children: 'children', disabled: 'disabled' }"
    :multiple="multiple"
    :show-checkbox="multiple"
    check-strictly
    clearable
    filterable
    collapse-tags
    collapse-tags-tooltip
    placeholder="请选择部门"
    class="!w-full"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { treeSelectApi } from '@/api/sys/depart'

defineProps<{ modelValue?: string | string[]; multiple?: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: string | string[]): void }>()
const departments = ref<any[]>([])

const markSelectable = (nodes: any[]): any[] => nodes.map((node) => ({
  ...node,
  disabled: node.deptType === 1 || node.parentId === '0',
  children: markSelectable(node.children || [])
}))

onMounted(async () => {
  departments.value = markSelectable((await treeSelectApi()).data || [])
})
</script>
