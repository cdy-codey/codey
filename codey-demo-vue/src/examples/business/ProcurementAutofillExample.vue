<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { AiChatWorkspace } from 'codey-chat-workspace'
import { useAiAssistantHandlers } from '../../composables/useAiAssistantHandlers'
import { createBusinessScenarioApi } from '../../api/businessScenario'
import { createBusinessAiWorkspace } from '../../composables/useBusinessAiWorkspace'

const { aiAssistantHandlers, queryWorkspace, workspaceApi } = useAiAssistantHandlers()
const businessScenarioApi = createBusinessScenarioApi()
const assistantRef = ref(null)
const loading = ref(false)
const aiCollapsed = ref(false)
const aiSummary = ref('')
const scenario = ref(null)
const lastSubmittedContextSignature = ref('')
const PROCUREMENT_FORM_SKILL = ['procurement-form-agent', 'ui-json-render-agent']
const AUTO_FILL_CHAT_MESSAGE = '请帮我自动填写当前表单。'
const BUSINESS_AI_SYSTEM_PROMPT = '1.思考内容不要出现表单字段的英文名称，使用中文替代'

// 表单模式开关：开启后走 formMode 直出 JSON，关闭仍走旧 skill 编辑文件模式，两种模式并存
const formModeEnabled = ref(false)
// 表单模式对应的业务表单名称，后端按该名称精确匹配 ProcurementFormProvider
const PROCUREMENT_FORM_NAME = 'procurement-form'

// 表单字段中文名映射：与 formModel 字段一一对应，作为可见字段的 label 来源（前端为权威）
const PROCUREMENT_FORM_FIELD_LABELS = {
  requireTitle: '需求标题',
  requireNo: '需求编号',
  requireAttribute: '需求属性',
  purchaseCategory: '采购类别',
  emergency: '需求紧急度',
  estimatedStartTime: '预计启动时间',
  subscribeDepartName: '申购部门',
  requireDepartNames: '需求部门',
  operatorName: '经办人',
  applyName: '申请人',
  purchaseContent: '采购内容描述',
  busService: '商务服务要求',
  isSingleSource: '是否单一来源',
  isImportPurchase: '是否进口采购',
  isMajor: '是否三重一大',
  isInformation: '是否信息化',
  isEntrust: '是否委托',
  isSecret: '是否涉密',
  isSmb: '适宜中小企业',
  isBeginningBudget: '是否年初预算',
  requireCatalog: '采购目录性质',
  budgetType: '预算类型',
  fundsSource: '资金性质',
  costSubject: '资金来源',
  fundFlow: '资金方向',
  organizeForm: '初拟组织形式',
  organizeFormExtra: '组织形式性质',
  purchaseWay: '初拟采购方式',
  processWay: '采购执行方式',
  centralizedDepartName: '归口执行部门',
  purchaseDepartName: '采购执行部门',
  budgetName: '预算项目',
  budgetNo: '预算编号',
  purchaseAmount: '申购金额',
  confirmAmount: '审定金额',
  requireBudgetAmount: '预算总金额',
  planBeginTime: '计划开始时间',
  planFinishTime: '计划完成时间',
  businessDocType: '单据类型',
  fromBizName: '关联单据',
  isOverYear: '是否跨年项目',
  isAddition: '是否补录',
  isChangeStructure: '改变主体结构',
  containContent: '项目包含内容',
  budgetDesc: '资金说明',
  budgetRemark: '备注',
  targetList: '采购标的明细',
  businessEntryList: '采购商务条款',
  attachments: '需求附件',
}

// 采购需求基础信息，字段映射自 BizRequire 实体
const formModel = reactive({
  // 基本信息
  requireTitle: '',
  requireNo: '',
  requireAttribute: '',   // goods:货物类 / build:工程建设类 / service:服务类
  purchaseCategory: '',
  emergency: '',
  estimatedStartTime: '',
  // 组织与人员
  subscribeDepartName: '',
  requireDepartNames: '',
  operatorName: '',
  applyName: '',
  // 描述
  purchaseContent: '',
  busService: '',
  // 采购特征(是/否)
  isSingleSource: '',
  isImportPurchase: '',
  isMajor: '',
  isInformation: '',
  isEntrust: '',
  isSecret: '',
  isSmb: '',
  isBeginningBudget: '',
  // 目录与预算
  requireCatalog: '',
  budgetType: '',
  fundsSource: '',
  costSubject: '',
  fundFlow: '',
  // 采购方式
  organizeForm: '',
  organizeFormExtra: '',
  purchaseWay: '',
  processWay: '',
  // 执行部门
  centralizedDepartName: '',
  purchaseDepartName: '',
  // 预算项目
  budgetName: '',
  budgetNo: '',
  // 金额
  purchaseAmount: 0,
  confirmAmount: 0,
  requireBudgetAmount: 0,
  // 时间计划
  planBeginTime: '',
  planFinishTime: '',
  // 其他
  businessDocType: '',
  fromBizName: '',
  isOverYear: '',
  isAddition: '',
  isChangeStructure: '',
  containContent: '',
  budgetDesc: '',
  budgetRemark: '',
  // 采购标的明细列表（对应 BizRequire.targetList / BizRequireTarget）
  targetList: [],
  // 采购商务条款列表（对应 BizRequire.businessEntryList / BizBusinessEntry）
  businessEntryList: [],
  // 需求附件列表
  attachments: [],
})

// 表单界面展示的可见字段：由 formModel 顶层字段派生，label 取字段中文名映射（前端为权威）。
// 对象化结构（{ label, field }）供后端按 field 过滤、用 label 覆盖 Schema 中的字段中文描述。
const PROCUREMENT_FORM_VISIBLE_FIELDS = Object.keys(formModel).map((field) => ({
  label: PROCUREMENT_FORM_FIELD_LABELS[field] || field,
  field,
}))

const uploading = ref(false)
const uploadInputRef = ref(null)

// 下拉选项映射
const optionMaps = reactive({
  requireAttributeOptions: [],
  purchaseCategoryOptions: [],
  emergencyOptions: [],
  requireCatalogOptions: [],
  budgetTypeOptions: [],
  fundsSourceOptions: [],
  fundFlowOptions: [],
  organizeFormOptions: [],
  organizeFormExtraOptions: [],
  purchaseWayOptions: [],
  processWayOptions: [],
  businessDocTypeOptions: [],
  ynOptions: [],
  isSmbOptions: [],
  targetTypeOptions: [],
  purchaseTypeOptions: [],
})

const totalTargetAmount = computed(() =>
  formModel.targetList.reduce((sum, item) => sum + toNumber(item.num) * toNumber(item.unitPrice), 0),
)

const aiContextFileKey = "采购文件申请"

function handleAiCollapseChange(collapsed) {
  aiCollapsed.value = collapsed
}

function toNumber(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

// 格式化文件大小显示
function formatFileSize(bytes) {
  if (bytes == null || !Number.isFinite(bytes)) return '未知'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function mapOptions(list = []) {
  return Array.isArray(list)
    ? list.map((item) => ({
        value: item.value,
        label: item.description || item.value,
        description: item.description || '',
      }))
    : []
}

// 采购标的明细 — 标准化（映射自 BizRequireTarget 实体）
function normalizeTarget(item = {}, index = 0) {
  return {
    rowNo: item.rowNo || index + 1,
    targetName: item.targetName || '',
    targetTypeName: item.targetTypeName || '',
    purchaseTypeName: item.purchaseTypeName || '',
    num: toNumber(item.num) || 1,
    unitPrice: toNumber(item.unitPrice),
    unit: item.unit || '台',
    targetPrice: toNumber(item.targetPrice) || toNumber(item.num) * toNumber(item.unitPrice),
    targetContent: item.targetContent || '',
    storageArea: item.storageArea || '',
    expectPurchaseTime: item.expectPurchaseTime || '',
    referenceListStr: item.referenceListStr || '',
    remark: item.remark || '',
  }
}

// 商务条款 — 标准化（映射自 BizBusinessEntry 实体）
function normalizeBusinessEntry(item = {}, index = 0) {
  return {
    rowNo: item.rowNo || index + 1,
    entriesCategory: item.entriesCategory || '',
    businessItem: item.businessItem || '',
    businessRequirement: item.businessRequirement || '',
    businessRequirementResult: item.businessRequirementResult || '',
    standardBasis: item.standardBasis || '',
  }
}

// context.json 与查询接口返回结构保持一致，前端展示和 AI 上下文共用同一份格式。
function buildContextSnapshot() {
  // 直接以 formModel 作为同步到 AI 工作区的业务对象，结构与后端 BizRequire 保持一致
  return formModel
}

function buildContextSignature(payload) {
  return JSON.stringify(payload || {})
}

function normalizeWorkspaceContextContent(content) {
  if (!content) {
    return null
  }
  if (typeof content === 'string') {
    return JSON.parse(content)
  }
  if (typeof content === 'object') {
    return content
  }
  return null
}

function applyScenarioContext(context) {
  scenario.value = context || null
  // 后端基础信息已平铺在 context 顶层，与 formModel 字段一一对应，直接整体覆盖
  Object.assign(formModel, context || {})
  // 金额字段统一归一化为数字，防止 null/字符串导致后续计算异常
  formModel.purchaseAmount = toNumber(formModel.purchaseAmount)
  formModel.confirmAmount = toNumber(formModel.confirmAmount)
  formModel.requireBudgetAmount = toNumber(formModel.requireBudgetAmount)
  formModel.targetList = Array.isArray(context?.targetList)
    ? context.targetList.map((item, index) => normalizeTarget(item, index))
    : []
  formModel.businessEntryList = Array.isArray(context?.businessEntryList)
    ? context.businessEntryList.map((item, index) => normalizeBusinessEntry(item, index))
    : []
  optionMaps.requireAttributeOptions = mapOptions(context?.requireAttributeOptions)
  optionMaps.purchaseCategoryOptions = mapOptions(context?.purchaseCategoryOptions)
  optionMaps.emergencyOptions = mapOptions(context?.emergencyOptions)
  optionMaps.requireCatalogOptions = mapOptions(context?.requireCatalogOptions)
  optionMaps.budgetTypeOptions = mapOptions(context?.budgetTypeOptions)
  optionMaps.fundsSourceOptions = mapOptions(context?.fundsSourceOptions)
  optionMaps.fundFlowOptions = mapOptions(context?.fundFlowOptions)
  optionMaps.organizeFormOptions = mapOptions(context?.organizeFormOptions)
  optionMaps.organizeFormExtraOptions = mapOptions(context?.organizeFormExtraOptions)
  optionMaps.purchaseWayOptions = mapOptions(context?.purchaseWayOptions)
  optionMaps.processWayOptions = mapOptions(context?.processWayOptions)
  optionMaps.businessDocTypeOptions = mapOptions(context?.businessDocTypeOptions)
  optionMaps.ynOptions = mapOptions(context?.ynOptions)
  optionMaps.isSmbOptions = mapOptions(context?.isSmbOptions)
  optionMaps.targetTypeOptions = mapOptions(context?.targetTypeOptions)
  optionMaps.purchaseTypeOptions = mapOptions(context?.purchaseTypeOptions)
  formModel.attachments = Array.isArray(context?.attachments) ? [...context.attachments] : []
  recalculatePurchaseAmount()
}

async function loadScenarioContext(showMessage = false) {
  loading.value = true
  try {
    const context = await businessScenarioApi.getProcurementContext()
    applyScenarioContext(context)
    if (showMessage) {
      ElMessage.success('演示数据已初始化')
    }
  } catch (error) {
    ElMessage.error(error.message || '读取业务示例失败')
  } finally {
    loading.value = false
  }
}

function recalculatePurchaseAmount() {
  formModel.purchaseAmount = totalTargetAmount.value
}

function addTarget() {
  formModel.targetList.push(
    normalizeTarget(
      { rowNo: formModel.targetList.length + 1 },
      formModel.targetList.length,
    ),
  )
  recalculatePurchaseAmount()
}

function removeTarget(index) {
  formModel.targetList.splice(index, 1)
  formModel.targetList = formModel.targetList.map((item, rowIndex) => ({
    ...item,
    rowNo: rowIndex + 1,
  }))
  recalculatePurchaseAmount()
}

function addBusinessEntry() {
  formModel.businessEntryList.push(
    normalizeBusinessEntry(
      { rowNo: formModel.businessEntryList.length + 1 },
      formModel.businessEntryList.length,
    ),
  )
}

function removeBusinessEntry(index) {
  formModel.businessEntryList.splice(index, 1)
  formModel.businessEntryList = formModel.businessEntryList.map((item, rowIndex) => ({
    ...item,
    rowNo: rowIndex + 1,
  }))
}

// 打开文件选择器并上传附件
function handleUploadAttachment() {
  uploadInputRef.value?.click()
}

// 文件选择变化时触发上传
async function handleFileChange(event) {
  const file = event.target?.files?.[0]
  if (!file) return

  uploading.value = true
  try {
    const result = await businessScenarioApi.uploadAttachment(file)
    formModel.attachments.push(result)
    ElMessage.success(`附件 "${file.name}" 上传成功`)
  } catch (error) {
    ElMessage.error(error.message || '附件上传失败')
  } finally {
    uploading.value = false
    if (uploadInputRef.value) {
      uploadInputRef.value.value = ''
    }
  }
}

// 移除已上传附件
function removeAttachment(index) {
  formModel.attachments.splice(index, 1)
}

function applyAiAutofillPayload(payload = {}) {
  const basicInfo = payload || {}
  const targets = Array.isArray(payload.targetList) ? payload.targetList : []
  const entries = Array.isArray(payload.businessEntryList) ? payload.businessEntryList : []

  const basicFields = [
    'requireTitle', 'requireNo', 'requireAttribute', 'purchaseCategory', 'emergency',
    'estimatedStartTime', 'subscribeDepartName', 'requireDepartNames', 'operatorName', 'applyName',
    'purchaseContent', 'busService', 'isSingleSource', 'isImportPurchase', 'isMajor', 'isInformation',
    'isEntrust', 'isSecret', 'isSmb', 'isBeginningBudget', 'requireCatalog', 'budgetType',
    'fundsSource', 'costSubject', 'fundFlow', 'organizeForm', 'organizeFormExtra', 'purchaseWay',
    'processWay', 'centralizedDepartName', 'purchaseDepartName', 'budgetName', 'budgetNo',
    'planBeginTime', 'planFinishTime', 'businessDocType', 'fromBizName', 'isOverYear', 'isAddition',
    'isChangeStructure', 'containContent', 'budgetDesc', 'budgetRemark',
  ]
  basicFields.forEach((field) => {
    if (basicInfo[field] !== undefined && basicInfo[field] !== '') {
      formModel[field] = basicInfo[field]
    }
  })

  if (toNumber(basicInfo.purchaseAmount) > 0) formModel.purchaseAmount = toNumber(basicInfo.purchaseAmount)
  if (toNumber(basicInfo.confirmAmount) > 0) formModel.confirmAmount = toNumber(basicInfo.confirmAmount)
  if (toNumber(basicInfo.requireBudgetAmount) > 0) formModel.requireBudgetAmount = toNumber(basicInfo.requireBudgetAmount)

  if (targets.length > 0) {
    formModel.targetList = targets.map((item, index) =>
      normalizeTarget({ ...item, rowNo: item.rowNo || index + 1 }, index),
    )
  }

  if (entries.length > 0) {
    formModel.businessEntryList = entries.map((item, index) =>
      normalizeBusinessEntry({ ...item, rowNo: item.rowNo || index + 1 }, index),
    )
  }

  if (Array.isArray(payload.attachments)) {
    formModel.attachments = [...payload.attachments]
  }

  recalculatePurchaseAmount()
}

// 折叠态下从页面头部重新打开 AI 助手
function handleToggleAiAssistant() {
  if (aiCollapsed.value) {
    assistantRef.value.expandAssistant?.()
  }
}

async function triggerAiAutofill() {
  if (!assistantRef.value?.sendPrompt) {
    ElMessage.warning('AI 助手暂未初始化完成')
    return
  }
  try {
    assistantRef.value.expandAssistant?.()
    await assistantRef.value.sendPrompt(AUTO_FILL_CHAT_MESSAGE)
    ElMessage.success('已发起自动填写请求')
  } catch (error) {
    ElMessage.error(error.message || '发送 AI 自动填写请求失败')
  }
}

async function handleBeforeAiSend({ prompt, syncPagePayloadToWorkspace }) {
  const contextSnapshot = buildContextSnapshot()
  if (typeof syncPagePayloadToWorkspace === 'function') {
    await syncPagePayloadToWorkspace(contextSnapshot)
  }
  lastSubmittedContextSignature.value = buildContextSignature(contextSnapshot)
  return prompt
}

async function handleAiTaskEnded(event) {
  try {
    const workspaceContext = normalizeWorkspaceContextContent(event?.data)
    if (!workspaceContext) {
      return
    }
    const latestSignature = buildContextSignature(workspaceContext)
    if (!latestSignature || latestSignature === lastSubmittedContextSignature.value) {
      return
    }
    applyAiAutofillPayload(workspaceContext)
    lastSubmittedContextSignature.value = latestSignature
    ElMessage.success('已读取 AI 写入的 context.json 并刷新表单')
  } catch (error) {
    ElMessage.warning(error.message || '读取 AI 写入的 context.json 失败，请检查文件是否已更新')
  }
}

// 切换表单模式后重置会话：formMode 属于会话级配置，需在下一次 openSession 时生效
watch(formModeEnabled, () => {
  assistantRef.value?.startNewSession?.()
})
</script>

<template>
  <div class="procurement-example">
    <div class="page-shell">
      <div class="main-panel">
        <el-skeleton :loading="loading" animated>
          <template #template>
            <el-skeleton-item variant="rect" style="width: 100%; height: 100%;" />
          </template>

          <template #default>
            <div class="page-header">
              <div>
                <div class="breadcrumb-text">{{ scenario?.breadcrumbs?.join(' / ') || '业务示例' }}</div>
                <h2 class="page-title">{{ scenario?.title || '采购需求申请' }}</h2>
                <p class="page-subtitle">{{ scenario?.subtitle }}</p>
              </div>

              <el-space wrap>
                <el-switch
                  v-model="formModeEnabled"
                  active-text="表单模式"
                  inactive-text="编辑模式"
                />
                <el-button @click="loadScenarioContext(true)">初始化数据</el-button>
                <el-button v-if="aiCollapsed" type="primary" @click="handleToggleAiAssistant">
                  打开 AI 助手
                </el-button>
                <el-button type="primary" @click="triggerAiAutofill">
                  AI 自动填写
                </el-button>
              </el-space>
            </div>

            <el-alert
              :title="scenario?.goal || '根据表单上下文让 AI 自动填写采购需求表单'"
              type="info"
              :closable="false"
              show-icon
            />

            <!-- ======== 一、采购需求基础信息 ======== -->
            <el-card shadow="never" class="section-card">
              <template #header><div class="section-title">采购需求基础信息</div></template>
              <el-form label-width="130px" class="header-form">
                <!-- 基本信息 -->
                <div class="form-sub-title">基本信息</div>
                <div class="form-grid form-grid-3">
                  <el-form-item label="需求标题">
                    <el-input v-model="formModel.requireTitle" placeholder="请输入需求标题" />
                  </el-form-item>
                  <el-form-item label="需求编号">
                    <el-input v-model="formModel.requireNo" placeholder="系统自动生成" disabled />
                  </el-form-item>
                  <el-form-item label="需求属性">
                    <el-select v-model="formModel.requireAttribute" placeholder="请选择">
                      <el-option v-for="o in optionMaps.requireAttributeOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="采购类别">
                    <el-select v-model="formModel.purchaseCategory" placeholder="请选择">
                      <el-option v-for="o in optionMaps.purchaseCategoryOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="需求紧急度">
                    <el-select v-model="formModel.emergency" placeholder="请选择">
                      <el-option v-for="o in optionMaps.emergencyOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="预计启动时间">
                    <el-date-picker v-model="formModel.estimatedStartTime" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width:100%" />
                  </el-form-item>
                </div>

                <!-- 组织与人员 -->
                <div class="form-sub-title">组织与人员</div>
                <div class="form-grid form-grid-2">
                  <el-form-item label="申购部门">
                    <el-input v-model="formModel.subscribeDepartName" placeholder="请输入申购部门" />
                  </el-form-item>
                  <el-form-item label="需求部门">
                    <el-input v-model="formModel.requireDepartNames" placeholder="请输入需求部门" />
                  </el-form-item>
                  <el-form-item label="经办人">
                    <el-input v-model="formModel.operatorName" placeholder="请输入经办人" />
                  </el-form-item>
                  <el-form-item label="申请人">
                    <el-input v-model="formModel.applyName" placeholder="请输入申请人" />
                  </el-form-item>
                </div>

                <!-- 描述信息 -->
                <div class="form-sub-title">描述信息</div>
                <el-form-item label="采购内容描述">
                  <el-input v-model="formModel.purchaseContent" type="textarea" :rows="3" placeholder="请输入采购内容描述" />
                </el-form-item>
                <el-form-item label="商务服务要求">
                  <el-input v-model="formModel.busService" type="textarea" :rows="2" placeholder="请输入商务服务要求" />
                </el-form-item>

                <!-- 采购特征 -->
                <div class="form-sub-title">采购特征</div>
                <div class="form-grid form-grid-4">
                  <el-form-item label="是否单一来源">
                    <el-radio-group v-model="formModel.isSingleSource">
                      <el-radio v-for="o in optionMaps.ynOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="是否进口采购">
                    <el-radio-group v-model="formModel.isImportPurchase">
                      <el-radio v-for="o in optionMaps.ynOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="是否三重一大">
                    <el-radio-group v-model="formModel.isMajor">
                      <el-radio v-for="o in optionMaps.ynOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="是否信息化">
                    <el-radio-group v-model="formModel.isInformation">
                      <el-radio v-for="o in optionMaps.ynOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="是否委托">
                    <el-radio-group v-model="formModel.isEntrust">
                      <el-radio v-for="o in optionMaps.ynOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="是否涉密">
                    <el-radio-group v-model="formModel.isSecret">
                      <el-radio v-for="o in optionMaps.ynOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="是否年初预算">
                    <el-radio-group v-model="formModel.isBeginningBudget">
                      <el-radio v-for="o in optionMaps.ynOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="适宜中小企业">
                    <el-radio-group v-model="formModel.isSmb">
                      <el-radio v-for="o in optionMaps.isSmbOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                </div>

                <!-- 目录与预算 -->
                <div class="form-sub-title">目录与预算</div>
                <div class="form-grid form-grid-3">
                  <el-form-item label="采购目录性质">
                    <el-select v-model="formModel.requireCatalog" placeholder="请选择">
                      <el-option v-for="o in optionMaps.requireCatalogOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="预算类型">
                    <el-select v-model="formModel.budgetType" placeholder="请选择">
                      <el-option v-for="o in optionMaps.budgetTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="资金性质">
                    <el-select v-model="formModel.fundsSource" placeholder="请选择">
                      <el-option v-for="o in optionMaps.fundsSourceOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="资金来源">
                    <el-input v-model="formModel.costSubject" placeholder="请输入资金来源" />
                  </el-form-item>
                  <el-form-item label="资金方向">
                    <el-select v-model="formModel.fundFlow" placeholder="请选择">
                      <el-option v-for="o in optionMaps.fundFlowOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </el-form-item>
                </div>

                <!-- 采购方式 -->
                <div class="form-sub-title">采购方式</div>
                <div class="form-grid form-grid-2">
                  <el-form-item label="初拟组织形式">
                    <el-select v-model="formModel.organizeForm" placeholder="请选择">
                      <el-option v-for="o in optionMaps.organizeFormOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="组织形式性质">
                    <el-select v-model="formModel.organizeFormExtra" placeholder="请选择">
                      <el-option v-for="o in optionMaps.organizeFormExtraOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="初拟采购方式">
                    <el-select v-model="formModel.purchaseWay" placeholder="请选择">
                      <el-option v-for="o in optionMaps.purchaseWayOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="采购执行方式">
                    <el-select v-model="formModel.processWay" placeholder="请选择">
                      <el-option v-for="o in optionMaps.processWayOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </el-form-item>
                </div>

                <!-- 执行部门与预算 -->
                <div class="form-sub-title">执行部门与预算</div>
                <div class="form-grid form-grid-2">
                  <el-form-item label="归口执行部门">
                    <el-input v-model="formModel.centralizedDepartName" placeholder="请输入归口执行部门" />
                  </el-form-item>
                  <el-form-item label="采购执行部门">
                    <el-input v-model="formModel.purchaseDepartName" placeholder="请输入采购执行部门" />
                  </el-form-item>
                  <el-form-item label="预算项目">
                    <el-input v-model="formModel.budgetName" placeholder="请输入预算项目名称" />
                  </el-form-item>
                  <el-form-item label="预算编号">
                    <el-input v-model="formModel.budgetNo" placeholder="请输入预算编号" />
                  </el-form-item>
                </div>

                <!-- 金额与时间 -->
                <div class="form-sub-title">金额与时间</div>
                <div class="form-grid form-grid-3">
                  <el-form-item label="申购金额（元）">
                    <el-input-number v-model="formModel.purchaseAmount" :min="0" :step="100" controls-position="right" />
                  </el-form-item>
                  <el-form-item label="审定金额（元）">
                    <el-input-number v-model="formModel.confirmAmount" :min="0" :step="100" controls-position="right" />
                  </el-form-item>
                  <el-form-item label="预算总金额（元）">
                    <el-input-number v-model="formModel.requireBudgetAmount" :min="0" :step="100" controls-position="right" />
                  </el-form-item>
                  <el-form-item label="计划开始时间">
                    <el-date-picker v-model="formModel.planBeginTime" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width:100%" />
                  </el-form-item>
                  <el-form-item label="计划完成时间">
                    <el-date-picker v-model="formModel.planFinishTime" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width:100%" />
                  </el-form-item>
                </div>

                <!-- 其他信息 -->
                <div class="form-sub-title">其他信息</div>
                <div class="form-grid form-grid-2">
                  <el-form-item label="单据类型">
                    <el-select v-model="formModel.businessDocType" placeholder="请选择">
                      <el-option v-for="o in optionMaps.businessDocTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="关联单据">
                    <el-input v-model="formModel.fromBizName" placeholder="请输入关联单据名称" />
                  </el-form-item>
                  <el-form-item label="是否跨年项目">
                    <el-radio-group v-model="formModel.isOverYear">
                      <el-radio v-for="o in optionMaps.ynOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="是否补录">
                    <el-radio-group v-model="formModel.isAddition">
                      <el-radio v-for="o in optionMaps.ynOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="改变主体结构">
                    <el-radio-group v-model="formModel.isChangeStructure">
                      <el-radio v-for="o in optionMaps.ynOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="项目包含内容">
                    <el-input v-model="formModel.containContent" placeholder="多个用逗号分隔" />
                  </el-form-item>
                </div>
                <el-form-item label="资金说明">
                  <el-input v-model="formModel.budgetDesc" type="textarea" :rows="2" placeholder="请输入资金说明" />
                </el-form-item>
                <el-form-item label="备注">
                  <el-input v-model="formModel.budgetRemark" type="textarea" :rows="2" placeholder="请输入备注" />
                </el-form-item>
              </el-form>
            </el-card>

            <!-- ======== 二、采购标的明细 ======== -->
            <el-card shadow="never" class="section-card">
              <template #header>
                <div class="section-title section-title-between">
                  <span>采购标的明细</span>
                  <el-button link type="primary" @click="addTarget">新增标的</el-button>
                </div>
              </template>

              <el-alert
                title="演示说明：点击“AI 自动填写”后，会把当前表单上下文发送给右侧 AI 助手，并自动把返回的 JSON 回填到页面。"
                type="warning"
                :closable="false"
                show-icon
                class="tips-alert"
              />

              <!-- 需求附件上传区域 -->
              <div class="attachment-section">
                <div class="attachment-header">
                  <span class="attachment-label">需求附件</span>
                  <el-button
                    size="small"
                    type="primary"
                    :loading="uploading"
                    @click="handleUploadAttachment"
                  >
                    上传附件
                  </el-button>
                  <input
                    ref="uploadInputRef"
                    type="file"
                    style="display: none"
                    @change="handleFileChange"
                  />
                </div>
                <div v-if="formModel.attachments.length === 0" class="attachment-empty">
                  暂无附件，点击"上传附件"添加需求相关文件
                </div>
                <div v-else class="attachment-list">
                  <div
                    v-for="(attachment, index) in formModel.attachments"
                    :key="attachment.storedName || index"
                    class="attachment-item"
                  >
                    <span class="attachment-name">{{ attachment.originalName }}</span>
                    <span class="attachment-size">{{ formatFileSize(attachment.size) }}</span>
                    <el-button link type="danger" size="small" @click="removeAttachment(index)">删除</el-button>
                  </div>
                </div>
              </div>

              <el-table :data="formModel.targetList" border class="line-item-table">
                <el-table-column prop="rowNo" label="序号" width="60" align="center" />
                <el-table-column label="标的名称" min-width="140">
                  <template #default="{ row }">
                    <el-input v-model="row.targetName" placeholder="请输入" />
                  </template>
                </el-table-column>
                <el-table-column label="标的类型" min-width="120">
                  <template #default="{ row }">
                    <el-select v-model="row.targetTypeName" placeholder="请选择">
                      <el-option v-for="o in optionMaps.targetTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="采购分类" min-width="120">
                  <template #default="{ row }">
                    <el-select v-model="row.purchaseTypeName" placeholder="请选择">
                      <el-option v-for="o in optionMaps.purchaseTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="数量" width="100">
                  <template #default="{ row }">
                    <el-input-number v-model="row.num" :min="1" @change="recalculatePurchaseAmount" />
                  </template>
                </el-table-column>
                <el-table-column label="单价（元）" width="130">
                  <template #default="{ row }">
                    <el-input-number v-model="row.unitPrice" :min="0" :step="100" @change="recalculatePurchaseAmount" />
                  </template>
                </el-table-column>
                <el-table-column label="单位" width="80">
                  <template #default="{ row }">
                    <el-input v-model="row.unit" placeholder="台" />
                  </template>
                </el-table-column>
                <el-table-column label="金额（元）" width="120">
                  <template #default="{ row }">
                    <span>{{ toNumber(row.num) * toNumber(row.unitPrice) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="规格参数" min-width="160">
                  <template #default="{ row }">
                    <el-input v-model="row.targetContent" placeholder="请输入" />
                  </template>
                </el-table-column>
                <el-table-column label="存放地" min-width="120">
                  <template #default="{ row }">
                    <el-input v-model="row.storageArea" placeholder="请输入" />
                  </template>
                </el-table-column>
                <el-table-column label="期望使用时间" width="140">
                  <template #default="{ row }">
                    <el-date-picker v-model="row.expectPurchaseTime" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width:100%" />
                  </template>
                </el-table-column>
                <el-table-column label="参考品牌" min-width="140">
                  <template #default="{ row }">
                    <el-input v-model="row.referenceListStr" placeholder="至少3家" />
                  </template>
                </el-table-column>
                <el-table-column label="备注" min-width="120">
                  <template #default="{ row }">
                    <el-input v-model="row.remark" placeholder="请输入" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80" fixed="right">
                  <template #default="{ $index }">
                    <el-button link type="danger" @click="removeTarget($index)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>

              <div class="footer-actions">
                <div class="amount-summary">
                  标的汇总金额：
                  <strong>{{ totalTargetAmount }}</strong>
                  元
                </div>
                <el-space wrap>
                  <el-button>保存草稿</el-button>
                  <el-button type="primary">提交审批</el-button>
                  <el-button>返回</el-button>
                </el-space>
              </div>
            </el-card>

            <!-- ======== 三、采购商务条款 ======== -->
            <el-card shadow="never" class="section-card">
              <template #header>
                <div class="section-title section-title-between">
                  <span>采购商务条款</span>
                  <el-button link type="primary" @click="addBusinessEntry">新增条款</el-button>
                </div>
              </template>
              <el-table :data="formModel.businessEntryList" border class="line-item-table">
                <el-table-column prop="rowNo" label="序号" width="60" align="center" />
                <el-table-column label="适用分类" min-width="120">
                  <template #default="{ row }"><el-input v-model="row.entriesCategory" placeholder="请输入" /></template>
                </el-table-column>
                <el-table-column label="商务条目" min-width="160">
                  <template #default="{ row }"><el-input v-model="row.businessItem" placeholder="请输入" /></template>
                </el-table-column>
                <el-table-column label="商务要求" min-width="200">
                  <template #default="{ row }"><el-input v-model="row.businessRequirement" type="textarea" :rows="1" placeholder="请输入" /></template>
                </el-table-column>
                <el-table-column label="商务要求结论" min-width="160">
                  <template #default="{ row }"><el-input v-model="row.businessRequirementResult" placeholder="请输入" /></template>
                </el-table-column>
                <el-table-column label="标准依据" min-width="160">
                  <template #default="{ row }"><el-input v-model="row.standardBasis" placeholder="请输入" /></template>
                </el-table-column>
                <el-table-column label="操作" width="80" fixed="right">
                  <template #default="{ $index }"><el-button link type="danger" @click="removeBusinessEntry($index)">删除</el-button></template>
                </el-table-column>
              </el-table>
            </el-card>

            <el-card v-if="aiSummary" shadow="never" class="section-card">
              <template #header>
                <div class="section-title">AI 返回摘要</div>
              </template>
              <div class="ai-summary-text">{{ aiSummary }}</div>
            </el-card>
          </template>
        </el-skeleton>
      </div>

      <div class="assistant-panel" :class="{ collapsed: aiCollapsed }">
        <AiChatWorkspace
          ref="assistantRef"
          title="AI 智能分析助手"
          subtitle="发送前会自动把当前页面查询结构同步到工作区，AI 完成后直接回填页面并同步最新上下文。"
          placeholder="例如：帮我完善采购需求申请理由，并给出更合理的设备配置建议"
          v-bind="aiAssistantHandlers"
          welcome-message="您好！我是您的AI助手，可以帮您填写和审查采购需求申请单。请问有什么可以帮助您的？"
          :welcome-suggestions="[{
            label: '采购合规检查',
            content: '帮我检查表单是否符合政府采购规定',
          },
          {
            label: '校验补全表单',
            content: '帮我校验和补充表单内容',
          },
          {
            label: '提取附件信息填表',
            content: '帮我从采购需求文件中提取信息填到表单',
          },
        ]"
          :skill-name-value="PROCUREMENT_FORM_SKILL"
          :system-prompt-value="BUSINESS_AI_SYSTEM_PROMPT"
          :compact-header="true"
          :current-file-key="aiContextFileKey"
          :identities-value="['programming','workspace-core']"
          :form-mode-value="formModeEnabled"
          :form-name-value="PROCUREMENT_FORM_NAME"
          :form-visible-fields-value="PROCUREMENT_FORM_VISIBLE_FIELDS"
          :load-history-on-mounted="true"
          :show-working-directory="false"
          :collapsible="true"
          :on-before-send="handleBeforeAiSend"
          :on-task-ended="handleAiTaskEnded"
          height="calc(100vh - 96px)"
          :min-height="460"
          @collapse-change="handleAiCollapseChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.procurement-example {
  width: 100%;
  padding-bottom: 24px;
}

.page-shell {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
}

.main-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.assistant-panel {
  width: 35%;
  flex: 0 0 35%;
  min-width: 320px;
  position: sticky;
  top: 12px;
  align-self: flex-start;
  transition: width 0.3s ease, flex-basis 0.3s ease;
}

.assistant-panel.collapsed {
  width: 48px;
  flex: 0 0 48px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 2px;
}

.breadcrumb-text {
  color: #94a3b8;
  font-size: 12px;
}

.page-title {
  margin: 6px 0 4px;
  color: #111827;
  font-size: 22px;
  font-weight: 700;
}

.page-subtitle {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.section-card {
  border-radius: 12px;
}

.section-title {
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}

.section-title-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.form-sub-title {
  color: #374151;
  font-size: 13px;
  font-weight: 600;
  margin: 16px 0 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid #e5e7eb;
}

.header-form,
.bottom-form {
  margin-top: 4px;
}

.form-grid {
  display: grid;
  gap: 8px 12px;
}

.form-grid-2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid-3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.form-grid-4 {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.tips-alert {
  margin-bottom: 12px;
}

.attachment-section {
  margin-top: 12px;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.attachment-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.attachment-label {
  color: #111827;
  font-size: 14px;
  font-weight: 600;
}

.attachment-empty {
  margin-top: 8px;
  color: #94a3b8;
  font-size: 13px;
}

.attachment-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 13px;
}

.attachment-name {
  flex: 1;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-size {
  color: #94a3b8;
  flex-shrink: 0;
}

.line-item-table {
  margin-top: 8px;
}

.footer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
}

.amount-summary {
  color: #475569;
  font-size: 14px;
}

.ai-summary-text {
  color: #475569;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1200px) {
  .form-grid-3,
  .form-grid-4 {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .page-shell {
    flex-direction: column;
  }

  .assistant-panel {
    order: -1;
    width: 100%;
    flex-basis: auto;
    position: static;
    height: auto;
    max-height: none;
    min-height: 460px;
  }
}

@media (max-width: 900px) {
  .form-grid-2,
  .form-grid-3,
  .form-grid-4,
  .footer-actions {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }

  .page-header {
    flex-direction: column;
  }
}
</style>
