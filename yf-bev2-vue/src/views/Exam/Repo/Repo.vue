<template>
  <ContentWrap>
    <DataTable ref="table" :options="options" :query="query" @on-add="handleAdd" @on-edit="handleEdit">
      <template #search>
        <el-input v-model="query.params.title" class="filter-item" placeholder="搜索题库" />
        <DepartmentSelect v-model="query.params.departId" class="filter-item" @update:model-value="query.params.positionId = ''" />
        <el-select v-model="query.params.positionId" class="filter-item" clearable placeholder="岗位"><el-option v-for="p in queryPositions" :key="p.id" :label="p.name" :value="p.id" /></el-select>
        <el-select v-model="query.params.sceneType" class="filter-item" clearable placeholder="场景"><el-option v-for="s in scenes" :key="s.value" :label="s.label" :value="s.value" /></el-select>
      </template>
      <template #columns>
        <el-table-column type="selection" width="50" />
        <el-table-column label="题库名称" prop="title" min-width="180" />
        <el-table-column label="题库分类" prop="catId_dictText" width="130" />
        <el-table-column label="部门" prop="departId_dictText" width="140" />
        <el-table-column label="岗位" prop="positionId_dictText" width="140" />
        <el-table-column label="场景" width="90"><template #default="{row}">{{ sceneLabel(row.sceneType) }}</template></el-table-column>
        <el-table-column label="目标职级" width="130"><template #default="{row}">{{ row.targetGradeId_dictText || '-' }}</template></el-table-column>
        <el-table-column label="状态" width="80"><template #default="{row}"><el-tag :type="row.status===1?'success':'info'">{{row.status===1?'启用':'停用'}}</el-tag></template></el-table-column>
        <el-table-column align="center" label="题目数量" prop="quCount" width="95" />
        <el-table-column align="center" label="操作" width="150"><template #default="{ row }"><el-button icon="Setting" type="primary" @click="toQuList(row.id)">试题管理</el-button></template></el-table-column>
      </template>
    </DataTable>

    <el-dialog v-model="dialogVisible" title="题库管理" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="题库名称" prop="title"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="题库分类" prop="catId"><el-select v-model="form.catId" class="!w-full"><el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" /></el-select></el-form-item>
        <el-form-item label="部门" prop="departId"><DepartmentSelect v-model="form.departId" @update:model-value="onFormDepartmentChange" /></el-form-item>
        <el-form-item label="岗位" prop="positionId"><el-select v-model="form.positionId" class="!w-full" placeholder="请先选择部门"><el-option v-for="p in formPositions" :key="p.id" :label="p.name" :value="p.id" /></el-select></el-form-item>
        <el-form-item label="应用场景" prop="sceneType"><el-select v-model="form.sceneType" class="!w-full"><el-option v-for="s in scenes" :key="s.value" :label="s.label" :value="s.value" /></el-select></el-form-item>
        <el-form-item v-if="form.sceneType === 'PROMOTION'" label="目标职级"><el-select v-model="form.targetGradeId" class="!w-full" clearable placeholder="可暂不设置"><el-option v-for="g in currentGrades" :key="g.id" :label="g.name" :value="g.id" /></el-select></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="handleSave">保存</el-button></template>
    </el-dialog>
  </ContentWrap>
</template>

<script lang="ts" setup>
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { ContentWrap } from '@/components/ContentWrap'
import { DataTable } from '@/components/DataTable'
import type { OptionsType, TableQueryType } from '@/components/DataTable/src/types'
import { detailApi, saveApi } from '@/api/modules/exam/repo'
import { listByDepartmentApi } from '@/api/modules/exam/position'
import DepartmentSelect from '@/views/Exam/components/DepartmentSelect.vue'
import type { RepoDataType } from './types'

const { push } = useRouter(); const table = ref(); const dialogVisible = ref(false); const formRef = ref<FormInstance>()
const scenes = [{ value: 'INTERVIEW', label: '面试' }, { value: 'REGULARIZATION', label: '转正' }, { value: 'PROMOTION', label: '晋升' }]
const categories = [{ value: 'RECRUITMENT', label: '入职招聘' }, { value: 'EMPLOYEE_PROMOTION', label: '员工晋升' }]
const sceneLabel = (value:string) => scenes.find(item => item.value === value)?.label || value
const query = ref<TableQueryType>({ current: 1, size: 10, params: { title: '', departId: '', positionId: '', sceneType: '' } })
const options = ref<OptionsType>({ listUrl: '/api/exam/repo/repo/paging', delUrl: '/api/exam/repo/repo/delete', add: { enable: true, permission: ['repo:repo:add'] }, edit: { enable: true, permission: ['repo:repo:edit'] }, del: { enable: true, permission: ['repo:repo:delete'] } })
const fresh = (): RepoDataType => ({ title: '', catId: '', departId: '', positionId: '', sceneType: 'INTERVIEW', targetGradeId: '', status: 1 })
const form = ref<RepoDataType>(fresh()); const queryPositions = ref<any[]>([]); const formPositions = ref<any[]>([])
const currentGrades = computed(() => formPositions.value.find(item => item.id === form.value.positionId)?.grades?.filter((g:any) => g.status === 1) || [])
const rules = reactive<FormRules>({ title: [{ required: true, message: '题库名称不能为空', trigger: 'blur' }], catId: [{ required: true, message: '题库分类不能为空', trigger: 'change' }], departId: [{ required: true, message: '部门不能为空', trigger: 'change' }], positionId: [{ required: true, message: '岗位不能为空', trigger: 'change' }], sceneType: [{ required: true, message: '应用场景不能为空', trigger: 'change' }] })

function handleAdd() { form.value = fresh(); formPositions.value = []; dialogVisible.value = true }
async function handleEdit(row:any) { form.value = (await detailApi({ id: row.id })).data; formPositions.value = form.value.departId ? (await listByDepartmentApi(form.value.departId)).data || [] : []; dialogVisible.value = true }
async function onFormDepartmentChange(value:string|string[]) { form.value.positionId = ''; form.value.targetGradeId = ''; formPositions.value = typeof value === 'string' && value ? (await listByDepartmentApi(value)).data || [] : [] }
async function handleSave() { if (!await formRef.value?.validate()) return; await saveApi(form.value); ElMessage.success('操作成功'); dialogVisible.value = false; table.value?.reload() }
function toQuList(id:string) { push({ name: 'Qu', query: { repoId: id } }) }

watch(() => query.value.params.departId, async value => { queryPositions.value = value ? (await listByDepartmentApi(value)).data || [] : [] })
watch(() => [form.value.positionId, form.value.sceneType], () => { if (form.value.sceneType !== 'PROMOTION') form.value.targetGradeId = '' })
</script>
