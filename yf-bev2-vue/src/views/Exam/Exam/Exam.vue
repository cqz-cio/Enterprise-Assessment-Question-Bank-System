<template>
  <ContentWrap>
    <DataTable
      ref="table"
      :options="options"
      :query="query"
      @on-add="handleAdd"
      @on-edit="handleEdit"
    >
      <template #search>
        <el-input v-model="query.params.title" class="filter-item" placeholder="搜索考试" />
        <DepartmentSelect v-model="query.params.departId" class="filter-item" />
        <el-select v-model="query.params.sceneType" class="filter-item" clearable placeholder="考核场景"><el-option label="面试" value="INTERVIEW" /><el-option label="转正" value="REGULARIZATION" /><el-option label="晋升" value="PROMOTION" /></el-select>
        <el-date-picker
          v-model="dateRange"
          class="filter-item"
          end-placeholder="截止"
          range-separator="到"
          start-placeholder="考试时间"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </template>

      <template #columns>
        <el-table-column type="selection" width="50px" />
        <el-table-column label="考试名称" prop="title" />
        <el-table-column align="center" label="部门" prop="departId_dictText" />
        <el-table-column align="center" label="岗位" prop="positionId_dictText" />
        <el-table-column align="center" label="场景" prop="sceneType" width="90"><template #default="{row}">{{({INTERVIEW:'面试',REGULARIZATION:'转正',PROMOTION:'晋升'}[row.sceneType]||row.sceneType)}}</template></el-table-column>
        <el-table-column align="center" label="目标职级" prop="targetGradeId_dictText"><template #default="{row}">{{row.targetGradeId_dictText||'-'}}</template></el-table-column>
        <el-table-column align="center" label="模板状态" width="90"><template #default="{row}"><el-tag :type="row.templateStatus===1?'success':'info'">{{row.templateStatus===1?'启用':'停用'}}</el-tag></template></el-table-column>
        <el-table-column align="center" label="开始时间" prop="startTime" show-overflow-tooltip />
        <el-table-column align="center" label="结束时间" prop="endTime" show-overflow-tooltip />
        <el-table-column align="center" label="创建人" prop="createBy_dictText" />
        <el-table-column align="center" label="创建时间" prop="createTime" show-overflow-tooltip />
        <el-table-column :align="'center'" label="操作" width="180px">
          <template #default="{ row }">
            <el-button icon="Document" type="primary" @click="toRecord(row.id)">考试记录</el-button>
          </template>
        </el-table-column>
      </template>
    </DataTable>
  </ContentWrap>
</template>

<script lang="ts" setup>
import { ContentWrap } from '@/components/ContentWrap'
import { DataTable } from '@/components/DataTable'
import { computed, onActivated, ref } from 'vue'
import type { OptionsType, TableQueryType } from '@/components/DataTable/src/types'
import { useRouter } from 'vue-router'
import DepartmentSelect from '@/views/Exam/components/DepartmentSelect.vue'

const { push } = useRouter()

// 表格查询参数
let query = ref<TableQueryType>({
  current: 1,
  size: 10,
  params: {
    title: '',
    startTime: null,
    endTime: null,
    departId: '',
    sceneType: ''
  }
})

// 表格默认参数
let options = ref<OptionsType>({
  listUrl: '/api/exam/exam/exam/paging',
  delUrl: '/api/exam/exam/exam/delete',
  add: {
    enable: true,
    permission: ['exam:exam:add']
  },
  edit: {
    enable: true,
    permission: ['exam:exam:edit']
  },
  del: {
    enable: true,
    permission: ['exam:exam:delete']
  }
})

const table = ref()

const handleAdd = () => {
  push({ name: 'ExamAdd' })
}
const handleEdit = (row: any) => {
  push({ name: 'ExamEdit', query: { id: row.id } })
}

const toRecord = (id: string) => {
  push({ name: 'ExamRecord', query: { id: id } })
}

const dateRange = computed({
  get: () => [query.value.params.startTimeL, query.value.params.startTimeR],
  set: (val) => {
    query.value.params.startTimeL = val?.[0] || ''
    query.value.params.startTimeR = val?.[1] || ''
  }
})

onActivated(() => {
  // 刷新表格
  table.value.reload()
})
</script>
