package com.codey.web.entity;

import com.codey.web.common.Dict;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 采购需求
 *
 * @author sys
 * @version V1.0
 * @date 2022-03-24
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "biz_require对象", description = "采购需求表")
public class BizRequire implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 额外查询条件字段
     */
    @ApiModelProperty(value = "钉钉表单JSON数据")
    private String dingTalkJson;
    @ApiModelProperty(value = "钉钉表单流程实例ID")
    private String dingTalkProcInstId;
    @ApiModelProperty(value = "前端显示的钉钉表单字段")
    private String dingTalkDisplayFiled;

    @ApiModelProperty(value = "年")
    private String year;

    @ApiModelProperty(value = "Tab页查询条件 mine:我经办的 , depart:我部门的 , all:全部需求")
    private String queryType;

    @ApiModelProperty(value = "创建时间开始")
    private String createTimeBegin;

    @ApiModelProperty(value = "创建时间结束")
    private String createTimeEnd;

    @ApiModelProperty(value = "项目主要内容列表")
    private List<String> containContentList;

    /**
     * 提审快照字段
     */
    @ApiModelProperty(value = "提审是否上传合同")
    private Integer isHaveContract;


    @ApiModelProperty(value = "入库单出库审批意见结果0不同意，1同意，默认同意")
    private Integer outProjectLibResult;

    /*额外查询条件字段*/
    @ApiModelProperty(value = "采购标的明细列表")
    private List<BizRequireTarget> targetList;

    @ApiModelProperty(value = "商务条目")
    private List<BizBusinessEntry> businessEntryList;

    @ApiModelProperty(value = "是否为下一步操作 true: 下一步 false:仅保存")
    private Boolean isNext;

    @ApiModelProperty(value = "是否完成需求 true:已完成 false:仅保存")
    private Boolean isComplete;

    @ApiModelProperty(value = "流程实例id")
    private String businessKey;

    @ApiModelProperty(value = "审核id")
    private String tableId;

    @ApiModelProperty(value = "节点id")
    private String nodeId;

    @ApiModelProperty(value = "论证进度text")
    private String discusProgress_dictText;

    @ApiModelProperty(value = "是否修改审定状态")
    private Boolean changeConfirmStatus;

    @ApiModelProperty(value = "剩余可用金额")
    private BigDecimal remainAmount;

    @ApiModelProperty(value = "已批复的预算金额")
    private BigDecimal replyAmount;

    /**
     * 审议控制价(元)
     */
    @ApiModelProperty(value = "入库单审议控制价(元)")
    private BigDecimal projectLibConfirmAmount;

    /**
     * 采购项目数量
     */
    private Integer projectCount;

    @ApiModelProperty(hidden = true, value = "论证专家List")
    private List<String> expertInfoList;

    @ApiModelProperty(value = "采购人电话")
    private String operatorPhone;


    @ApiModelProperty(value = "归口预算部门列表")
    private List<String> budgetDeptList;

    @ApiModelProperty(value = "归口预算部门名称")
    private List<String> budgetDeptListName;


    @ApiModelProperty(value = "跳转类型:默认 list 列表,projectExecute 项目执行")
    private String redirectType;

    @ApiModelProperty(value = "跳转到项目ID")
    private String redirectProjectId;


    /**
     * 需求单对象
     */
    private String projectLibId;

    /**
     * 标的单价列表（用于匹配规则）
     */
    private String targetAmounts;

    /*额外参数字段*/

    /**
     * ID
     */
    @ApiModelProperty(value = "ID")
    private String id;
    /**
     * 所属机构
     */
    @ApiModelProperty(value = "所属机构")
    private String agencyId;
    /**
     * 所属部门
     */
    @ApiModelProperty(value = "所属部门")
    private String departId;
    /**
     * 关联单据类型
     */
    @ApiModelProperty(value = "关联单据类型,字典 project_from_type,none,projectLib,requireLib")
    @Dict(dicCode = "require_from_type")
    private String fromBizType;
    /**
     * 关联单据id
     */
    @ApiModelProperty(value = "关联单据id")
    private String fromBizId;
    /**
     * 关联单据名称
     */
    @ApiModelProperty(value = "关联单据名称")
    private String fromBizName;
    /**
     * 关联单据编号
     */
    private String fromBizNo;


    /**
     * 需求编号
     */
    @ApiModelProperty(value = "需求编号")
    private String requireNo;
    /**
     * 立项审核id
     */
    @ApiModelProperty(value = "立项审核id")
    private String projectReviewId;
    /**
     * 立项审核编号
     */
    @ApiModelProperty(value = "立项审核编号")
    private String projectReviewNo;
    /**
     * 立项审核流程id
     */
    @ApiModelProperty(value = "立项审核编号")
    private String projectReviewActId;
    /**
     * 送审业务标题
     */
    @ApiModelProperty(value = "送审业务标题")
    private String projectReviewActTitle;
    /**
     * 立项审核状态
     */
    @ApiModelProperty(value = "立项状态 create:新发起,review:审核中,approved:已批准,unapproved:未批准,close:已关闭")
    @Dict(dicCode = "project_review_status")
    private String projectReviewStatus;
    private String projectReviewStatus_dictText;

    @ApiModelProperty(value = "单据类型,year年度计划，day日常零星采购")
    @Dict(dicCode = "business_doc_type")
    private String businessDocType;
    private String businessDocType_dictText;

    /**
     * 预算归口部门列表，逗号隔开
     */
    @ApiModelProperty(value = "预算归口部门列表，逗号隔开")
    private String budgetDepartIds;
    /**
     * 申购部门id
     */
    @ApiModelProperty(value = "申购部门id")
    private String subscribeDepartId;
    /**
     * 申购部门名称
     */
    @ApiModelProperty(value = "申购部门名称")
    private String subscribeDepartName;
    /**
     * 需求部门ids
     */
    @ApiModelProperty(value = "需求部门ids")
    private String requireDepartIds;
    /**
     * 需求部门名称
     */
    @ApiModelProperty(value = "需求部门名称")
    private String requireDepartNames;

    /**
     * 需求标题
     */
    @ApiModelProperty(value = "需求标题", notes = "亦作需求标题查询条件")
    private String requireTitle;
    /**
     * 需求部门id
     */
    @ApiModelProperty(value = "需求部门id", notes = "亦作需求部门查询条件")
    private String requireDepartId;
    /**
     * 需求部门名称
     */
    @ApiModelProperty(value = "需求部门名称")
    private String requireDepartName;

    @ApiModelProperty(value = "需求经办人 项目成员中角色类型为需求经办人的员工")
    private String requireHandler;

    /**
     * 是否单一来源
     */
    @ApiModelProperty(value = "是否单一来源")
    @Dict(dicCode = "yn")
    private String isSingleSource;
    private String isSingleSource_dictText;
    /**
     * 是否进口采购
     */
    @ApiModelProperty(value = "是否进口采购")
    @Dict(dicCode = "yn")
    private String isImportPurchase;
    private String isImportPurchase_dictText;

    /**
     * 是否三重一大：1是 0否
     */
    @ApiModelProperty(value = "是否三重一大：1是 0否")
    @Dict(dicCode = "yn")
    private String isMajor;
    private String isMajor_dictText;

    /**
     * 是否信息化：1是 0否
     */
    @ApiModelProperty(value = "是否信息化：1是 0否")
    @Dict(dicCode = "yn")
    private String isInformation;
    private String isInformation_dictText;

    /**
     * 是否委托：1是 0否
     */
    @ApiModelProperty(value = "是否委托：1是 0否")
    @Dict(dicCode = "yn")
    private String isEntrust;
    private String isEntrust_dictText;

    /**
     * 需求紧急度
     */
    @ApiModelProperty(value = "需求紧急度")
    @Dict(dicCode = "require_emergency")
    private String emergency;
    private String emergency_dictText;
    /**
     * 是否涉密
     */
    @ApiModelProperty(value = "是否涉密")
    @Dict(dicCode = "yn")
    private String isSecret;
    private String isSecret_dictText;

    /**
     * 是否年初预算
     */
    @ApiModelProperty(value = "是否年初预算")
    @Dict(dicCode = "yn")
    private String isBeginningBudget;
    private String isBeginningBudget_dictText;

    /**
     * 是否已偏离
     */
    @ApiModelProperty(value = "是否偏离")
    @Dict(dicCode = "yn")
    private String isDeviate;
    /**
     * 偏离率
     */
    @ApiModelProperty(value = "偏离率")
    private Double deviateRate;
    /**
     * 是否非核心参数已偏离
     */
    @ApiModelProperty(value = "是否非核心参数偏离")
    @Dict(dicCode = "yn")
    private String isOtherDeviate;
    /**
     * 是否非核心参数已偏离超出
     */
    @ApiModelProperty(value = "是否非核心参数偏离超出")
    private String isOtherDeviateOver;
    @ApiModelProperty(value = "偏离情况说明")
    private String deviateReasonDesc;
    /**
     * 是否上会
     */
    @ApiModelProperty(value = "是否上会")
    @Dict(dicCode = "yn")
    private String isMeeting;
    private String isMeeting_dictText;

    /**
     * 是否适宜中小型企业
     */
    @ApiModelProperty(value = "是否适宜中小型企业")
    @Dict(dicCode = "is_SMB")
    private String isSmb;
    private String isSmb_dictText;

    /**
     * 预计启动时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @ApiModelProperty(value = "预计启动时间")
    private Date estimatedStartTime;

    /**
     * 需求属性 goods: 货物服务类 build:工程建设类
     */
    @ApiModelProperty(value = "需求属性 goods: 货物类 build:工程建设类 service服务类")
    @Dict(dicCode = "project_attribute")
    private String requireAttribute;
    private String requireAttribute_dictText;
    /**
     * 申购金额
     */
    @ApiModelProperty(value = "申购金额(元)")
    private BigDecimal purchaseAmount;
    /**
     * 审定金额
     */
    @ApiModelProperty(value = "审定金额(元)")
    private BigDecimal confirmAmount;
    /**
     * 已使用金额
     */
    @ApiModelProperty(value = "已使用金额(元)")
    private BigDecimal usedAmount;

    @ApiModelProperty(value = "预算（总）金额")
    private BigDecimal requireBudgetAmount;

    /**
     * 采购内容描述
     */
    @ApiModelProperty(value = "采购内容描述")
    private String purchaseContent;
    /**
     * 商务服务要求
     */
    @ApiModelProperty(value = "商务服务要求")
    private String busService;

    /**
     * 采购类别
     */
    @ApiModelProperty(value = "采购类别")
    @Dict(dicCode = "organize_form_extra")
    private String purchaseCategory;
    private String purchaseCategory_dictText;

    /**
     * 需求状态 create:新创建 discus:论证中 research:调研中 to_project: 待立项  has_project: 已立项 review:审核中 close:已关闭
     */
    @ApiModelProperty(value = "需求状态 , create:新创建 , discus:论证中 , research:调研中 , toProject: 待立项 , hasProject: 已立项 , review:审核中 , close:已关闭")
    @Dict(dicCode = "purchase_require_status")
    private String requireStatus;
    private String requireStatus_dictText;

    /**
     * 审定状态 confirm 已审定 notConfirm 待审定
     */
    @ApiModelProperty(value = "审定状态 confirm 已审定 notConfirm 待审定")
    @Dict(dicCode = "require_confirm_status")
    private String confirmStatus;
    private String confirmStatus_dictText;

    /**
     * 是否可生成项目 是:1 否:0
     */
    @ApiModelProperty(value = "是否可生成项目 是:1 否:0")
    @Dict(dicCode = "yn")
    private Integer canProject;
    private String canProject_dictText;

    /**
     * 是否采购完成 是:1 否:0
     */
    @ApiModelProperty(value = "是否采购完成 是:1 否:0")
    @Dict(dicCode = "yn")
    private Integer isPurchase;
    private String isPurchase_dictText;

    /**
     * 是否有标的 是:1 否:0
     */
    @ApiModelProperty(value = "是否有标的 是:1 否:0")
    @Dict(dicCode = "yn")
    private Integer hasTarget;
    private String hasTarget_dictText;

    /**
     * 需求状态Num 1:新创建 , 2:论证中 , 3:调研中 , 4:待提交 , 5:待立项 , 6:已立项 , 7:审核中 , 8:已关闭
     */
    @ApiModelProperty(value = "需求状态Num , 新创建:1,论证中:2,调研中:3,待提交:4,待立项:5,已立项:6,审核中:7,已关闭:8")
    private Integer requireStatusNum;
    /**
     * 是否开启需求论证 1: 是 0:否
     * 默认1 页面用户选择的开关默认是打开状态
     */
    @ApiModelProperty(value = "默认1 页面用户选择的开关默认是打开状态")
    private Integer isNeedDiscus;

    @ApiModelProperty(value = "是否启动前期咨询 1:是 0:否")
    @Dict(dicCode = "yn")
    private Integer isEarlyConsult;
    private String isEarlyConsult_dictText;

    /**
     * 论证专家
     */
    @ApiModelProperty(value = "论证专家")
    private String expertInfo;

    /**
     * 采购必要性 0:否 1:是
     */
    @ApiModelProperty(value = "采购必要性 0:否 1:是")
    @Dict(dicCode = "yn")
    private Integer purchaseNecessity;
    private String purchaseNecessity_dictText;

    /**
     * 需求可行性 0:否 1:是
     */
    @ApiModelProperty(value = "需求可行性 0:否 1:是")
    @Dict(dicCode = "yn")
    private Integer requireFeasibility;
    private String requireFeasibility_dictText;

    /**
     * 调研咨询情况 0:否 1:是
     */
    @ApiModelProperty(value = "调研咨询情况 0:否 1:是")
    @Dict(dicCode = "yn")
    private Integer researchResult;
    private String researchResult_dictText;

    /**
     * 预算编号
     */
    @ApiModelProperty(value = "预算编号")
    private String researchNo;
    /**
     * 预算项目
     */
    @ApiModelProperty(value = "预算项目")
    private String researchProject;
    /**
     * 是否开启预算调研 1: 是 0:否
     * 默认1 页面用户选择的开关默认是打开状态
     */
    @ApiModelProperty(value = "默认1 页面用户选择的开关默认是打开状态")
    private Integer isNeedResearch;
    /**
     * 是否默认开启需求论证（0：否，1：是，2：可选）
     * 取消：由前端根据流程开关列表取值，前端自己控制
     */
    @ApiModelProperty(value = "取消：由前端根据流程开关列表取值，前端自己控制")
    private Integer defaultNeedDiscus;
    /**
     * 是否默认开启预算调研（0：否，1：是，2：可选）
     * 取消：由前端根据流程开关列表取值，前端自己控制
     */
    @ApiModelProperty(value = "取消：由前端根据流程开关列表取值，前端自己控制")
    private Integer defaultNeedResearch;
    /**
     * 申请人id
     */
    @ApiModelProperty(value = "申请人id")
    private String applyId;
    /**
     * 申请人名称
     */
    @ApiModelProperty(value = "申请人名称")
    private String applyName;
    /**
     * 经办人id
     */
    @ApiModelProperty(value = "经办人id")
    private String operatorId;
    /**
     * 经办人名称
     */
    @ApiModelProperty(value = "经办人名称")
    private String operatorName;
    /**
     * 经办人部门id
     */
    @ApiModelProperty(value = "经办人部门id")
    private String operatorDepartId;
    /**
     * 经办人部门名称
     */
    @ApiModelProperty(value = "经办人部门名称")
    private String operatorDepartName;

    /**
     * 采购目录性质
     */
    @ApiModelProperty(value = "采购目录性质")
    @Dict(dicCode = "require_catalog")
    private String requireCatalog;
    private String requireCatalog_dictText;

    /**
     * 预算类型
     */
    @ApiModelProperty(value = " 预算类型")
    @Dict(dicCode = "budget_type")
    private String budgetType;
    private String budgetType_dictText;

    /**
     * 资金性质
     */
    @ApiModelProperty(value = "资金性质")
    @Dict(dicCode = "funds_source")
    private String fundsSource;
    private String fundsSource_dictText;

    /**
     * 初拟组织形式
     */
    @ApiModelProperty(value = "初拟组织形式")
    @Dict(dicCode = "organize_form")
    private String organizeForm;
    private String organizeForm_dictText;

    /**
     * 组织形式性质
     */
    @ApiModelProperty(value = "组织形式性质")
    @Dict(dicCode = "organize_form_extra")
    private String organizeFormExtra;
    private String organizeFormExtra_dictText;

    /**
     * 初拟采购方式
     */
    @ApiModelProperty(value = "初拟采购方式")
    @Dict(dicCode = "purchase_way")
    private String purchaseWay;
    private String purchaseWay_dictText;

    /**
     * 采购执行方式
     */
    @ApiModelProperty(value = "采购执行方式")
    @Dict(dicCode = "process_way")
    private String processWay;
    private String processWay_dictText;

    /**
     * 资金来源
     * 原: 费用科目
     */
    @ApiModelProperty(value = "资金来源")
    private String costSubject;

    /**
     * 归口执行部门名称
     */
    @ApiModelProperty(value = "归口执行部门名称")
    private String centralizedDepartName;

    /**
     * 归口执行部门id
     */
    @ApiModelProperty(value = "归口执行部门id")
    private String centralizedDepartId;

    /**
     * 采购执行部门id
     */
    @ApiModelProperty(value = "采购执行部门id")
    private String purchaseDepartId;

    /**
     * 采购执行部门名称
     */
    @ApiModelProperty(value = "采购执行部门名称")
    private String purchaseDepartName;

    /**
     * 需求论证进度
     */
    @ApiModelProperty(value = "需求论证进度")
    private String discusProgress;

    /**
     * 需求论证流程实例id
     */
    @ApiModelProperty(value = "需求论证流程实例id")
    private String processInstanceId;

    /**
     * 预算项目Id
     */
    @ApiModelProperty(value = "预算项目Id")
    private String budgetId;

    /**
     * 预算项目名称
     */
    @ApiModelProperty(value = "预算项目名称")
    private String budgetName;

    /**
     * 预算编号
     */
    @ApiModelProperty(value = "预算编号")
    private String budgetNo;

    /**
     * 指标可用余额
     */
    @ApiModelProperty(value = "指标可用余额")
    private BigDecimal budgetRemain;

    /**
     * 备注
     */
    @ApiModelProperty(value = "备注")
    private String budgetRemark;

    /**
     * 采购计划审核编号
     */
    @ApiModelProperty(value = "采购计划审核编号")
    private String planReviewNo;
    /**
     * 采购计划审核流程id
     */
    @ApiModelProperty(value = "采购计划审核流程id")
    private String planReviewActId;
    /**
     * 采购计划审核状态
     */
    @ApiModelProperty(value = "采购计划审核状态 create:新发起,review:审核中,approved:已批准,unapproved:未批准,close:已关闭")
    @Dict(dicCode = "com_review_status")
    private String planReviewStatus;
    private String planReviewStatus_dictText;

    /**
     * 采购计划审核创建时间
     */
    @ApiModelProperty(value = "采购计划审核创建时间")
    private Date planReviewCreateTime;

    /**
     * 采购计划审核-流程重启uuId
     */
    @ApiModelProperty(value = "采购计划审核-流程重启uuId")
    private String planReviewResetId;

    /*计划开始时间
    /**
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @ApiModelProperty(value = "计划开始时间")
    private Date planBeginTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @ApiModelProperty(value = "计划完成时间")
    private Date planFinishTime;

    /**
     * 需求创建页面标识
     */
    @ApiModelProperty(value = "需求创建页面标识")
    private String addType;

    /**
     * 政府采购计划状态
     */
    @ApiModelProperty(value = "政府采购计划状态")
    @Dict(dicCode = "require_plan_status")
    private String planStatus;
    private String planStatus_dictText;

    /**
     * 是否已分派 1:是 2:否
     */
    @ApiModelProperty(value = "是否已分派 1:是 0:否")
    private Integer isGenerate;

    /**
     * 分派人员id/采购负责人
     */
    @ApiModelProperty(value = "分派人员id")
    private String generateUserId;

    /**
     * 分派人员名称/采购负责人
     */
    @ApiModelProperty(value = "分派人员名称")
    private String generateUserName;

    /**
     * 分派部门id/采购负责人部门
     */
    @ApiModelProperty(value = "分派部门id")
    private String generateDepartId;

    /**
     * 分派部门名称/采购负责人部门
     */
    @ApiModelProperty(value = "分派部门名称")
    private String generateDepartName;

    /**
     * 是否补录
     */
    @Dict(dicCode = "yn")
    @ApiModelProperty(value = "是否补录")
    private String isAddition;
    private String isAddition_dictText;

    /**
     * 资金方向（income：收入，pay：支出）
     */
    @ApiModelProperty(value = "资金方向（income：收入，pay：支出）")
    @Dict(dicCode = "contract_fund_flow")
    private String fundFlow;
    private String fundFlow_dictText;

    /**
     * 工程是否改变主体结构（1：是，0：否）
     */
    @ApiModelProperty(value = "工程是否改变主体结构（1：是，0：否）")
    @Dict(dicCode = "yn")
    private String isChangeStructure;
    private String isChangeStructure_dictText;

    /**
     * 项目包含内容 多个用,分割 @dictCode=project_contain_content
     */
    @ApiModelProperty(value = "项目包含内容 多个用,分割 @dictCode=project_contain_content")
    @Dict(dicCode = "project_contain_content")
    private String containContent;
    private String containContent_dictText;

    /**
     * 是否重启流程
     */
    @ApiModelProperty(value = "是否重启流程")
    private Boolean isResetProcess;

    /**
     * 流程重启uuId
     */
    @ApiModelProperty(value = "流程重启uuId")
    private String resetId;

    /**
     * 是否跨年项目
     */
    @ApiModelProperty(value = "是否跨年项目")
    @Dict(dicCode = "yn")
    private String isOverYear;
    private String isOverYear_dictText;

    /**
     * 是否临时授权
     */
    @ApiModelProperty(value = "是否临时授权")
    @Dict(dicCode = "yn")
    private String isTemporaryAuthorize;
    private String isTemporaryAuthorize_dictText;

    /**
     * 资金说明
     */
    @ApiModelProperty(value = "资金说明")
    private String budgetDesc;


    @ApiModelProperty(value = "第三方关联ID")
    private String thirdId;
    @ApiModelProperty(value = "第三方单据号")
    private String thirdNo;
    @ApiModelProperty(value = "第三方单据名称")
    private String thirdName;
    @ApiModelProperty(value = "第三方关联类型")
    private String thirdType;

    @ApiModelProperty(value = "是否删除：1是，0否")
    private String isDel;
    /**
     * 完成时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "完成时间")
    private Date finishTime;
    /**
     * 表单模式:简单模式，流程模式
     * simple:简单模式，flow:流程模式
     */
    @ApiModelProperty(value = "simple:简单模式，flow:业务流")
    private String formMode;

    @ApiModelProperty(value = "拟定成交价")
    private String priceTotal;

    /**
     * 创建人ID
     */
    @ApiModelProperty(value = "创建人ID")
    private String createId;
    /**
     * 创建人
     */
    @ApiModelProperty(value = "创建人")
    private String createBy;
    /**
     * 创建时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;
    /**
     * 更新人
     */
    @ApiModelProperty(value = "更新人")
    private String updateBy;
    /**
     * 更新时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

}
