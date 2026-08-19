package com.codey.web.entity;
import com.codey.client.FormIgnore;
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
 * 采购标的
 *
 * @author sys
 * @version V1.0
 * @date 2022-03-24
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "biz_require_target对象", description = "需求标的表")
public class BizRequireTarget  implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 额外查询条件字段
     */

    @ApiModelProperty(value = "Tab页查询条件 mine:我经办的 depart:我部门的 all:全部需求")
    @FormIgnore
    private String queryType;

    @ApiModelProperty(value = "是否库查询(需求单项目库) 1:是 0:否")
    @FormIgnore
    private Integer isLibQuery;

    @ApiModelProperty("查询排除id")
    @FormIgnore
    private String noIds;

    @ApiModelProperty("查询包含id")
    @FormIgnore
    private String inIds;

    @ApiModelProperty(value = "创建时间开始")
    @FormIgnore
    private String createTimeBegin;

    @ApiModelProperty(value = "创建时间结束")
    @FormIgnore
    private String createTimeEnd;

    @ApiModelProperty(value = "需求id, 逗号分割")
    @FormIgnore
    private String requireIds;

    @ApiModelProperty(value = "排除的标的id, 逗号分割")
    @FormIgnore
    private String excludeTargetIds;

    /**
     * 需求申购金额
     */
    @ApiModelProperty(value = "需求申购金额")
    private BigDecimal requirePurchaseAmount;

    /**
     * 额外参数字段
     */

    @ApiModelProperty(value = "标的状态Num")
    private Integer targetStatusNum;

    @ApiModelProperty(value = "流程实例id")
    private String businessKey;

    @ApiModelProperty(value = "审核id")
    private String tableId;

    @ApiModelProperty(value = "标的状态描述")
    private String targetStatusDesc;

    @ApiModelProperty(value = "标的id")
    private String targetId;

    @ApiModelProperty(value = "是否有改动")
    private Integer isChange;

    @ApiModelProperty(value = "最新申购金额(元)")
    private BigDecimal newTargetAmount;
    @ApiModelProperty(value = "最新数量")
    private Double newNum;
    @ApiModelProperty(value = "最新单价")
    private BigDecimal newUnitPrice;
    /**
     * ID
     */
    @ApiModelProperty(value = "ID")
    private String id;

    /**
     * 需求单，项目库关联字段
     */
    @ApiModelProperty(value = "需求单名称")
    private String requireLibTitle;

    @ApiModelProperty(value = "需求单id")
    private String requireLibId;

    @ApiModelProperty(value = "需求单编号")
    private String requireLibNo;

    @ApiModelProperty(value = "项目库id")
    private String projectLibId;
    @ApiModelProperty(value = "项目库编号")
    private String projectLibNo;

    /**
     * 采购需求id
     */
    @ApiModelProperty(value = "采购申请名称id")
    private String requireId;

    /**
     * 需求编号
     */
    @ApiModelProperty(value = "采购申请名称)")
    private String requireTitle;

    /**
     * 需求编号
     */
    @ApiModelProperty(value = "采购申请单号")
    private String requireNo;

    /**
     * 采购项目id
     */
    @ApiModelProperty(value = "采购项目id")
    private String projectPurchaseId;
    /**
     * 采购项目编号
     */
    @ApiModelProperty(value = "采购项目编号")
    private String projectPurchaseNo;
    /**
     * 采购项目内部编号
     */
    @ApiModelProperty(value = "采购项目内部编号")
    private String projectInNo;

    /**
     * 采购项目内部编号
     */
    @ApiModelProperty(value = "采购立项名称")
    private String projectName;

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

    @ApiModelProperty(value = "需求部门id")
    private String requireDepartId;

    @ApiModelProperty(value = "需求部门名称")
    private String requireDepartName;
    /**
     * 需求业务类型
     */
    @ApiModelProperty(value = "需求业务类型")
    @Dict(dicCode = "require_type")
    private String requireType;
    private String requireType_dictText;
    /**
     * 是否零星采购
     */
    @ApiModelProperty(value = "单据类型,year年度计划，day日常零星采购")
    @Dict(dicCode = "business_doc_type")
    private String businessDocType;
    private String businessDocType_dictText;
    /**
     * 标的识别码
     */
    @ApiModelProperty(value = "标的识别码")
    private String targetNo;

    /**
     * 分组id
     */
    @ApiModelProperty(value = "分组id")
    private String targetGroupId;
    /**
     * 分组名称
     */
    @ApiModelProperty(value = "分组名称")
    private String targetGroupName;

    /**
     * 来源标的id
     */
    @ApiModelProperty(value = "来源标的id")
    private String sourceTargetId;

    /**
     * 标的库id
     */
    @ApiModelProperty(value = "标的库id")
    private String targetLibraryId;


    /**
     * 标项ID
     */
    @ApiModelProperty(value = "标项ID")
    private String bidId;
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
     * 标的类型id
     */
    @ApiModelProperty(value = "标的类型id")
    private String targetTypeId;
    /**
     * 标的类型名称
     */
    @ApiModelProperty(value = "标的类型名称")
    private String targetTypeName;

    /**
     * 标的类型编号
     */
    @ApiModelProperty(value = "标的类型编号")
    private String targetTypeCode;
    /**
     * 标的名称
     */
    @ApiModelProperty(value = "标的名称")
    private String targetName;

    /**
     * 申购金额(元)
     */
    @ApiModelProperty(value = "申购金额(元)/初筛后金额/审议控制价")
    private BigDecimal targetAmount;

    /**
     * 等级
     */
    @ApiModelProperty(value = "需求单等级,字典require_level")
    @Dict(dicCode = "require_level")
    private String requireLibLevel;

    /**
     * 排序
     */
    @ApiModelProperty(value = "排序")
    private Integer requireLibSort;


    /**
     * 审定金额(元)
     */
    @ApiModelProperty(value = "审定金额(元)")
    private BigDecimal targetConfirmAmount;

    private String targetAmountStr;

    /**
     * 参数及要求
     */
    @ApiModelProperty(value = "参数及要求/规格参数")
    private String targetContent;

    @ApiModelProperty(value = "结论参数")
    private String bizTargetResultParamsListStr;

    /**
     * 合并状态
     */
    @ApiModelProperty(value = "合并入库状态 0:未入库 1:入库中(等待入库) 2:已入库 3:已入需求")
    private Integer mergeStatus;

    /**
     * 标的状态
     */
    @ApiModelProperty(value = "标的状态")
    @Dict(dicCode = "purchase_targetStatus_status")
    private String targetStatus;
    private String targetStatus_dictText;

    /**
     * 调研项目id
     */
    @ApiModelProperty(value = "调研项目id")
    private String researchId;

    /**
     * 采购分类id
     */
    @ApiModelProperty(value = "采购分类id")
    private String purchaseTypeId;

    /**
     * 采购分类名称
     */
    @ApiModelProperty(value = "采购分类名称")
    private String purchaseTypeName;

    /**
     * 期望使用时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @ApiModelProperty(value = "期望使用时间")
    private Date expectPurchaseTime;

    @ApiModelProperty(value = "参考单价")
    private BigDecimal referencePrice;
    @ApiModelProperty(value = "参考数量(需求数量)")
    private Double referenceNum;
    @ApiModelProperty(value = "金额/参考总金额/计划控制价")
    private BigDecimal referenceTotalPrice;

    @ApiModelProperty(value = "需求配置说明 ")
    private String requireConfig;
    //需求单项目库关联字段结束

    /**
     * 审定状态 confirm 已审定 notConfirm 待审定
     */
    @ApiModelProperty(value = "审定状态 confirm 已审定 notConfirm 待审定")
    @Dict(dicCode = "require_confirm_status")
    private String confirmStatus;

    @ApiModelProperty(value = "是否为默认标的")
    private String confirmStatus_dictText;
    /**
     * 预算是否批复
     */
    @ApiModelProperty(value = "是否已批复预算")
    @Dict(dicCode = "yn")
    private String budgetReply;
    @ApiModelProperty(value = "是否为默认标的")
    private String budgetReply_dictText;
    /**
     * 是否为默认标的
     */
    @ApiModelProperty(value = "是否为默认标的")
    private Integer isDefault;
    /**
     * 单价(元)
     */
    @ApiModelProperty(value = "单价(元)/审议单价")
    private BigDecimal unitPrice;
    /**
     * 数量
     */
    @ApiModelProperty(value = "数量/审批数量/标签筛选数量/审议数量")
    private Double num;
    @ApiModelProperty(value = "已验收数量")
    private Double  acceptNum;
    /**
     * 单位
     */
    @ApiModelProperty(value = "单位")
    private String unit;
    /**
     * 金额(元)，单价×数量
     */
    @ApiModelProperty(value = "金额(元)")
    private BigDecimal targetPrice;

    @ApiModelProperty(value = "采购目录性质")
    @Dict(dicCode = "require_catalog")
    private String requireCatalog;

    private String requireCatalog_dictText;

    @ApiModelProperty(value = "需求属性 goods: 货物类 build:工程建设类 service服务类")
    @Dict(dicCode = "project_attribute")
    private String requireAttribute;

    @ApiModelProperty(value = "需求属性 goods: 货物类 build:工程建设类 service服务类")
    private String requireAttribute_dictText;
    /**
     * 存放地
     */
    @ApiModelProperty(value = "存放地/提交服务成果地点/工程地点")
    private String storageArea;
    @ApiModelProperty(value = "院区设备数量说明")
    private String storageAreaDesc;
    @ApiModelProperty(value = "设备经济效益情况说明")
    private String economicDesc;

    /**
     * 备注
     */
    @ApiModelProperty(value = "备注")
    private String remark;
    /**
     * 经办人id
     */
    @ApiModelProperty(value = "经办人/填报人id")
    private String operatorId;
    /**
     * 经办人名称
     */
    @ApiModelProperty(value = "经办人/填报人名称")
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
     * 排序字段
     */
    @ApiModelProperty(value = "排序字段")
    private Integer sortOrder;

    @ApiModelProperty(value = "需求经办人 项目成员中角色类型为需求经办人的员工")
    private String requireHandler;

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

    @ApiModelProperty(value = "标的参数列表")

    protected List<BizTargetParam> targetParamList;
    /**
     * 参考品牌（需填写至少3家）
     */

    @ApiModelProperty(value = "成交品牌（只能有一家）")
    protected List<BizTargetReference> referenceList;


    @ApiModelProperty(value = "参考品牌")
    protected String referenceListStr;


    @ApiModelProperty(value = "参数说明")
    protected String bizTargetParamListStr;
}