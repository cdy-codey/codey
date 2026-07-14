import { get, uploadFile } from './http'

/**
 * 业务示例接口统一收口，后续如需新增审批、报销等页面可继续扩展。
 */
export function createBusinessScenarioApi(baseUrl = '/api/business-demo') {
  return {
    getProcurementContext() {
      return get(`${baseUrl}/procurement/context`)
    },

    /**
     * 上传附件文件。
     * @param {File} file - 需要上传的文件对象
     * @returns {Promise<Object>} 上传结果元信息
     */
    uploadAttachment(file) {
      return uploadFile(`${baseUrl}/attachment/upload`, file)
    },
  }
}
