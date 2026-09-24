<script setup lang="ts">
import { ref, watch, computed, onMounted, unref } from 'vue'
import { useAppStore } from '@/store/modules/app'
import { useDesign } from '@/hooks/web/useDesign'
import tripeerLogo from '@/assets/imgs/tripeer-logo-light.png'

const { getPrefixCls } = useDesign()

const prefixCls = getPrefixCls('logo')

const appStore = useAppStore()

const show = ref(true)

const layout = computed(() => appStore.getLayout)

const collapse = computed(() => appStore.getCollapse)

const siteInfo = computed(() => appStore.getSiteInfo)

const isBuiltinLogo = computed(() => {
  const configuredLogo = siteInfo.value.backLogo
  return !configuredLogo || ['/brand-logo.png', '/logo.png'].includes(configuredLogo)
})

onMounted(() => {
  if (unref(collapse)) show.value = false
})

watch(
  () => collapse.value,
  (collapse: boolean) => {
    if (unref(layout) === 'topLeft') {
      show.value = true
      return
    }
    if (!collapse) {
      setTimeout(() => {
        show.value = !collapse
      }, 400)
    } else {
      show.value = !collapse
    }
  }
)

watch(
  () => layout.value,
  (layout) => {
    if (layout === 'top') {
      show.value = true
    } else {
      if (unref(collapse)) {
        show.value = false
      } else {
        show.value = true
      }
    }
  }
)
</script>

<template>
  <div>
    <router-link
      :class="[
        prefixCls,
        layout !== 'classic' ? `${prefixCls}__Top` : '',
        'flex !h-[var(--logo-height)] items-center cursor-pointer relative decoration-none overflow-hidden',
        layout === 'classic' && collapse ? 'justify-center' : 'pl-8px'
      ]"
      :aria-label="siteInfo.siteName || '首页'"
      to="/"
    >
      <!-- Show the approved transparent mark at icon size, without the tiny wordmark. -->
      <svg
        v-if="isBuiltinLogo"
        viewBox="110 68 565 580"
        aria-hidden="true"
        focusable="false"
        class="overflow-hidden shrink-0 w-[calc(var(--logo-height)-20px)] h-[calc(var(--logo-height)-20px)]"
      >
        <image :href="tripeerLogo" width="2154" height="730" />
      </svg>
      <img
        v-else
        :src="siteInfo.backLogo"
        alt=""
        class="object-contain shrink-0 w-[calc(var(--logo-height)-20px)] h-[calc(var(--logo-height)-20px)]"
      />
      <div
        v-if="show"
        :class="[
          'ml-10px text-16px font-700',
          {
            'text-[var(--logo-title-text-color)]': layout === 'classic',
            'text-[var(--top-header-text-color)]': layout === 'topLeft' || layout === 'top'
          }
        ]"
      >
        {{ siteInfo.siteName }}
      </div>
    </router-link>
  </div>
</template>
