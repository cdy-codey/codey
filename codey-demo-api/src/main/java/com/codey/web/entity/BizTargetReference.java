package com.codey.web.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Data
@ApiModel(value = "biz_target_reference", description = "标的参考品牌")
public class BizTargetReference {
    @ApiModelProperty(value = "ID")
    private String id;
    /**
     * 标的id
     */
    @ApiModelProperty(value = "标的id")
    private String targetId;

    /**
     * 需求单di
     */
    @ApiModelProperty(value = "业务id")
    private String bizId;
    /**
     * 业务阶段
     */
    @ApiModelProperty(value = "业务阶段")
    private String bizType;

    /**
     * 品牌名称
     */
    @ApiModelProperty(value = "品牌名称")
    private String brandName;

    /**
     * 规格型号
     */
    @ApiModelProperty(value = "规格型号")
    private String model;
    /**
     * 生产厂家
     */
    @ApiModelProperty(value = "生产厂家")
    private String manufacturer;
    /**
     * 创建时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;
    @ApiModelProperty(value = "创建人")
    private String createBy;


}
