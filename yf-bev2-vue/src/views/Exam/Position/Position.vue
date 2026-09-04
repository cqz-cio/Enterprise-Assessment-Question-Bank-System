<template>
  <ContentWrap>
    <DataTable ref="table" :options="options" :query="query" @on-add="handleAdd" @on-edit="handleEdit">
      <template #search>
        <DepartmentSelect v-model="query.params.departmentId" class="filter-item" />
        <el-input v-model="query.params.code" class="filter-item" placeholder="岗位编码" />
        <el-input v-model="query.params.name" class="filter-item" placeholder="岗位名称" />
        <el-select v-model="query.params.status" class="filter-item" clearable placeholder="状态">
          <el-option label="启用" :value="1" /><el-option label="停用" :value="0" />
        </el-select>
      </template>
      <template #columns>
        <el-table-column type="selection" width="50" />
        <el-table-column label="所属部门" min-width="180">
          <template #default="{ row }">{{ row.departmentNames?.join('、') || '-' }}</template>
        </el-table-column>
        <el-table-column label="岗位编码" prop="code" width="150" />
        <el-table-column label="岗位名称" prop="name" min-width="150" />
        <el-table-column label="岗位职级" min-width="220">
          <template #default="{ row }">
            <el-tag v-for="grade in row.grades" :key="grade.id" class="!mr-6px !mb-4px" :type="grade.status === 1 ? '' : 'info'">
              {{ grade.name }}<span v-if="grade.levelNo !== null && grade.levelNo !== undefined">（{{ grade.levelNo }}级）</span>
            </el-tag>
            <span v-if="!row.grades?.length">暂未设置</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="180" show-overflow-tooltip />
      </template>
    </DataTable>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑岗位结构' : '新增岗位结构'" width="760px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属部门" prop="departmentIds">
          <DepartmentSelect v-model="form.departmentIds" multiple />
          <div class="form-tip">同一个岗位可以同时关联多个部门。</div>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="岗位编码" prop="code"><el-input v-model="form.code" maxlength="64" placeholder="如 SALES_DEV" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="岗位名称" prop="name"><el-input v-model="form.name" maxlength="128" placeholder="如 开发业务员" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态" prop="status"><el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item></el-col>
        </el-row>

        <div class="grade-header"><span>岗位职级（晋升场景使用，可暂不设置）</span><el-button type="primary" link @click="addGrade">+ 添加职级</el-button></div>
        <el-table :data="form.grades" border empty-text="尚未设置职级，可后续补充">
          <el-table-column label="职级编码" min-width="140"><template #default="{ row }"><el-input v-model="row.code" placeholder="如 P1" /></template></el-table-column>
          <el-table-column label="职级名称" min-width="150"><template #default="{ row }"><el-input v-model="row.name" placeholder="如 初级业务员" /></template></el-table-column>
          <el-table-column label="级别" width="110"><template #default="{ row }"><el-input-number v-model="row.levelNo" :min="0" controls-position="right" /></template></el-table-column>
          <el-table-column label="状态" width="95"><template #default="{ row }"><el-switch v-model="row.status" :active-value="1" :inactive-value="0" /></template></el-table-column>
          <el-table-column label="操作" width="70"><template #default="{ $index }"><el-button link type="danger" @click="form.grades.splice($index, 1)">删除</el-button></template></el-table-column>
        </el-table>
        <el-form-item label="备注" class="!mt-18px"><el-input v-model="form.remark" type="textarea" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
  </ContentWrap>
</template>

<script lang="ts" setup>
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { ContentWrap } from '@/components/ContentWrap'
import { DataTable } from '@/components/DataTable'
import type { OptionsType, TableQueryType } from '@/components/DataTable/src/types'
import { detailApi, saveApi } from '@/api/modules/exam/position'
import DepartmentSelect from '@/views/Exam/components/DepartmentSelect.vue'

const table = ref(); const formRef = ref<FormInstance>(); const dialogVisible = ref(false); const saving = ref(false)
const query = ref<TableQueryType>({ current: 1, size: 10, params: { departmentId: '', code: '', name: '', status: null } })
const options = ref<OptionsType>({ listUrl: '/api/exam/position/paging', delUrl: '/api/exam/position/delete', add: { enable: true, permission: ['exam:position:add'] }, edit: { enable: true, permission: ['exam:position:edit'] }, del: { enable: true, permission: ['exam:position:delete'] } })
const fresh = () => ({ id: '', code: '', name: '', status: 1, sort: 0, remark: '', departmentIds: [] as string[], grades: [] as any[] })
const form = ref(fresh())
const rules = reactive<FormRules>({
  departmentIds: [{ type: 'array', required: true, min: 1, message: '请至少选择一个部门', trigger: 'change' }],
  code: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }, { pattern: /^[A-Z][A-Z0-9_]{1,63}$/, message: '使用大写字母、数字或下划线，且以字母开头', trigger: 'blur' }],
  name: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }]
})

function handleAdd() { form.value = fresh(); dialogVisible.value = true }
async function handleEdit(row: any) { form.value = (await detailApi({ id: row.id })).data; dialogVisible.value = true }
function addGrade() { form.value.grades.push({ id: '', code: '', name: '', levelNo: form.value.grades.length + 1, sort: form.value.grades.length, status: 1 }) }
async function submit() {
  if (!await formRef.value?.validate()) return
  if (form.value.grades.some((item: any) => !item.code?.trim() || !item.name?.trim())) { ElMessage.warning('请补全所有职级的编码和名称'); return }
  saving.value = true
  try { await saveApi(form.value); ElMessage.success('岗位结构保存成功'); dialogVisible.value = false; table.value?.reload() } finally { saving.value = false }
}
</script>

<style scoped>
.form-tip { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 4px }
.grade-header { display: flex; align-items: center; justify-content: space-between; margin: 8px 0 12px 100px; font-weight: 600 }
</style>
