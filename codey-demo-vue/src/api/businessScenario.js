import { get } from './http'

/**
 * 业务示例接口统一收口，后续如需新增审批、报销等页面可继续扩展。
 */
export function createBusinessScenarioApi(baseUrl = '/api/business-demo') {
  return {
    getProcurementContext() {
      return get(`${baseUrl}/procurement/context`)
    },
  }
}
