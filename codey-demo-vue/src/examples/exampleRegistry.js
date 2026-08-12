import { markRaw } from 'vue'
import ProcurementAutofillExample from './business/ProcurementAutofillExample.vue'
import OpenSystemAiAssistantExample from './business/OpenSystemAiAssistantExample.vue'
import SourceWorkspaceExample from './source-code/SourceWorkspaceExample.vue'
import VFormDesignerExample from './vform/VFormDesignerExample.vue'

// 示例入库使用静态注册表承载，后续增加新示例时只需要追加一个条目。
export const exampleRegistry = [
  {
    id: 'business-procurement-autofill',
    routePath: '/examples/business-procurement-autofill',
    title: '业务示例',
    summary: '采购申请单页面，演示根据表单上下文触发 AI 自动填写与回填。',
    category: '业务示例',
    tags: ['采购申请', '自动填写', 'AI'],
    component: markRaw(ProcurementAutofillExample),
  },
  {
    id: 'business-open-system-assistant',
    routePath: '/examples/business-open-system-assistant',
    title: '全局挂载助手',
    summary: '采购申请单页面，演示通过 openSystemAiAssistant 编程式打开 AI 助手完成自动填写与回填。业务逻辑与"业务示例"一致，仅 AI 交互方式不同。',
    category: '业务示例',
    tags: ['采购申请', '自动填写', 'openSystemAiAssistant'],
    component: markRaw(OpenSystemAiAssistantExample),
  },
  {
    id: 'source-workspace',
    routePath: '/examples/source-workspace',
    title: '源码示例',
    summary: '文件树、代码编辑器与 AI 协作工作台。',
    category: '源码入库',
    tags: ['代码', 'AI', '工作目录'],
    component: markRaw(SourceWorkspaceExample),
  },
  {
    id: 'vform-designer',
    routePath: '/examples/vform-designer',
    title: '自定义表单示例',
    summary: '基于 v-form-designer 的可视化表单设计器。',
    category: '表单入库',
    tags: ['v-form-designer', '低代码', '表单'],
    component: markRaw(VFormDesignerExample),
  },
]

export function getExampleById(exampleId) {
  return exampleRegistry.find((item) => item.id === exampleId) || null
}
