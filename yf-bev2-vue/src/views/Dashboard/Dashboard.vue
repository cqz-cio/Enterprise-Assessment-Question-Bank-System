<script lang="ts" setup>
import { useAppStore } from '@/store/modules/app'
import { computed, ref } from 'vue'
import { ContentWrap } from '@/components/ContentWrap'

const appStore = useAppStore()
const siteInfo = computed(() => appStore.getSiteInfo)
const loading = ref(false)
</script>

<template>
  <ContentWrap v-loading="loading" element-loading-text="加载中...">
    <div> 欢迎使用{{ siteInfo.siteName }}</div>
  </ContentWrap>
</template>
