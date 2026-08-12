package com.codey.web.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;

/**
 * 标的参数表
 * @author sys
 * @date 2025-02-07
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="biz_target_param对象", description="标的参数表")
public class BizTargetParam implements Serializable {
    private static final long serialVersionUID = 1L;

	/**ID*/
    @ApiModelProperty(value = "ID")
    private String id;
	/**所属机构*/
    @ApiModelProperty(value = "所属机构")
    private String agencyId;
    /**业务Id*/
    @ApiModelProperty(value = "业务Id")
    private String bizId;
    /**业务类型*/
    @ApiModelProperty(value = "业务类型")
    private String bizType;
	/**标的id*/
    @ApiModelProperty(value = "标的id")
    private String targetId;
	/**标的id*/
    @ApiModelProperty(value = "标的库参数id")
    private String libraryParamId;
	/**参数名称*/
    @ApiModelProperty(value = "参数名称")
    private String paramName;
	/**标的参数及要求*/
    @ApiModelProperty(value = "参数要求")
    private String paramContent;
	/**标的参数及要求结论*/
    @ApiModelProperty(value = "标的参数及要求结论(仅入库单和调研项目使用)")
    private String resultParamContent;
	/**是否核心参数：1是 0否*/
    @ApiModelProperty(value = "是否核心参数：1是 0否")
    private String isCoreParam;
	/**创建人*/
    @ApiModelProperty(value = "创建人")
    private String createBy;
	/**创建时间*/
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "创建时间")
    private java.util.Date createTime;
	/**更新人*/
    @ApiModelProperty(value = "更新人")
    private String updateBy;
	/**更新时间*/
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "更新时间")
    private java.util.Date updateTime;

}
