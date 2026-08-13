package com.codey.web.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 业务示例固定数据服务。
 * Demo 阶段直接返回手写样例，便于前端快速联调 AI 自动填写流程。
 */
@Service
public class BusinessScenarioService {

    /**
     * 返回采购申请演示页面所需的完整上下文。
     */
    public ProcurementFormContext getProcurementFormContext() {
        HeaderForm headerForm = new HeaderForm(
                "紧急",
                "无前期申购单",
                "2026-06-22",
                "行政服务部",
                "货物",
                "非政府采购",
                "采购一台用于日常办公的台式机，预算控制在 5000 元以内。"
        );
        DetailForm detailForm = new DetailForm(
                4800,
                4800,
                "现有办公电脑启动缓慢，影响采购部日常制单与数据整理效率。",
                "优先选择品牌机，要求 16G 内存、512G SSD，满足日常办公和报表处理。"
        );
        List<LineItem> lineItems = Arrays.asList(
                new LineItem(1, "办公类", "目录外", "电脑", 1, 0, "", "戴尔", false),
                new LineItem(2, "办公类", "目录内", "台式计算机", 1, 4800, "Intel i5/16G/512G SSD", "联想 ThinkCentre", true)
        );
        List<SelectionOption> urgencyOptions = Arrays.asList(
                new SelectionOption("紧急", "本周内需要完成采购"),
                new SelectionOption("一般", "按常规周期排期")
        );
        List<SelectionOption> orderOptions = Arrays.asList(
                new SelectionOption("无前期申购单", "当前为首次申购"),
                new SelectionOption("关联已有申购单", "与历史申购流程关联")
        );
        List<SelectionOption> departmentOptions = Arrays.asList(
                new SelectionOption("行政服务部", "负责办公资产和后勤保障"),
                new SelectionOption("采购部", "负责供应商询价与执行"),
                new SelectionOption("信息技术部", "负责设备选型与验收")
        );
        List<SelectionOption> categoryOptions = Arrays.asList(
                new SelectionOption("货物", "固定资产或低值易耗品"),
                new SelectionOption("服务", "外采实施、维护或咨询服务")
        );
        List<SelectionOption> purchaseTypeOptions = Arrays.asList(
                new SelectionOption("非政府采购", "部门内部常规采购"),
                new SelectionOption("政府采购", "需走政府采购流程")
        );
        List<SelectionOption> catalogScopeOptions = Arrays.asList(
                new SelectionOption("目录内", "属于政府采购统一采购目录内项目"),
                new SelectionOption("目录外", "不属于政府采购统一采购目录内项目")
        );
        List<FormFieldDefinition> formFields = Arrays.asList(
                new FormFieldDefinition("header.urgencyLevel", "紧急度", "enum", true, Arrays.asList("紧急", "一般")),
                new FormFieldDefinition("header.relatedOrderNo", "关联前期申购单", "enum", true, Arrays.asList("无前期申购单", "关联已有申购单")),
                new FormFieldDefinition("header.requestDate", "申请日期", "string", true, Collections.singletonList("YYYY-MM-DD")),
                new FormFieldDefinition("header.requestDepartment", "申请部门", "enum", true, Arrays.asList("行政服务部", "采购部", "信息技术部")),
                new FormFieldDefinition("header.assetType", "属性", "enum", true, Arrays.asList("货物", "服务")),
                new FormFieldDefinition("header.purchaseType", "采购类别", "enum", true, Arrays.asList("非政府采购", "政府采购")),
                new FormFieldDefinition("header.requestDescription", "采购内容描述", "string", true, Collections.emptyList()),
                new FormFieldDefinition("detail.budgetAmount", "申请金额（元）", "number", true, Collections.singletonList("0-5000")),
                new FormFieldDefinition("detail.requestReason", "需求原因说明", "string", true, Collections.emptyList()),
                new FormFieldDefinition("detail.exceedReason", "超标原因说明", "string", false, Collections.emptyList()),
                new FormFieldDefinition("items[].category", "目录类型", "enum", true, Arrays.asList("目录内", "目录外")),
                new FormFieldDefinition("items[].itemName", "标的名称", "string", true, Collections.emptyList()),
                new FormFieldDefinition("items[].quantity", "数量", "number", true, Collections.singletonList("大于等于 1")),
                new FormFieldDefinition("items[].unitPrice", "单价（元）", "number", true, Collections.singletonList("大于等于 0")),
                new FormFieldDefinition("items[].specification", "规格型号", "string", true, Collections.emptyList()),
                new FormFieldDefinition("items[].referenceBrand", "参考品牌", "string", false, Collections.emptyList())
        );
        List<AiInsightCard> aiInsightCards = Arrays.asList(
                new AiInsightCard(
                        "采购目录识别",
                        "已匹配",
                        "政府采购统一采购目录",
                        "当前推荐目录类型：目录内",
                        Arrays.asList("目录类型只允许填写“目录内”或“目录外”", "当前推荐优先选择目录内项目", "预算上限 5000 元")
                ),
                new AiInsightCard(
                        "资产配置标准检查",
                        "符合标准",
                        "标准预算 6000 元",
                        "当前预算 4800 元",
                        Arrays.asList("16G/512G SSD 满足办公标准", "预算未超标，无需追加审批说明")
                ),
                new AiInsightCard(
                        "历史标的推荐",
                        "可参考",
                        "台式计算机",
                        "联想 天逸 2025 款",
                        Arrays.asList("历史成交价 4800 元", "采购部门：信息技术部", "建议延续品牌一致性")
                )
        );
        List<String> aiSuggestions = Arrays.asList(
                "建议采购方式填写为：非政府采购",
                "目录类型建议：目录内（政府采购统一采购目录）",
                "参考目录预算：5000 元",
                "规格建议：Intel i5 / 16G / 512G SSD"
        );
        return new ProcurementFormContext(
                "purchase-request-autofill",
                "采购申请-新建",
                "根据采购表单上下文生成 AI 自动填写建议，并支持一键回填页面字段。",
                Arrays.asList("首页", "采购项目", "采购申请-新建"),
                "让 AI 结合表单上下文、预算限制和历史推荐自动补齐申请信息。",
                headerForm,
                detailForm,
                lineItems,
                formFields,
                aiInsightCards,
                aiSuggestions,
                urgencyOptions,
                orderOptions,
                departmentOptions,
                categoryOptions,
                purchaseTypeOptions,
                catalogScopeOptions,
                "请根据当前采购申请表单上下文，补齐缺失字段，并输出可直接回填页面的 JSON。"
        );
    }

    public static class ProcurementFormContext {
        private final String scenarioId;
        private final String title;
        private final String subtitle;
        private final List<String> breadcrumbs;
        private final String goal;
        private final HeaderForm header;
        private final DetailForm detail;
        private final List<LineItem> items;
        private final List<FormFieldDefinition> formFields;
        private final List<AiInsightCard> aiInsightCards;
        private final List<String> aiSuggestions;
        private final List<SelectionOption> urgencyOptions;
        private final List<SelectionOption> orderOptions;
        private final List<SelectionOption> departmentOptions;
        private final List<SelectionOption> categoryOptions;
        private final List<SelectionOption> purchaseTypeOptions;
        private final List<SelectionOption> catalogScopeOptions;
        private final String aiInstruction;

        public ProcurementFormContext(String scenarioId,
                                      String title,
                                      String subtitle,
                                      List<String> breadcrumbs,
                                      String goal,
                                      HeaderForm header,
                                      DetailForm detail,
                                      List<LineItem> items,
                                      List<FormFieldDefinition> formFields,
                                      List<AiInsightCard> aiInsightCards,
                                      List<String> aiSuggestions,
                                      List<SelectionOption> urgencyOptions,
                                      List<SelectionOption> orderOptions,
                                      List<SelectionOption> departmentOptions,
                                      List<SelectionOption> categoryOptions,
                                      List<SelectionOption> purchaseTypeOptions,
                                      List<SelectionOption> catalogScopeOptions,
                                      String aiInstruction) {
            this.scenarioId = scenarioId;
            this.title = title;
            this.subtitle = subtitle;
            this.breadcrumbs = breadcrumbs;
            this.goal = goal;
            this.header = header;
            this.detail = detail;
            this.items = items;
            this.formFields = formFields;
            this.aiInsightCards = aiInsightCards;
            this.aiSuggestions = aiSuggestions;
            this.urgencyOptions = urgencyOptions;
            this.orderOptions = orderOptions;
            this.departmentOptions = departmentOptions;
            this.categoryOptions = categoryOptions;
            this.purchaseTypeOptions = purchaseTypeOptions;
            this.catalogScopeOptions = catalogScopeOptions;
            this.aiInstruction = aiInstruction;
        }

        public String getScenarioId() {
            return scenarioId;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public List<String> getBreadcrumbs() {
            return breadcrumbs;
        }

        public String getGoal() {
            return goal;
        }

        public HeaderForm getHeader() {
            return header;
        }

        public DetailForm getDetail() {
            return detail;
        }

        public List<LineItem> getItems() {
            return items;
        }

        public List<FormFieldDefinition> getFormFields() {
            return formFields;
        }

        public List<AiInsightCard> getAiInsightCards() {
            return aiInsightCards;
        }

        public List<String> getAiSuggestions() {
            return aiSuggestions;
        }

        public List<SelectionOption> getUrgencyOptions() {
            return urgencyOptions;
        }

        public List<SelectionOption> getOrderOptions() {
            return orderOptions;
        }

        public List<SelectionOption> getDepartmentOptions() {
            return departmentOptions;
        }

        public List<SelectionOption> getCategoryOptions() {
            return categoryOptions;
        }

        public List<SelectionOption> getPurchaseTypeOptions() {
            return purchaseTypeOptions;
        }

        public List<SelectionOption> getCatalogScopeOptions() {
            return catalogScopeOptions;
        }

        public String getAiInstruction() {
            return aiInstruction;
        }
    }

    public static class HeaderForm {
        private final String urgencyLevel;
        private final String relatedOrderNo;
        private final String requestDate;
        private final String requestDepartment;
        private final String assetType;
        private final String purchaseType;
        private final String requestDescription;

        public HeaderForm(String urgencyLevel,
                          String relatedOrderNo,
                          String requestDate,
                          String requestDepartment,
                          String assetType,
                          String purchaseType,
                          String requestDescription) {
            this.urgencyLevel = urgencyLevel;
            this.relatedOrderNo = relatedOrderNo;
            this.requestDate = requestDate;
            this.requestDepartment = requestDepartment;
            this.assetType = assetType;
            this.purchaseType = purchaseType;
            this.requestDescription = requestDescription;
        }

        public String getUrgencyLevel() {
            return urgencyLevel;
        }

        public String getRelatedOrderNo() {
            return relatedOrderNo;
        }

        public String getRequestDate() {
            return requestDate;
        }

        public String getRequestDepartment() {
            return requestDepartment;
        }

        public String getAssetType() {
            return assetType;
        }

        public String getPurchaseType() {
            return purchaseType;
        }

        public String getRequestDescription() {
            return requestDescription;
        }
    }

    public static class DetailForm {
        private final Integer budgetAmount;
        private final Integer actualAmount;
        private final String requestReason;
        private final String exceedReason;

        public DetailForm(Integer budgetAmount, Integer actualAmount, String requestReason, String exceedReason) {
            this.budgetAmount = budgetAmount;
            this.actualAmount = actualAmount;
            this.requestReason = requestReason;
            this.exceedReason = exceedReason;
        }

        public Integer getBudgetAmount() {
            return budgetAmount;
        }

        public Integer getActualAmount() {
            return actualAmount;
        }

        public String getRequestReason() {
            return requestReason;
        }

        public String getExceedReason() {
            return exceedReason;
        }
    }

    public static class LineItem {
        private final Integer rowNo;
        private final String demandType;
        private final String category;
        private final String itemName;
        private final Integer quantity;
        private final Integer unitPrice;
        private final String specification;
        private final String referenceBrand;
        private final boolean recommended;

        public LineItem(Integer rowNo,
                        String demandType,
                        String category,
                        String itemName,
                        Integer quantity,
                        Integer unitPrice,
                        String specification,
                        String referenceBrand,
                        boolean recommended) {
            this.rowNo = rowNo;
            this.demandType = demandType;
            this.category = category;
            this.itemName = itemName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.specification = specification;
            this.referenceBrand = referenceBrand;
            this.recommended = recommended;
        }

        public Integer getRowNo() {
            return rowNo;
        }

        public String getDemandType() {
            return demandType;
        }

        public String getCategory() {
            return category;
        }

        public String getItemName() {
            return itemName;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public Integer getUnitPrice() {
            return unitPrice;
        }

        public String getSpecification() {
            return specification;
        }

        public String getReferenceBrand() {
            return referenceBrand;
        }

        public boolean isRecommended() {
            return recommended;
        }
    }

    public static class FormFieldDefinition {
        private final String fieldKey;
        private final String label;
        private final String fieldType;
        private final boolean required;
        private final List<String> constraints;

        public FormFieldDefinition(String fieldKey, String label, String fieldType, boolean required, List<String> constraints) {
            this.fieldKey = fieldKey;
            this.label = label;
            this.fieldType = fieldType;
            this.required = required;
            this.constraints = constraints;
        }

        public String getFieldKey() {
            return fieldKey;
        }

        public String getLabel() {
            return label;
        }

        public String getFieldType() {
            return fieldType;
        }

        public boolean isRequired() {
            return required;
        }

        public List<String> getConstraints() {
            return constraints;
        }
    }

    public static class AiInsightCard {
        private final String title;
        private final String status;
        private final String primaryText;
        private final String secondaryText;
        private final List<String> bulletPoints;

        public AiInsightCard(String title, String status, String primaryText, String secondaryText, List<String> bulletPoints) {
            this.title = title;
            this.status = status;
            this.primaryText = primaryText;
            this.secondaryText = secondaryText;
            this.bulletPoints = bulletPoints;
        }

        public String getTitle() {
            return title;
        }

        public String getStatus() {
            return status;
        }

        public String getPrimaryText() {
            return primaryText;
        }

        public String getSecondaryText() {
            return secondaryText;
        }

        public List<String> getBulletPoints() {
            return bulletPoints;
        }
    }

    public static class SelectionOption {
        private final String value;
        private final String description;

        public SelectionOption(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }
    }
}
