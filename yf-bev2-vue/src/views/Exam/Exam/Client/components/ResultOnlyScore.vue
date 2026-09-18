<template>
  <ContentWrap>
    <el-result
      :icon="(detail.resultAvailable === false || detail.passed == null) ? 'info' : detail.passed ? 'success' : 'warning'"
      :title="(detail.resultAvailable === false || detail.passed == null) ? '结果处理中' : detail.passed ? '恭喜，考试通过了哟！' : '很遗憾，本次考试未及格！'"
    >
      <template #extra>
        <el-button size="large" type="primary" @click="backExamList">返回我的考核</el-button>
      </template>
    </el-result>
  </ContentWrap>
</template>

<script lang="ts" setup>
import { ContentWrap } from '@/components/ContentWrap'
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { paperDetailApi } from '@/api/modules/exam/paper'

const { push } = useRouter()

const props = withDefaults(defineProps<{ paperId?: string }>(), { paperId: '' })

const detail = ref<any>({})

// 查找详情
const paperDetail = (id: string) => {
  paperDetailApi({ id }).then((res) => {
    detail.value = res.data
  })
}

// 返回考试列表
const backExamList = () => {
  push({ name: 'ClientExamList' })
}

watch(
  () => props.paperId,
  (val) => {
    if (val) {
      paperDetail(val)
    }
  },
  { immediate: true }
)
</script>
