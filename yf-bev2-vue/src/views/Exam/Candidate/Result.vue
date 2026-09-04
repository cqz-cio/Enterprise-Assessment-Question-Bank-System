<template>
  <div class="result-page"><div class="result-card">
    <el-result v-if="loaded" :icon="!result.resultAvailable ? 'info' : result.passed ? 'success' : 'warning'" :title="!result.resultAvailable ? '结果处理中' : result.passed ? '测评通过' : '测评未通过'" :sub-title="!result.resultAvailable ? '主观题尚待阅卷，请稍后查看' : '本页面仅展示最终是否通过，详细结果由管理人员查看'">
      <template #extra><el-button type="primary" @click="router.replace('/exam-entry')">返回测评入口</el-button></template>
    </el-result>
  </div></div>
</template>
<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { assignmentResultApi } from '@/api/modules/exam/assignment'
const route=useRoute(); const router=useRouter(); const loaded=ref(false); const result=ref<any>({})
onMounted(async()=>{ const id=String(route.query.assignmentId || ''); if(!id){ router.replace('/exam-entry'); return } result.value=(await assignmentResultApi({id})).data; loaded.value=true })
</script>
<style scoped>.result-page{min-height:100vh;display:grid;place-items:center;background:#f5f7fa;padding:24px}.result-card{width:min(560px,100%);background:#fff;border-radius:18px;box-shadow:0 20px 60px rgba(0,0,0,.08)}</style>
