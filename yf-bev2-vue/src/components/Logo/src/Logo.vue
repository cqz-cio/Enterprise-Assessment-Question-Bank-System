<script setup lang="ts">
import { computed } from 'vue'
import { useAppStore } from '@/store/modules/app'
import { useDesign } from '@/hooks/web/useDesign'
import { BRAND_MARK, isBuiltinBrandLogo } from '@/utils/branding'

const { getPrefixCls } = useDesign()
const prefixCls = getPrefixCls('logo')
const appStore = useAppStore()
const layout = computed(() => appStore.getLayout)
const collapse = computed(() => appStore.getCollapse)
const siteInfo = computed(() => appStore.getSiteInfo)

// Only the narrow classic sidebar hides the title, including on first mount.
const show = computed(() => layout.value !== 'classic' || !collapse.value)
const logoSrc = computed(() =>
  isBuiltinBrandLogo(siteInfo.value.backLogo) ? BRAND_MARK : siteInfo.value.backLogo
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
      <img
        :src="logoSrc"
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
