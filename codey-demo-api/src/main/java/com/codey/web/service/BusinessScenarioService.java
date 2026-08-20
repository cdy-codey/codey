package com.codey.web.service;

import com.codey.web.entity.BizBusinessEntry;
import com.codey.web.entity.BizRequire;
import com.codey.web.entity.BizRequireTarget;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.Arrays;
import java.util.List;

/**
 * 业务示例固定数据服务。
 * Demo 阶段直接返回手写样例，便于前端快速联调 AI 自动填写流程。
 * 上下文直接构建为真实的 BizRequire 实体（含 BizRequireTarget / BizBusinessEntry 嵌套），
 * 与前端 formModel、AI 表单 schema 的字段结构完全一致，避免手写 DTO 与实体字段漂移。
 */
@Service
public class BusinessScenarioService {

    /**
     * 返回采购需求申请演示页面所需的完整上下文（真实的 BizRequire 实体）。
     */
    public BizRequire getProcurementFormContext() {
        // 采购标的明细（真实实体 BizRequireTarget，字段与 AI 表单 schema 一致）
        List<BizRequireTarget> targetList = Arrays.asList(
                new BizRequireTarget()
                        .setTargetName("台式计算机")
                        .setTargetTypeName("信息化设备")
                        .setTargetTypeCode("A02010104")
                        .setPurchaseTypeName("固定资产")
                        .setNum(10.0)
                        .setUnitPrice(new BigDecimal("4800"))
                        .setUnit("台")
                        .setTargetPrice(new BigDecimal("48000"))
                        .setTargetContent("Intel i5-14500 / 16G DDR5 / 512G SSD / 23.8寸显示器")
                        .setStorageArea("行政楼3F办公室")
                        .setExpectPurchaseTime(Date.valueOf("2026-09-01"))
                        .setReferenceListStr("联想 ThinkCentre、戴尔 OptiPlex、惠普 Elite")
                        .setRemark(""),
                new BizRequireTarget()
                        .setTargetName("激光打印机")
                        .setTargetTypeName("信息化设备")
                        .setTargetTypeCode("A02021003")
                        .setPurchaseTypeName("固定资产")
                        .setNum(2.0)
                        .setUnitPrice(new BigDecimal("3500"))
                        .setUnit("台")
                        .setTargetPrice(new BigDecimal("7000"))
                        .setTargetContent("A4黑白激光/自动双面/网络打印/30页/分钟以上")
                        .setStorageArea("行政楼3F办公室")
                        .setExpectPurchaseTime(Date.valueOf("2026-09-01"))
                        .setReferenceListStr("惠普 LaserJet、兄弟 HL、佳能 LBP")
                        .setRemark(""),
                new BizRequireTarget()
                        .setTargetName("碎纸机")
                        .setTargetTypeName("办公设备")
                        .setTargetTypeCode("A02021006")
                        .setPurchaseTypeName("低值易耗品")
                        .setNum(3.0)
                        .setUnitPrice(new BigDecimal("800"))
                        .setUnit("台")
                        .setTargetPrice(new BigDecimal("2400"))
                        .setTargetContent("段状/12张/次/连续工作30分钟")
                        .setStorageArea("各部门办公室")
                        .setExpectPurchaseTime(Date.valueOf("2026-09-15"))
                        .setReferenceListStr("科密、得力、三木")
                        .setRemark("")
        );

        // 采购商务条款（真实实体 BizBusinessEntry，实体未启用链式赋值，统一走 createBusinessEntry）
        List<BizBusinessEntry> businessEntryList = Arrays.asList(
                createBusinessEntry("通用", "质量保证",
                        "供应商须提供原厂质保服务，质保期不少于3年，质保期内免费上门维修。",
                        "满足", "《政府采购货物和服务招标投标管理办法》"),
                createBusinessEntry("通用", "售后服务",
                        "供应商需在本地设有售后服务网点，响应时间不超过4小时，48小时内解决故障。",
                        "满足", "《电子电器产品售后服务管理办法》"),
                createBusinessEntry("通用", "付款方式",
                        "验收合格后30个工作日内支付合同总价的95%，质保期满后支付剩余5%。",
                        "满足", ""),
                createBusinessEntry("信息化设备", "信息安全",
                        "设备须通过国家信息安全等级保护认证，不得预装未经授权的软件。",
                        "满足", "《网络安全法》《信息安全等级保护管理办法》"),
                createBusinessEntry("信息化设备", "兼容性要求",
                        "所供设备须与现有办公网络及信息系统兼容，操作系统须为正版授权。",
                        "满足", "")
        );

        // 采购需求主单（真实实体 BizRequire，链式赋值，字段与前端 formModel 一一对应）
        return new BizRequire()
                .setRequireTitle("2026年度办公设备采购需求")
                .setRequireNo("XQ-2026-0001")
                .setRequireAttribute("goods")      // 需求属性：货物类
                .setPurchaseCategory("非政府采购")
                .setEmergency("一般")
                .setEstimatedStartTime(Date.valueOf("2026-08-15"))
                .setSubscribeDepartName("行政服务部")
                .setRequireDepartNames("行政服务部,信息技术部")
                .setOperatorName("张三")
                .setApplyName("李四")
                .setPurchaseContent("采购一批办公台式计算机及外设，用于替换老旧设备，满足部门日常办公需求。")
                .setBusService("1. 供应商需提供原厂授权及售后服务承诺函；2. 质保期不少于3年；3. 交货周期不超过30个自然日。")
                .setIsSingleSource("0")    // 是否单一来源：否
                .setIsImportPurchase("0")  // 是否进口：否
                .setIsMajor("0")           // 是否重大项目：否
                .setIsInformation("1")     // 是否信息化项目：是
                .setIsEntrust("0")         // 是否委托：否
                .setIsSecret("0")          // 是否涉密：否
                .setIsSmb("1")             // 适宜中小企业：是
                .setIsBeginningBudget("1") // 是否年初预算：是
                .setRequireCatalog("目录内")
                .setBudgetType("一般公共预算")
                .setFundsSource("财政拨款")
                .setCostSubject("公用经费")
                .setFundFlow("pay")        // 资金方向：支出
                .setOrganizeForm("分散采购")
                .setOrganizeFormExtra("非政府采购")
                .setPurchaseWay("询价")
                .setProcessWay("自行组织")
                .setCentralizedDepartName("采购部")
                .setPurchaseDepartName("采购部")
                .setBudgetName("2026年度办公设备采购预算")
                .setBudgetNo("YS-2026-BG-001")
                .setPurchaseAmount(new BigDecimal("12800"))
                .setConfirmAmount(new BigDecimal("0"))
                .setRequireBudgetAmount(new BigDecimal("15000"))
                .setPlanBeginTime(Date.valueOf("2026-08-15"))
                .setPlanFinishTime(Date.valueOf("2026-11-30"))
                .setBusinessDocType("day") // 单据类型：日常零星采购
                .setFromBizName("")
                .setIsOverYear("0")        // 是否跨年：否
                .setIsAddition("0")        // 是否追加：否
                .setIsChangeStructure("0") // 是否调整预算结构：否
                .setContainContent("采购,设备,办公")
                .setBudgetDesc("本批次采购资金来源于年度公用经费预算，已纳入2026年度部门预算。")
                .setBudgetRemark("")
                .setTargetList(targetList)
                .setBusinessEntryList(businessEntryList);
    }

    /**
     * 构建一条商务条款 demo 数据（BizBusinessEntry 未启用链式赋值，逐个 set）。
     */
    private BizBusinessEntry createBusinessEntry(String entriesCategory, String businessItem,
                                                 String businessRequirement, String businessRequirementResult,
                                                 String standardBasis) {
        BizBusinessEntry entry = new BizBusinessEntry();
        entry.setEntriesCategory(entriesCategory);
        entry.setBusinessItem(businessItem);
        entry.setBusinessRequirement(businessRequirement);
        entry.setBusinessRequirementResult(businessRequirementResult);
        entry.setStandardBasis(standardBasis);
        return entry;
    }
}
