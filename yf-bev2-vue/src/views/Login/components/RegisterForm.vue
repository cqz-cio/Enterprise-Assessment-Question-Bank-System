<template>
  <el-form
    ref="formRef"
    :model="form"
    :rules="rules"
    class="dark:(border-1 border-[var(--el-border-color)] border-solid); w-[100%]"
    hide-required-asterisk
    label-position="left"
    label-width="90px"
    size="large"
  >
    <el-form-item>
      <h2 class="text-2xl font-bold text-center w-[100%]">{{ t('login.registerTitle') }}</h2>
    </el-form-item>

    <el-form-item :label="t('login.username')" prop="userName">
      <el-input
        v-model="form.userName"
        :placeholder="t('login.usernamePlaceholder')"
        clearable
        type="text"
      />
    </el-form-item>

    <el-form-item :label="t('login.realName')" prop="realName">
      <el-input
        v-model="form.realName"
        :placeholder="t('login.realNamePlaceholder')"
        clearable
        type="text"
      />
    </el-form-item>

    <el-form-item label="员工工号" prop="employeeNo">
      <el-input v-model="form.employeeNo" clearable placeholder="请输入员工工号" />
    </el-form-item>

    <el-form-item label="所属部门" prop="deptCode">
      <el-tree-select
        v-model="form.deptCode"
        :data="departments"
        :props="{ label: 'deptName', value: 'deptCode' }"
        check-strictly
        class="!w-full"
        placeholder="请选择部门"
      />
    </el-form-item>

    <el-form-item label="手机号码" prop="mobile">
      <el-input v-model="form.mobile" clearable placeholder="选填" />
    </el-form-item>

    <el-form-item label="邮箱" prop="email">
      <el-input v-model="form.email" clearable placeholder="选填" />
    </el-form-item>

    <el-form-item :label="t('login.password')" prop="password">
      <input-password
        v-model="form.password"
        :placeholder="t('login.passwordPlaceholder')"
        :strength="true"
        class="!w-full"
      />
    </el-form-item>

    <el-form-item :label="t('login.checkPassword')" prop="checkPassword">
      <input-password
        v-model="form.checkPassword"
        :placeholder="t('login.passwordPlaceholder')"
        :strength="true"
        class="!w-full"
      />
    </el-form-item>

    <el-form-item :label="t('login.code')" prop="captchaValue">
      <input-captcha v-model="form" :placeholder="t('login.codePlaceholder')" class="!w-full" />
    </el-form-item>

    <el-form-item>
      <div class="w-[100%]">
        <el-button :loading="loading" class="w-[100%]" type="primary" @click="register(formRef)">
          {{ t('login.register') }}
        </el-button>
      </div>
      <div class="w-[100%] mt-15px">
        <el-button class="w-[100%]" @click="toLogin"> {{ t('login.hasUser') }}</el-button>
      </div>
    </el-form-item>
  </el-form>
</template>

<script lang="ts" setup>
import { onMounted, ref, unref } from 'vue'
import { useI18n } from '@/hooks/web/useI18n'
import { useValidator } from '@/hooks/web/useValidator'
import { UserLoginType } from '@/api/login/types'
import { ElMessage, FormInstance } from 'element-plus'
import InputPassword from '@/components/InputPassword/src/InputPassword.vue'
import InputCaptcha from '@/components/InputCaptcha/src/InputCaptcha.vue'
import { useUserStoreWithOut } from '@/store/modules/user'
import { treeSelectApi } from '@/api/sys/depart'

const { required } = useValidator()

const emit = defineEmits(['to-login'])

const userStore = useUserStoreWithOut()

const { t } = useI18n()
const form = ref<UserLoginType>({
  userName: '',
  realName: '',
  employeeNo: '',
  deptCode: '',
  mobile: '',
  email: '',
  password: '',
  checkPassword: '',
  captchaKey: '',
  captchaValue: ''
})
const formRef = ref<FormInstance>()

// 密码校验
const checkPass = (_rule: any, value: any, callback: any) => {
  if (value === '') {
    callback(new Error('请输入确认密码！'))
  } else if (value !== form.value.password) {
    callback(new Error('两次密码输入不一致！'))
  } else {
    callback()
  }
}

const rules = {
  userName: [required()],
  realName: [required()],
  employeeNo: [required()],
  deptCode: [required()],
  password: [required()],
  checkPassword: [{ validator: checkPass, trigger: 'blur' }],
  captchaValue: [required()]
}
const loading = ref(false)
const departments = ref<any[]>([])

// 登录
const register = async (formEl: FormInstance | undefined) => {
  if (!formEl) return
  await formEl?.validate(async (isValid) => {
    if (isValid) {
      loading.value = true
      const formData = unref(form)
      // 注册并登录
      userStore
        .register(formData)
        .then(() => {
          ElMessage.success('注册申请已提交，请等待管理员或 HR 审核')
          emit('to-login')
          loading.value = false
        })
        .catch(() => {
          loading.value = false
        })
    }
  })
}

// 去登录页面
const toLogin = () => {
  emit('to-login')
}

onMounted(async () => {
  const res = await treeSelectApi()
  departments.value = res.data || []
})
</script>

<style lang="less" scoped>
:deep(.el-form-item__label) {
  display: inline-block;
  width: 90px;
  margin-right: 10px;
  text-align-last: justify;
}
</style>
