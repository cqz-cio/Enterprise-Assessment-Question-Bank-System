<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    align-center
    title="试题管理"
    width="80%"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="试题题型" prop="quType">
            <DictListSelect v-model="form.quType" :disabled="!!form.id" dic-code="qu_type" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="难度等级" prop="difficultyLevel">
            <DictListSelect v-model="form.difficultyLevel" dic-code="qu_difficulty_level" />
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="所属题库" prop="repoId">
            <RepoSelect v-model="form.repoId" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="启用状态" prop="status">
            <el-switch
              v-model="form.status"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="停用"
            />
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="题目编号" prop="externalCode">
            <el-input
              v-model="form.externalCode"
              maxlength="64"
              placeholder="选填，用于题库维护和导入追溯"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="题目标签" prop="tags">
            <el-input v-model="form.tags" maxlength="500" placeholder="多个标签使用英文逗号分隔" />
          </el-form-item>
        </el-col>

        <el-col :span="24">
          <el-form-item label="试题题干" prop="content">
            <Editor ref="editorRef" v-model="form.content" height="100px" />
          </el-form-item>
        </el-col>

        <el-col v-if="form.quType !== 'short'" :span="24">
          <el-form-item label="试题解析" prop="analysis">
            <Editor ref="analysisRef" v-model="form.analysis" height="100px" />
          </el-form-item>
        </el-col>

        <template v-if="form.quType === 'short'">
          <el-col :span="24">
            <el-form-item label="参考答案" prop="referenceAnswer">
              <Editor v-model="form.referenceAnswer" height="120px" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="评分要点" prop="gradingCriteria">
              <Editor v-model="form.gradingCriteria" height="120px" />
            </el-form-item>
          </el-col>
        </template>
      </el-row>

      <el-col v-if="form.quType && form.quType !== 'short'" :span="24">
        <el-divider />

        <div class="!pb-10px">
          <el-button type="primary" @click="addAnswer">添加选项</el-button>
        </div>

        <el-table :data="form.answerList" border class="!w-full">
          <el-table-column label="序号" width="180">
            <template #default="{ $index }">第{{ $index + 1 }}项</template>
          </el-table-column>
          <el-table-column align="center" label="是否答案" width="180">
            <template #default="{ row, $index }">
              <el-checkbox
                v-model="row.isRight"
                label="答案"
                @change="checkChange($event, $index)"
              />
            </template>
          </el-table-column>
          <el-table-column label="答案内容">
            <template #default="{ row }">
              <el-input v-model="row.content" placeholder="输入选项内容" />
            </template>
          </el-table-column>
          <el-table-column align="center" label="操作项" width="180px">
            <template #default="{ $index }">
              <el-button :icon="Delete" circle type="danger" @click="removeAnswer($index)" />
            </template>
          </el-table-column>
        </el-table>
      </el-col>
    </el-form>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button :loading="loading" type="primary" @click="handleSave(formRef)">保存</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { DictListSelect } from '@/components/DictListSelect'
import { QuDataType } from '@/views/Exam/Repo/types'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import RepoSelect from '@/views/Exam/Repo/components/RepoSelect.vue'
import { detailApi, saveApi } from '@/api/modules/exam/qu'
import Editor from '@/components/Editor/src/Editor.vue'
import { Delete } from '@element-plus/icons-vue'

const dialogVisible = ref(false)

// 组件参数
const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  repoId: {
    type: String,
    default: ''
  },
  quId: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:visible', 'saved'])
const loading = ref(false)

const form = ref<QuDataType>({
  answerList: [],
  status: 1
})
const formRef = ref<FormInstance>()
const rules = reactive<FormRules>({
  quType: [
    {
      required: true,
      message: '题型不能为空',
      trigger: 'blur'
    }
  ],
  repoId: [
    {
      required: true,
      message: '题库必须选择！',
      trigger: 'blur'
    }
  ],
  content: [
    {
      required: true,
      message: '题干必须填写！',
      trigger: 'blur'
    }
  ],
  difficultyLevel: [
    {
      required: true,
      message: '难度等级不能为空',
      trigger: 'change'
    }
  ]
})

// 监听数据变化
watch(
  () => props.visible,
  (val) => {
    dialogVisible.value = val
  }
)

// 监听数据变化
watch(
  () => props.quId,
  (val) => {
    if (val) {
      loadData(val)
    } else {
      form.value = { answerList: [], status: 1, repoId: props.repoId }
    }
  }
)

watch(
  () => props.repoId,
  (val) => {
    form.value.repoId = val
  }
)

watch(
  () => form.value.quType,
  (val) => {
    if (val) {
      autoFill(val)
    }
  }
)

const handleClose = () => {
  dialogVisible.value = false
  emit('update:visible', false)
}

// 保存题目
const handleSave = async (formEl: FormInstance | undefined) => {
  if (!formEl) return
  await formEl?.validate(async (isValid) => {
    if (isValid && checkItems()) {
      if (form.value.quType === 'short') {
        form.value.answerList = []
        form.value.analysis = ''
      }
      loading.value = true
      saveApi(form.value)
        .then(() => {
          ElMessage({
            message: '题目添加成功！',
            type: 'success'
          })

          dialogVisible.value = false
          emit('update:visible', false)
          emit('saved')
          loading.value = false

          // 重置表单，保留题库
          formEl?.resetFields()
          form.value.repoId = props.repoId
          form.value.status = 1
        })
        .catch(() => {
          loading.value = false
        })
    }
  })
}

// 加载详情
const loadData = (repoId: string) => {
  detailApi({ id: repoId }).then(({ data }) => {
    form.value = data
    form.value.status = data.status ?? 1
  })
}

// 添加一个选项
const addAnswer = () => {
  if (!form.value.answerList) {
    form.value.answerList = []
  }

  form.value.answerList.push({ content: '', isRight: false })
}

// 移除选项
const removeAnswer = (i: number) => {
  form.value.answerList?.splice(i, 1)
}

// 自动填充选项
const autoFill = (quType: string) => {
  if (form.value.id) {
    return
  }
  // 先清理
  form.value.answerList = []

  if (quType === 'radio' || quType === 'multi') {
    for (let i = 0; i < 3; i++) {
      form.value.answerList?.push({ content: '', isRight: false })
    }
  }

  if (quType === 'judge') {
    form.value.answerList?.push({ content: '正确', isRight: false })
    form.value.answerList?.push({ content: '错误', isRight: false })
  }
}

const checkChange = (val: boolean, index: number) => {
  //
  console.log(val + '::' + index)

  if (!form.value.answerList || form.value.answerList.length === 0) {
    return
  }

  if (val && (form.value.quType === 'radio' || form.value.quType === 'judge')) {
    for (let i = 0; i < form.value.answerList.length; i++) {
      if (index !== i) {
        form.value.answerList[i].isRight = false
      }
    }
  }
}

const checkItems = () => {
  if (form.value.quType === 'short') {
    return true
  }
  let total = 0
  let checked = 0

  if (form.value.answerList && form.value.answerList.length > 0) {
    for (let i = 0; i < form.value.answerList.length; i++) {
      const item = form.value.answerList[i]
      if (item.isRight) {
        checked += 1
      }
      total += 1
    }
  }

  if (total === 0) {
    ElMessage({
      message: '选项列表不能为空！',
      type: 'error'
    })
    return false
  }

  if (checked === 0) {
    ElMessage({
      message: '请至少选择一个选项作为正确答案！',
      type: 'error'
    })
    return false
  }

  return true
}

// 加载第一页数据
onMounted(() => {
  // 查询详情
  if (props.quId) {
    loadData(props.quId)
  }
})
</script>
