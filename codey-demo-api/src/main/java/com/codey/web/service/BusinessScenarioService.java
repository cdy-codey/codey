package com.codey.web.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 业务示例固定数据服务。
 * Demo 阶段直接返回手写样例，便于前端快速联调 AI 自动填写流程。
 * 数据格式与 BizRequire / BizRequireTarget / BizBusinessEntry 实体保持一致。
 */
@Service
public class BusinessScenarioService {

    /**
     * 返回采购需求申请演示页面所需的完整上下文。
     */
    public ProcurementFormContext getProcurementFormContext() {
        // 采购需求基础信息（对应 BizRequire）
        BasicInfo basicInfo = new BasicInfo(
                "2026年度办公设备采购需求", // requireTitle
                "XQ-2026-0001",              // requireNo
                "goods",                     // requireAttribute: 货物类
                "非政府采购",                 // purchaseCategory
                "一般",                       // emergency
                "2026-08-15",                // estimatedStartTime
                "行政服务部",                 // subscribeDepartName
                "行政服务部,信息技术部",       // requireDepartNames
                "张三",                       // operatorName
                "李四",                       // applyName
                "采购一批办公台式计算机及外设，用于替换老旧设备，满足部门日常办公需求。", // purchaseContent
                "1. 供应商需提供原厂授权及售后服务承诺函；2. 质保期不少于3年；3. 交货周期不超过30个自然日。", // busService
                "0",  // isSingleSource: 否
                "0",  // isImportPurchase: 否
                "0",  // isMajor: 否
                "1",  // isInformation: 是
                "0",  // isEntrust: 否
                "0",  // isSecret: 否
                "1",  // isSmb: 是
                "1",  // isBeginningBudget: 是
                "目录内",       // requireCatalog
                "一般公共预算",  // budgetType
                "财政拨款",     // fundsSource
                "公用经费",     // costSubject
                "pay",         // fundFlow: 支出
                "分散采购",     // organizeForm
                "非政府采购",   // organizeFormExtra
                "询价",        // purchaseWay
                "自行组织",     // processWay
                "采购部",       // centralizedDepartName
                "采购部",       // purchaseDepartName
                "2026年度办公设备采购预算", // budgetName
                "YS-2026-BG-001",        // budgetNo
                new BigDecimal("12800"),  // purchaseAmount
                new BigDecimal("0"),      // confirmAmount
                new BigDecimal("15000"),  // requireBudgetAmount
                "2026-08-15",             // planBeginTime
                "2026-11-30",             // planFinishTime
                "day",                    // businessDocType: 日常零星采购
                "",                       // fromBizName
                "0",                      // isOverYear: 否
                "0",                      // isAddition: 否
                "0",                      // isChangeStructure: 否
                "采购,设备,办公",            // containContent
                "本批次采购资金来源于年度公用经费预算，已纳入2026年度部门预算。", // budgetDesc
                ""                        // budgetRemark
        );

        // 采购标的明细（对应 BizRequireTarget）
        List<TargetItem> targetList = Arrays.asList(
                new TargetItem(1, "台式计算机", "信息化设备", "固定资产", 10.0, new BigDecimal("4800"), "台",
                        new BigDecimal("48000"), "Intel i5-14500 / 16G DDR5 / 512G SSD / 23.8寸显示器",
                        "行政楼3F办公室", "2026-09-01", "联想 ThinkCentre、戴尔 OptiPlex、惠普 Elite", ""),
                new TargetItem(2, "激光打印机", "信息化设备", "固定资产", 2.0, new BigDecimal("3500"), "台",
                        new BigDecimal("7000"), "A4黑白激光/自动双面/网络打印/30页/分钟以上",
                        "行政楼3F办公室", "2026-09-01", "惠普 LaserJet、兄弟 HL、佳能 LBP", ""),
                new TargetItem(3, "碎纸机", "办公设备", "低值易耗品", 3.0, new BigDecimal("800"), "台",
                        new BigDecimal("2400"), "段状/12张/次/连续工作30分钟",
                        "各部门办公室", "2026-09-15", "科密、得力、三木", "")
        );

        // 采购商务条款（对应 BizBusinessEntry）
        List<BusinessEntryItem> businessEntryList = Arrays.asList(
                new BusinessEntryItem(1, "通用", "质量保证", "供应商须提供原厂质保服务，质保期不少于3年，质保期内免费上门维修。", "满足", "《政府采购货物和服务招标投标管理办法》"),
                new BusinessEntryItem(2, "通用", "售后服务", "供应商需在本地设有售后服务网点，响应时间不超过4小时，48小时内解决故障。", "满足", "《电子电器产品售后服务管理办法》"),
                new BusinessEntryItem(3, "通用", "付款方式", "验收合格后30个工作日内支付合同总价的95%，质保期满后支付剩余5%。", "满足", ""),
                new BusinessEntryItem(4, "信息化设备", "信息安全", "设备须通过国家信息安全等级保护认证，不得预装未经授权的软件。", "满足", "《网络安全法》《信息安全等级保护管理办法》"),
                new BusinessEntryItem(5, "信息化设备", "兼容性要求", "所供设备须与现有办公网络及信息系统兼容，操作系统须为正版授权。", "满足", "")
        );

        // 下拉选项
        List<SelectionOption> requireAttributeOptions = Arrays.asList(
                new SelectionOption("goods", "货物类"),
                new SelectionOption("build", "工程建设类"),
                new SelectionOption("service", "服务类")
        );
        List<SelectionOption> purchaseCategoryOptions = Arrays.asList(
                new SelectionOption("政府采购", "政府采购"),
                new SelectionOption("非政府采购", "非政府采购")
        );
        List<SelectionOption> emergencyOptions = Arrays.asList(
                new SelectionOption("紧急", "本周内需要完成采购"),
                new SelectionOption("一般", "按常规周期排期")
        );
        List<SelectionOption> requireCatalogOptions = Arrays.asList(
                new SelectionOption("目录内", "属于政府采购统一采购目录内项目"),
                new SelectionOption("目录外", "不属于政府采购统一采购目录内项目")
        );
        List<SelectionOption> budgetTypeOptions = Arrays.asList(
                new SelectionOption("一般公共预算", "一般公共预算"),
                new SelectionOption("政府性基金预算", "政府性基金预算"),
                new SelectionOption("国有资本经营预算", "国有资本经营预算")
        );
        List<SelectionOption> fundsSourceOptions = Arrays.asList(
                new SelectionOption("财政拨款", "财政拨款"),
                new SelectionOption("事业收入", "事业收入"),
                new SelectionOption("其他资金", "其他资金")
        );
        List<SelectionOption> fundFlowOptions = Arrays.asList(
                new SelectionOption("pay", "支出"),
                new SelectionOption("income", "收入")
        );
        List<SelectionOption> organizeFormOptions = Arrays.asList(
                new SelectionOption("集中采购", "集中采购"),
                new SelectionOption("分散采购", "分散采购")
        );
        List<SelectionOption> organizeFormExtraOptions = Arrays.asList(
                new SelectionOption("政府采购", "政府采购"),
                new SelectionOption("非政府采购", "非政府采购")
        );
        List<SelectionOption> purchaseWayOptions = Arrays.asList(
                new SelectionOption("公开招标", "公开招标"),
                new SelectionOption("邀请招标", "邀请招标"),
                new SelectionOption("竞争性谈判", "竞争性谈判"),
                new SelectionOption("询价", "询价"),
                new SelectionOption("单一来源", "单一来源")
        );
        List<SelectionOption> processWayOptions = Arrays.asList(
                new SelectionOption("委托代理", "委托代理机构执行"),
                new SelectionOption("自行组织", "自行组织采购")
        );
        List<SelectionOption> businessDocTypeOptions = Arrays.asList(
                new SelectionOption("year", "年度计划"),
                new SelectionOption("day", "日常零星采购")
        );
        List<SelectionOption> ynOptions = Arrays.asList(
                new SelectionOption("1", "是"),
                new SelectionOption("0", "否")
        );
        List<SelectionOption> isSmbOptions = Arrays.asList(
                new SelectionOption("1", "是"),
                new SelectionOption("0", "否"),
                new SelectionOption("2", "不适用")
        );
        List<SelectionOption> targetTypeOptions = Arrays.asList(
                new SelectionOption("信息化设备", "信息化设备"),
                new SelectionOption("办公设备", "办公设备"),
                new SelectionOption("家具用具", "家具用具"),
                new SelectionOption("专业设备", "专业设备")
        );
        List<SelectionOption> purchaseTypeOptions2 = Arrays.asList(
                new SelectionOption("固定资产", "固定资产"),
                new SelectionOption("低值易耗品", "低值易耗品"),
                new SelectionOption("无形资产", "无形资产")
        );

        List<FormFieldDefinition> formFields = Arrays.asList(
                new FormFieldDefinition("basicInfo.requireTitle", "需求标题", "string", true, Collections.emptyList()),
                new FormFieldDefinition("basicInfo.requireAttribute", "需求属性", "enum", true, Arrays.asList("goods", "build", "service")),
                new FormFieldDefinition("basicInfo.purchaseCategory", "采购类别", "enum", true, Arrays.asList("政府采购", "非政府采购")),
                new FormFieldDefinition("basicInfo.emergency", "需求紧急度", "enum", true, Arrays.asList("紧急", "一般")),
                new FormFieldDefinition("basicInfo.purchaseContent", "采购内容描述", "string", true, Collections.emptyList()),
                new FormFieldDefinition("basicInfo.busService", "商务服务要求", "string", false, Collections.emptyList()),
                new FormFieldDefinition("basicInfo.purchaseAmount", "申购金额（元）", "number", true, Collections.singletonList("0-15000")),
                new FormFieldDefinition("targetList[].targetName", "标的名称", "string", true, Collections.emptyList()),
                new FormFieldDefinition("targetList[].num", "数量", "number", true, Collections.singletonList(">=1")),
                new FormFieldDefinition("targetList[].unitPrice", "单价", "number", true, Collections.singletonList(">=0")),
                new FormFieldDefinition("targetList[].targetContent", "规格参数", "string", true, Collections.emptyList()),
                new FormFieldDefinition("targetList[].referenceListStr", "参考品牌", "string", false, Collections.emptyList()),
                new FormFieldDefinition("businessEntryList[].businessItem", "商务条目", "string", true, Collections.emptyList()),
                new FormFieldDefinition("businessEntryList[].businessRequirement", "商务要求", "string", true, Collections.emptyList())
        );

        List<AiInsightCard> aiInsightCards = Arrays.asList(
                new AiInsightCard(
                        "采购目录识别",
                        "已匹配",
                        "政府采购统一采购目录",
                        "当前推荐目录类型：目录内",
                        Arrays.asList("目录类型只允许填写'目录内'或'目录外'", "当前推荐优先选择目录内项目", "预算上限 15000 元")
                ),
                new AiInsightCard(
                        "资产配置标准检查",
                        "符合标准",
                        "台式计算机标准预算 5000 元/台",
                        "当前预算 4800 元/台",
                        Arrays.asList("16G/512G SSD 满足办公标准", "预算未超标，无需追加审批说明")
                ),
                new AiInsightCard(
                        "历史标的推荐",
                        "可参考",
                        "台式计算机",
                        "联想 ThinkCentre M75q",
                        Arrays.asList("历史成交价 4800 元", "采购部门：信息技术部", "建议延续品牌一致性")
                )
        );
        List<String> aiSuggestions = Arrays.asList(
                "建议采购方式填写为：询价",
                "目录类型建议：目录内（政府采购统一采购目录）",
                "台式计算机建议预算：5000 元/台",
                "规格建议：Intel i5 / 16G / 512G SSD"
        );

        return new ProcurementFormContext(
                "purchase-require",
                "采购需求申请",
                "根据采购需求表单上下文让 AI 自动填写申请信息，并支持一键回填页面字段。",
                Arrays.asList("首页", "采购管理", "采购需求申请"),
                "让 AI 结合表单上下文、预算限制和历史推荐自动补齐采购需求申请信息。",
                basicInfo,
                targetList,
                businessEntryList,
                formFields,
                aiInsightCards,
                aiSuggestions,
                requireAttributeOptions,
                purchaseCategoryOptions,
                emergencyOptions,
                requireCatalogOptions,
                budgetTypeOptions,
                fundsSourceOptions,
                fundFlowOptions,
                organizeFormOptions,
                organizeFormExtraOptions,
                purchaseWayOptions,
                processWayOptions,
                businessDocTypeOptions,
                ynOptions,
                isSmbOptions,
                targetTypeOptions,
                purchaseTypeOptions2,
                "请根据当前采购需求申请表单上下文，补齐缺失字段，并输出可直接回填页面的 JSON。"
        );
    }

    // ============ 上下文对象 ============

    public static class ProcurementFormContext {
        private final String scenarioId;
        private final String title;
        private final String subtitle;
        private final List<String> breadcrumbs;
        private final String goal;
        private final BasicInfo basicInfo;
        private final List<TargetItem> targetList;
        private final List<BusinessEntryItem> businessEntryList;
        private final List<FormFieldDefinition> formFields;
        private final List<AiInsightCard> aiInsightCards;
        private final List<String> aiSuggestions;
        private final List<SelectionOption> requireAttributeOptions;
        private final List<SelectionOption> purchaseCategoryOptions;
        private final List<SelectionOption> emergencyOptions;
        private final List<SelectionOption> requireCatalogOptions;
        private final List<SelectionOption> budgetTypeOptions;
        private final List<SelectionOption> fundsSourceOptions;
        private final List<SelectionOption> fundFlowOptions;
        private final List<SelectionOption> organizeFormOptions;
        private final List<SelectionOption> organizeFormExtraOptions;
        private final List<SelectionOption> purchaseWayOptions;
        private final List<SelectionOption> processWayOptions;
        private final List<SelectionOption> businessDocTypeOptions;
        private final List<SelectionOption> ynOptions;
        private final List<SelectionOption> isSmbOptions;
        private final List<SelectionOption> targetTypeOptions;
        private final List<SelectionOption> purchaseTypeOptions;
        private final String aiInstruction;

        public ProcurementFormContext(String scenarioId, String title, String subtitle,
                                      List<String> breadcrumbs, String goal,
                                      BasicInfo basicInfo, List<TargetItem> targetList,
                                      List<BusinessEntryItem> businessEntryList,
                                      List<FormFieldDefinition> formFields,
                                      List<AiInsightCard> aiInsightCards, List<String> aiSuggestions,
                                      List<SelectionOption> requireAttributeOptions,
                                      List<SelectionOption> purchaseCategoryOptions,
                                      List<SelectionOption> emergencyOptions,
                                      List<SelectionOption> requireCatalogOptions,
                                      List<SelectionOption> budgetTypeOptions,
                                      List<SelectionOption> fundsSourceOptions,
                                      List<SelectionOption> fundFlowOptions,
                                      List<SelectionOption> organizeFormOptions,
                                      List<SelectionOption> organizeFormExtraOptions,
                                      List<SelectionOption> purchaseWayOptions,
                                      List<SelectionOption> processWayOptions,
                                      List<SelectionOption> businessDocTypeOptions,
                                      List<SelectionOption> ynOptions,
                                      List<SelectionOption> isSmbOptions,
                                      List<SelectionOption> targetTypeOptions,
                                      List<SelectionOption> purchaseTypeOptions,
                                      String aiInstruction) {
            this.scenarioId = scenarioId;
            this.title = title;
            this.subtitle = subtitle;
            this.breadcrumbs = breadcrumbs;
            this.goal = goal;
            this.basicInfo = basicInfo;
            this.targetList = targetList;
            this.businessEntryList = businessEntryList;
            this.formFields = formFields;
            this.aiInsightCards = aiInsightCards;
            this.aiSuggestions = aiSuggestions;
            this.requireAttributeOptions = requireAttributeOptions;
            this.purchaseCategoryOptions = purchaseCategoryOptions;
            this.emergencyOptions = emergencyOptions;
            this.requireCatalogOptions = requireCatalogOptions;
            this.budgetTypeOptions = budgetTypeOptions;
            this.fundsSourceOptions = fundsSourceOptions;
            this.fundFlowOptions = fundFlowOptions;
            this.organizeFormOptions = organizeFormOptions;
            this.organizeFormExtraOptions = organizeFormExtraOptions;
            this.purchaseWayOptions = purchaseWayOptions;
            this.processWayOptions = processWayOptions;
            this.businessDocTypeOptions = businessDocTypeOptions;
            this.ynOptions = ynOptions;
            this.isSmbOptions = isSmbOptions;
            this.targetTypeOptions = targetTypeOptions;
            this.purchaseTypeOptions = purchaseTypeOptions;
            this.aiInstruction = aiInstruction;
        }

        // Getters
        public String getScenarioId() { return scenarioId; }
        public String getTitle() { return title; }
        public String getSubtitle() { return subtitle; }
        public List<String> getBreadcrumbs() { return breadcrumbs; }
        public String getGoal() { return goal; }
        public BasicInfo getBasicInfo() { return basicInfo; }
        public List<TargetItem> getTargetList() { return targetList; }
        public List<BusinessEntryItem> getBusinessEntryList() { return businessEntryList; }
        public List<FormFieldDefinition> getFormFields() { return formFields; }
        public List<AiInsightCard> getAiInsightCards() { return aiInsightCards; }
        public List<String> getAiSuggestions() { return aiSuggestions; }
        public List<SelectionOption> getRequireAttributeOptions() { return requireAttributeOptions; }
        public List<SelectionOption> getPurchaseCategoryOptions() { return purchaseCategoryOptions; }
        public List<SelectionOption> getEmergencyOptions() { return emergencyOptions; }
        public List<SelectionOption> getRequireCatalogOptions() { return requireCatalogOptions; }
        public List<SelectionOption> getBudgetTypeOptions() { return budgetTypeOptions; }
        public List<SelectionOption> getFundsSourceOptions() { return fundsSourceOptions; }
        public List<SelectionOption> getFundFlowOptions() { return fundFlowOptions; }
        public List<SelectionOption> getOrganizeFormOptions() { return organizeFormOptions; }
        public List<SelectionOption> getOrganizeFormExtraOptions() { return organizeFormExtraOptions; }
        public List<SelectionOption> getPurchaseWayOptions() { return purchaseWayOptions; }
        public List<SelectionOption> getProcessWayOptions() { return processWayOptions; }
        public List<SelectionOption> getBusinessDocTypeOptions() { return businessDocTypeOptions; }
        public List<SelectionOption> getYnOptions() { return ynOptions; }
        public List<SelectionOption> getIsSmbOptions() { return isSmbOptions; }
        public List<SelectionOption> getTargetTypeOptions() { return targetTypeOptions; }
        public List<SelectionOption> getPurchaseTypeOptions() { return purchaseTypeOptions; }
        public String getAiInstruction() { return aiInstruction; }
    }

    // ============ 基础信息（对应 BizRequire） ============

    public static class BasicInfo {
        private final String requireTitle;
        private final String requireNo;
        private final String requireAttribute;
        private final String purchaseCategory;
        private final String emergency;
        private final String estimatedStartTime;
        private final String subscribeDepartName;
        private final String requireDepartNames;
        private final String operatorName;
        private final String applyName;
        private final String purchaseContent;
        private final String busService;
        private final String isSingleSource;
        private final String isImportPurchase;
        private final String isMajor;
        private final String isInformation;
        private final String isEntrust;
        private final String isSecret;
        private final String isSmb;
        private final String isBeginningBudget;
        private final String requireCatalog;
        private final String budgetType;
        private final String fundsSource;
        private final String costSubject;
        private final String fundFlow;
        private final String organizeForm;
        private final String organizeFormExtra;
        private final String purchaseWay;
        private final String processWay;
        private final String centralizedDepartName;
        private final String purchaseDepartName;
        private final String budgetName;
        private final String budgetNo;
        private final BigDecimal purchaseAmount;
        private final BigDecimal confirmAmount;
        private final BigDecimal requireBudgetAmount;
        private final String planBeginTime;
        private final String planFinishTime;
        private final String businessDocType;
        private final String fromBizName;
        private final String isOverYear;
        private final String isAddition;
        private final String isChangeStructure;
        private final String containContent;
        private final String budgetDesc;
        private final String budgetRemark;

        public BasicInfo(String requireTitle, String requireNo, String requireAttribute,
                         String purchaseCategory, String emergency, String estimatedStartTime,
                         String subscribeDepartName, String requireDepartNames,
                         String operatorName, String applyName,
                         String purchaseContent, String busService,
                         String isSingleSource, String isImportPurchase, String isMajor,
                         String isInformation, String isEntrust, String isSecret,
                         String isSmb, String isBeginningBudget,
                         String requireCatalog, String budgetType, String fundsSource,
                         String costSubject, String fundFlow,
                         String organizeForm, String organizeFormExtra,
                         String purchaseWay, String processWay,
                         String centralizedDepartName, String purchaseDepartName,
                         String budgetName, String budgetNo,
                         BigDecimal purchaseAmount, BigDecimal confirmAmount,
                         BigDecimal requireBudgetAmount,
                         String planBeginTime, String planFinishTime,
                         String businessDocType, String fromBizName,
                         String isOverYear, String isAddition, String isChangeStructure,
                         String containContent, String budgetDesc, String budgetRemark) {
            this.requireTitle = requireTitle;
            this.requireNo = requireNo;
            this.requireAttribute = requireAttribute;
            this.purchaseCategory = purchaseCategory;
            this.emergency = emergency;
            this.estimatedStartTime = estimatedStartTime;
            this.subscribeDepartName = subscribeDepartName;
            this.requireDepartNames = requireDepartNames;
            this.operatorName = operatorName;
            this.applyName = applyName;
            this.purchaseContent = purchaseContent;
            this.busService = busService;
            this.isSingleSource = isSingleSource;
            this.isImportPurchase = isImportPurchase;
            this.isMajor = isMajor;
            this.isInformation = isInformation;
            this.isEntrust = isEntrust;
            this.isSecret = isSecret;
            this.isSmb = isSmb;
            this.isBeginningBudget = isBeginningBudget;
            this.requireCatalog = requireCatalog;
            this.budgetType = budgetType;
            this.fundsSource = fundsSource;
            this.costSubject = costSubject;
            this.fundFlow = fundFlow;
            this.organizeForm = organizeForm;
            this.organizeFormExtra = organizeFormExtra;
            this.purchaseWay = purchaseWay;
            this.processWay = processWay;
            this.centralizedDepartName = centralizedDepartName;
            this.purchaseDepartName = purchaseDepartName;
            this.budgetName = budgetName;
            this.budgetNo = budgetNo;
            this.purchaseAmount = purchaseAmount;
            this.confirmAmount = confirmAmount;
            this.requireBudgetAmount = requireBudgetAmount;
            this.planBeginTime = planBeginTime;
            this.planFinishTime = planFinishTime;
            this.businessDocType = businessDocType;
            this.fromBizName = fromBizName;
            this.isOverYear = isOverYear;
            this.isAddition = isAddition;
            this.isChangeStructure = isChangeStructure;
            this.containContent = containContent;
            this.budgetDesc = budgetDesc;
            this.budgetRemark = budgetRemark;
        }

        // Getters
        public String getRequireTitle() { return requireTitle; }
        public String getRequireNo() { return requireNo; }
        public String getRequireAttribute() { return requireAttribute; }
        public String getPurchaseCategory() { return purchaseCategory; }
        public String getEmergency() { return emergency; }
        public String getEstimatedStartTime() { return estimatedStartTime; }
        public String getSubscribeDepartName() { return subscribeDepartName; }
        public String getRequireDepartNames() { return requireDepartNames; }
        public String getOperatorName() { return operatorName; }
        public String getApplyName() { return applyName; }
        public String getPurchaseContent() { return purchaseContent; }
        public String getBusService() { return busService; }
        public String getIsSingleSource() { return isSingleSource; }
        public String getIsImportPurchase() { return isImportPurchase; }
        public String getIsMajor() { return isMajor; }
        public String getIsInformation() { return isInformation; }
        public String getIsEntrust() { return isEntrust; }
        public String getIsSecret() { return isSecret; }
        public String getIsSmb() { return isSmb; }
        public String getIsBeginningBudget() { return isBeginningBudget; }
        public String getRequireCatalog() { return requireCatalog; }
        public String getBudgetType() { return budgetType; }
        public String getFundsSource() { return fundsSource; }
        public String getCostSubject() { return costSubject; }
        public String getFundFlow() { return fundFlow; }
        public String getOrganizeForm() { return organizeForm; }
        public String getOrganizeFormExtra() { return organizeFormExtra; }
        public String getPurchaseWay() { return purchaseWay; }
        public String getProcessWay() { return processWay; }
        public String getCentralizedDepartName() { return centralizedDepartName; }
        public String getPurchaseDepartName() { return purchaseDepartName; }
        public String getBudgetName() { return budgetName; }
        public String getBudgetNo() { return budgetNo; }
        public BigDecimal getPurchaseAmount() { return purchaseAmount; }
        public BigDecimal getConfirmAmount() { return confirmAmount; }
        public BigDecimal getRequireBudgetAmount() { return requireBudgetAmount; }
        public String getPlanBeginTime() { return planBeginTime; }
        public String getPlanFinishTime() { return planFinishTime; }
        public String getBusinessDocType() { return businessDocType; }
        public String getFromBizName() { return fromBizName; }
        public String getIsOverYear() { return isOverYear; }
        public String getIsAddition() { return isAddition; }
        public String getIsChangeStructure() { return isChangeStructure; }
        public String getContainContent() { return containContent; }
        public String getBudgetDesc() { return budgetDesc; }
        public String getBudgetRemark() { return budgetRemark; }
    }

    // ============ 标的明细（对应 BizRequireTarget） ============

    public static class TargetItem {
        private final Integer rowNo;
        private final String targetName;
        private final String targetTypeName;
        private final String purchaseTypeName;
        private final Double num;
        private final BigDecimal unitPrice;
        private final String unit;
        private final BigDecimal targetPrice;
        private final String targetContent;
        private final String storageArea;
        private final String expectPurchaseTime;
        private final String referenceListStr;
        private final String remark;

        public TargetItem(Integer rowNo, String targetName, String targetTypeName,
                          String purchaseTypeName, Double num, BigDecimal unitPrice,
                          String unit, BigDecimal targetPrice, String targetContent,
                          String storageArea, String expectPurchaseTime,
                          String referenceListStr, String remark) {
            this.rowNo = rowNo;
            this.targetName = targetName;
            this.targetTypeName = targetTypeName;
            this.purchaseTypeName = purchaseTypeName;
            this.num = num;
            this.unitPrice = unitPrice;
            this.unit = unit;
            this.targetPrice = targetPrice;
            this.targetContent = targetContent;
            this.storageArea = storageArea;
            this.expectPurchaseTime = expectPurchaseTime;
            this.referenceListStr = referenceListStr;
            this.remark = remark;
        }

        public Integer getRowNo() { return rowNo; }
        public String getTargetName() { return targetName; }
        public String getTargetTypeName() { return targetTypeName; }
        public String getPurchaseTypeName() { return purchaseTypeName; }
        public Double getNum() { return num; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public String getUnit() { return unit; }
        public BigDecimal getTargetPrice() { return targetPrice; }
        public String getTargetContent() { return targetContent; }
        public String getStorageArea() { return storageArea; }
        public String getExpectPurchaseTime() { return expectPurchaseTime; }
        public String getReferenceListStr() { return referenceListStr; }
        public String getRemark() { return remark; }
    }

    // ============ 商务条款（对应 BizBusinessEntry） ============

    public static class BusinessEntryItem {
        private final Integer rowNo;
        private final String entriesCategory;
        private final String businessItem;
        private final String businessRequirement;
        private final String businessRequirementResult;
        private final String standardBasis;

        public BusinessEntryItem(Integer rowNo, String entriesCategory, String businessItem,
                                 String businessRequirement, String businessRequirementResult,
                                 String standardBasis) {
            this.rowNo = rowNo;
            this.entriesCategory = entriesCategory;
            this.businessItem = businessItem;
            this.businessRequirement = businessRequirement;
            this.businessRequirementResult = businessRequirementResult;
            this.standardBasis = standardBasis;
        }

        public Integer getRowNo() { return rowNo; }
        public String getEntriesCategory() { return entriesCategory; }
        public String getBusinessItem() { return businessItem; }
        public String getBusinessRequirement() { return businessRequirement; }
        public String getBusinessRequirementResult() { return businessRequirementResult; }
        public String getStandardBasis() { return standardBasis; }
    }

    // ============ 通用类型 ============

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

        public String getFieldKey() { return fieldKey; }
        public String getLabel() { return label; }
        public String getFieldType() { return fieldType; }
        public boolean isRequired() { return required; }
        public List<String> getConstraints() { return constraints; }
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

        public String getTitle() { return title; }
        public String getStatus() { return status; }
        public String getPrimaryText() { return primaryText; }
        public String getSecondaryText() { return secondaryText; }
        public List<String> getBulletPoints() { return bulletPoints; }
    }

    public static class SelectionOption {
        private final String value;
        private final String description;

        public SelectionOption(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() { return value; }
        public String getDescription() { return description; }
    }
}
