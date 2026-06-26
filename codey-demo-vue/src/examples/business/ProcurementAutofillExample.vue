<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import AiChatWorkspace from '../../components/ai/AiChatWorkspace.vue'
import { createBusinessScenarioApi } from '../../api/businessScenario'
import { createBusinessAiWorkspace } from '../../composables/useBusinessAiWorkspace'

const businessScenarioApi = createBusinessScenarioApi()
const assistantRef = ref(null)
const loading = ref(false)
const aiSending = ref(false)
const aiCollapsed = ref(false)
const aiSummary = ref('')
const scenario = ref(null)
const lineItems = ref([])
const lastSubmittedContextSignature = ref('')
const PROCUREMENT_FORM_SKILL = 'procurement-form-agent'
const AUTO_FILL_CHAT_MESSAGE = '请帮我自动填写当前表单。'
// 业务示例额外补充一条面向用户展示的系统提示，约束思考内容更易读。
const BUSINESS_AI_SYSTEM_PROMPT = '1.思考内容不要出现表单字段的英文名称，使用中文替代\n2.不要使用markdown格式输出'

const formModel = reactive({
  urgencyLevel: '',
  relatedOrderNo: '',
  requestDate: '',
  requestDepartment: '',
  assetType: '',
  purchaseType: '',
  requestDescription: '',
  budgetAmount: 0,
  requestReason: '',
  exceedReason: '',
})

const optionMaps = reactive({
  urgencyOptions: [],
  orderOptions: [],
  departmentOptions: [],
  categoryOptions: [],
  purchaseTypeOptions: [],
  catalogScopeOptions: [],
})

const totalAmount = computed(() =>
  lineItems.value.reduce((sum, item) => sum + toNumber(item.quantity) * toNumber(item.unitPrice), 0)
)

const businessAiWorkspace = createBusinessAiWorkspace({
  workspaceId: 'business-procurement',
  workingDirectory: './business-procurement',
})
const aiWorkspaceId = businessAiWorkspace.workspaceId
const aiWorkingDirectory = businessAiWorkspace.workingDirectory
const aiContextFileKey = businessAiWorkspace.contextFileKey
const aiContextDocumentId = businessAiWorkspace.contextDocumentId

function handleAiCollapseChange(collapsed) {
  aiCollapsed.value = collapsed
}

function toNumber(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function mapOptions(list = []) {
  return Array.isArray(list)
    ? list.map((item) => ({
        value: item.value,
        label: item.value,
        description: item.description || '',
      }))
    : []
}

function normalizeLineItem(item = {}, index = 0) {
  return {
    rowNo: item.rowNo || index + 1,
    demandType: item.demandType || '办公类',
    category: item.category || '',
    itemName: item.itemName || '',
    quantity: toNumber(item.quantity) || 1,
    unitPrice: toNumber(item.unitPrice),
    specification: item.specification || '',
    referenceBrand: item.referenceBrand || '',
    recommended: Boolean(item.recommended),
  }
}

// context.json 与查询接口返回结构保持一致，前端展示和 AI 上下文共用同一份格式。
function buildContextSnapshot() {
  return {
    scenarioId: scenario.value?.scenarioId || 'purchase-request-autofill',
    title: scenario.value?.title || '采购申请-新建',
    subtitle: scenario.value?.subtitle || '',
    breadcrumbs: scenario.value?.breadcrumbs || [],
    goal: scenario.value?.goal || '',
    header: {
      urgencyLevel: formModel.urgencyLevel,
      relatedOrderNo: formModel.relatedOrderNo,
      requestDate: formModel.requestDate,
      requestDepartment: formModel.requestDepartment,
      assetType: formModel.assetType,
      purchaseType: formModel.purchaseType,
      requestDescription: formModel.requestDescription,
    },
    detail: {
      budgetAmount: formModel.budgetAmount,
      requestReason: formModel.requestReason,
      exceedReason: formModel.exceedReason,
    },
    items: lineItems.value.map((item) => ({
      rowNo: item.rowNo,
      demandType: item.demandType,
      category: item.category,
      itemName: item.itemName,
      quantity: item.quantity,
      unitPrice: item.unitPrice,
      specification: item.specification,
      referenceBrand: item.referenceBrand,
      recommended: item.recommended,
    })),
    formFields: scenario.value?.formFields || [],
    aiInsightCards: scenario.value?.aiInsightCards || [],
    aiSuggestions: scenario.value?.aiSuggestions || [],
    urgencyOptions: scenario.value?.urgencyOptions || [],
    orderOptions: scenario.value?.orderOptions || [],
    departmentOptions: scenario.value?.departmentOptions || [],
    categoryOptions: scenario.value?.categoryOptions || [],
    purchaseTypeOptions: scenario.value?.purchaseTypeOptions || [],
    catalogScopeOptions: scenario.value?.catalogScopeOptions || [],
    aiInstruction: scenario.value?.aiInstruction || '',
  }
}

async function syncContextToWorkspace() {
  await businessAiWorkspace.syncContext(buildContextSnapshot())
}

function buildContextSignature(payload) {
  return JSON.stringify(payload || {})
}

function applyScenarioContext(context) {
  scenario.value = context || null
  formModel.urgencyLevel = context?.header?.urgencyLevel || ''
  formModel.relatedOrderNo = context?.header?.relatedOrderNo || ''
  formModel.requestDate = context?.header?.requestDate || ''
  formModel.requestDepartment = context?.header?.requestDepartment || ''
  formModel.assetType = context?.header?.assetType || ''
  formModel.purchaseType = context?.header?.purchaseType || ''
  formModel.requestDescription = context?.header?.requestDescription || ''
  formModel.budgetAmount = toNumber(context?.detail?.budgetAmount)
  formModel.requestReason = context?.detail?.requestReason || ''
  formModel.exceedReason = context?.detail?.exceedReason || ''
  lineItems.value = Array.isArray(context?.items)
    ? context.items.map((item, index) => normalizeLineItem(item, index))
    : []
  optionMaps.urgencyOptions = mapOptions(context?.urgencyOptions)
  optionMaps.orderOptions = mapOptions(context?.orderOptions)
  optionMaps.departmentOptions = mapOptions(context?.departmentOptions)
  optionMaps.categoryOptions = mapOptions(context?.categoryOptions)
  optionMaps.purchaseTypeOptions = mapOptions(context?.purchaseTypeOptions)
  optionMaps.catalogScopeOptions = mapOptions(context?.catalogScopeOptions)
  recalculateBudgetAmount()
}

async function loadScenarioContext(showMessage = false) {
  loading.value = true
  try {
    const context = await businessScenarioApi.getProcurementContext()
    applyScenarioContext(context)
    if (showMessage) {
      ElMessage.success('演示数据已重新加载')
    }
  } catch (error) {
    ElMessage.error(error.message || '读取业务示例失败')
  } finally {
    loading.value = false
  }
}

function recalculateBudgetAmount() {
  // 预算金额由明细行自动汇总，确保页面展示与 AI 上下文保持一致。
  formModel.budgetAmount = totalAmount.value
}

function addLineItem() {
  lineItems.value.push(
    normalizeLineItem(
      {
        rowNo: lineItems.value.length + 1,
        demandType: '办公类',
      },
      lineItems.value.length,
    ),
  )
  recalculateBudgetAmount()
}

function removeLineItem(index) {
  lineItems.value.splice(index, 1)
  lineItems.value = lineItems.value.map((item, rowIndex) => ({
    ...item,
    rowNo: rowIndex + 1,
  }))
  recalculateBudgetAmount()
}

function applyAiAutofillPayload(payload = {}) {
  const header = payload.header || {}
  const detail = payload.detail || {}
  const items = Array.isArray(payload.items) ? payload.items : []

  formModel.urgencyLevel = header.urgencyLevel || formModel.urgencyLevel
  formModel.relatedOrderNo = header.relatedOrderNo || formModel.relatedOrderNo
  formModel.requestDate = header.requestDate || formModel.requestDate
  formModel.requestDepartment = header.requestDepartment || formModel.requestDepartment
  formModel.assetType = header.assetType || formModel.assetType
  formModel.purchaseType = header.purchaseType || formModel.purchaseType
  formModel.requestDescription = header.requestDescription || formModel.requestDescription
  formModel.requestReason = detail.requestReason || formModel.requestReason
  formModel.exceedReason = detail.exceedReason || formModel.exceedReason

  if (items.length > 0) {
    lineItems.value = items.map((item, index) =>
      normalizeLineItem(
        {
          ...item,
          rowNo: item.rowNo || index + 1,
        },
        index,
      ),
    )
  }

  const budgetAmount = toNumber(detail.budgetAmount)
  formModel.budgetAmount = budgetAmount > 0 ? budgetAmount : totalAmount.value
  if (!budgetAmount) {
    recalculateBudgetAmount()
  }
}

async function triggerAiAutofill() {
  if (!assistantRef.value?.sendPrompt) {
    ElMessage.warning('AI 助手暂未初始化完成')
    return
  }
  aiSending.value = true
  try {
    // 自动填写按钮只是在右侧聊天里代替操作人发一句“请自动填写”，不再维护专用提示词。
    await assistantRef.value.sendPrompt(AUTO_FILL_CHAT_MESSAGE)
    ElMessage.success('已发起自动填写请求')
  } catch (error) {
    aiSending.value = false
    ElMessage.error(error.message || '发送 AI 自动填写请求失败')
  }
}

async function handleBeforeAiSend() {
  // 业务表单可能被用户手动改动，所以每次发送前都同步一次最新上下文。
  const contextSnapshot = buildContextSnapshot()
  await businessAiWorkspace.prepareForSend(contextSnapshot)
  lastSubmittedContextSignature.value = buildContextSignature(contextSnapshot)
}

async function handleAssistantFinished(result) {
  aiSending.value = false
  aiSummary.value = result?.summary || result?.content || ''
  try {
    const workspaceContext = await businessAiWorkspace.loadContext()
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

onMounted(async () => {
  await loadScenarioContext()
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
                <h2 class="page-title">{{ scenario?.title || '采购申请-新建' }}</h2>
                <p class="page-subtitle">{{ scenario?.subtitle }}</p>
              </div>

              <el-space wrap>
                <el-button @click="loadScenarioContext(true)">重置演示数据</el-button>
                <el-button type="primary" :loading="aiSending" @click="triggerAiAutofill">
                  AI 自动填写
                </el-button>
              </el-space>
            </div>

            <el-alert
              :title="scenario?.goal || '根据表单上下文让 AI 自动填写表单'"
              type="info"
              :closable="false"
              show-icon
            />

            <el-card shadow="never" class="section-card">
              <template #header>
                <div class="section-title">需求来源信息</div>
              </template>

              <el-form label-width="110px" class="header-form">
                <div class="form-grid">
                  <el-form-item label="紧急度">
                    <el-select v-model="formModel.urgencyLevel" placeholder="请选择">
                      <el-option
                        v-for="option in optionMaps.urgencyOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                      />
                    </el-select>
                  </el-form-item>

                  <el-form-item label="关联前期申购单">
                    <el-select v-model="formModel.relatedOrderNo" placeholder="请选择">
                      <el-option
                        v-for="option in optionMaps.orderOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                      />
                    </el-select>
                  </el-form-item>

                  <el-form-item label="申请日期">
                    <el-input v-model="formModel.requestDate" placeholder="请输入" />
                  </el-form-item>

                  <el-form-item label="申请部门">
                    <el-select v-model="formModel.requestDepartment" placeholder="请选择">
                      <el-option
                        v-for="option in optionMaps.departmentOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                      />
                    </el-select>
                  </el-form-item>

                  <el-form-item label="属性">
                    <el-radio-group v-model="formModel.assetType">
                      <el-radio
                        v-for="option in optionMaps.categoryOptions"
                        :key="option.value"
                        :label="option.value"
                      >
                        {{ option.label }}
                      </el-radio>
                    </el-radio-group>
                  </el-form-item>

                  <el-form-item label="采购类别">
                    <el-radio-group v-model="formModel.purchaseType">
                      <el-radio
                        v-for="option in optionMaps.purchaseTypeOptions"
                        :key="option.value"
                        :label="option.value"
                      >
                        {{ option.label }}
                      </el-radio>
                    </el-radio-group>
                  </el-form-item>
                </div>

                <el-form-item label="采购内容描述">
                  <el-input
                    v-model="formModel.requestDescription"
                    type="textarea"
                    :rows="3"
                    placeholder="请输入采购内容描述"
                  />
                </el-form-item>
              </el-form>
            </el-card>

            <el-card shadow="never" class="section-card">
              <template #header>
                <div class="section-title section-title-between">
                  <span>录入需求明细</span>
                  <el-button link type="primary" @click="addLineItem">新增行</el-button>
                </div>
              </template>

              <el-alert
                title="演示说明：点击“AI 自动填写”后，会把当前表单上下文发送给右侧 AI 助手，并自动把返回的 JSON 回填到页面。"
                type="warning"
                :closable="false"
                show-icon
                class="tips-alert"
              />

              <div class="detail-toolbar">
                <el-form label-width="110px" class="detail-form">
                  <div class="detail-grid">
                    <el-form-item label="申请金额（元）">
                      <el-input-number
                        v-model="formModel.budgetAmount"
                        :min="0"
                        :step="100"
                        controls-position="right"
                      />
                    </el-form-item>

                    <el-form-item label="需求原因说明">
                      <el-input
                        v-model="formModel.requestReason"
                        placeholder="存在超预算或临时申请时请填写原因"
                      />
                    </el-form-item>
                  </div>
                </el-form>
              </div>

              <el-table :data="lineItems" border class="line-item-table">
                <el-table-column prop="rowNo" label="序号" width="60" align="center" />
                <el-table-column label="需求部门" min-width="110">
                  <template #default="{ row }">
                    <el-input v-model="row.demandType" placeholder="请输入" />
                  </template>
                </el-table-column>
                <el-table-column label="目录类型" min-width="140">
                  <template #default="{ row }">
                    <!-- 目录类型只允许选择政府采购统一采购目录内或目录外。 -->
                    <el-select v-model="row.category" placeholder="请选择">
                      <el-option
                        v-for="option in optionMaps.catalogScopeOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                      />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="标的名称" min-width="140">
                  <template #default="{ row }">
                    <el-input v-model="row.itemName" placeholder="请输入" />
                  </template>
                </el-table-column>
                <el-table-column label="数量" width="110">
                  <template #default="{ row }">
                    <el-input-number v-model="row.quantity" :min="1" @change="recalculateBudgetAmount" />
                  </template>
                </el-table-column>
                <el-table-column label="单价（元）" width="130">
                  <template #default="{ row }">
                    <el-input-number v-model="row.unitPrice" :min="0" :step="100" @change="recalculateBudgetAmount" />
                  </template>
                </el-table-column>
                <el-table-column label="规格型号" min-width="180">
                  <template #default="{ row }">
                    <el-input v-model="row.specification" placeholder="请输入" />
                  </template>
                </el-table-column>
                <el-table-column label="参考品牌" min-width="140">
                  <template #default="{ row }">
                    <el-input v-model="row.referenceBrand" placeholder="请输入" />
                  </template>
                </el-table-column>
                <el-table-column label="推荐" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.recommended ? 'success' : 'info'">
                      {{ row.recommended ? '是' : '否' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80" fixed="right">
                  <template #default="{ $index }">
                    <el-button link type="danger" @click="removeLineItem($index)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>

              <el-form label-width="110px" class="bottom-form">
                <el-form-item label="超标原因说明">
                  <el-input
                    v-model="formModel.exceedReason"
                    type="textarea"
                    :rows="2"
                    placeholder="存在超预算或临时申请时请填写原因"
                  />
                </el-form-item>
              </el-form>

              <div class="footer-actions">
                <div class="amount-summary">
                  当前汇总金额：
                  <strong>{{ totalAmount }}</strong>
                  元
                </div>
                <el-space wrap>
                  <el-button>保存草稿</el-button>
                  <el-button type="primary">提交审批</el-button>
                  <el-button>返回</el-button>
                </el-space>
              </div>
            </el-card>

            <el-card v-if="aiSummary" shadow="never" class="section-card">
              <template #header>
                <div class="section-title">AI 返回摘要</div>
              </template>

              <!-- AI 摘要直接展示模型最终返回文本，避免前端把内容强行套成固定卡片结构。 -->
              <div class="ai-summary-text">{{ aiSummary }}</div>
            </el-card>
          </template>
        </el-skeleton>
      </div>

      <div class="assistant-panel" :class="{ collapsed: aiCollapsed }">
        <AiChatWorkspace
          ref="assistantRef"
          title="AI 智能分析助手"
          subtitle="发送前会自动把当前页面查询结构同步到工作区 context.json，AI 完成后直接回填页面并同步最新上下文。"
          placeholder="例如：帮我完善采购申请理由，并给出更合理的设备配置建议"
          :skill-name-value="PROCUREMENT_FORM_SKILL"
          :system-prompt-value="BUSINESS_AI_SYSTEM_PROMPT"
          :compact-header="true"
          :workspace-id-value="aiWorkspaceId"
          :current-file-key="aiContextFileKey"
          :current-document-id="aiContextDocumentId"
          :working-directory-value="aiWorkingDirectory"
          :identities-value="['programming']"
          :load-history-on-mounted="true"
          :show-thinking="false"
          :show-working-directory="false"
          :collapsible="true"
          :on-before-send="handleBeforeAiSend"
          :on-assistant-finished="handleAssistantFinished"
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
  height: auto;
  min-height: 100%;
}

.page-shell {
  display: flex;
  align-items: stretch;
  gap: 10px;
  width: 100%;
  height: auto;
  min-height: 100%;
}

.main-panel {
  flex: 1;
  min-width: 0;
  /* 主内容区改为自然撑开，避免在页面内部再出现一层滚动条。 */
  min-height: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: visible;
}

.assistant-panel {
  width: 420px;
  flex: 0 0 420px;
  /* 右侧聊天区需要给页面顶栏和外层留白预留高度，避免底部发送框被挤出视口。 */
  position: sticky;
  top: 12px;
  align-self: flex-start;
  height: calc(100vh - 96px);
  max-height: calc(100vh - 96px);
  min-height: 460px;
  overflow: hidden;
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

.header-form,
.detail-form,
.bottom-form {
  margin-top: 4px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.tips-alert {
  margin-bottom: 12px;
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

@media (max-width: 1440px) {
  .page-shell {
    flex-direction: column;
  }

  .assistant-panel {
    width: 100%;
    flex-basis: auto;
    position: static;
    height: auto;
    max-height: none;
    min-height: 460px;
  }
}

@media (max-width: 900px) {
  .form-grid,
  .detail-grid,
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
