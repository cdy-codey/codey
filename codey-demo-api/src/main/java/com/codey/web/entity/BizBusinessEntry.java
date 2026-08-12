package com.codey.web.entity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 商务条目
 *
 * @author sys
 * @date 2025-02-07
 */
@Data
@ApiModel(value = "biz_business_entry对象", description = "商务条目表")
public class BizBusinessEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID")
    private Integer id;

    @ApiModelProperty(value = "基础库id")
    private Integer libId;

    @ApiModelProperty(value = "业务类型")
    private String bizType;

    @ApiModelProperty(value = "业务Id")
    private String bizId;

    @ApiModelProperty(value = "适用分类")
    private String entriesCategory;

    @ApiModelProperty(value = "商务条目")
    private String businessItem;

    @ApiModelProperty(value = "商务要求")
    private String businessRequirement;

    @ApiModelProperty(value = "商务要求结论")
    private String businessRequirementResult;

    @ApiModelProperty(value = "标准依据")
    private String standardBasis;

    @ApiModelProperty(value = "归属库")
    private String ownerType;

    @ApiModelProperty(value = "部门id")
    private String departId;

    @ApiModelProperty(value = "创建人Id")
    private String createById;

    @ApiModelProperty(value = "创建人")
    private String createBy;

    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @ApiModelProperty(value = "更新人")
    private String updateBy;

    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    @ApiModelProperty(value = "租户ID")
    private String agencyId;
}
