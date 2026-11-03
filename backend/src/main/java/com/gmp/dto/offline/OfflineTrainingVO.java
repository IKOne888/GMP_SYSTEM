package com.gmp.dto.offline;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 线下培训场次展示 VO（管理端列表 / 详情使用）
 */
@Data
@ApiModel("线下培训场次")
public class OfflineTrainingVO {

    /** 场次ID */
    @ApiModelProperty("场次ID")
    private Long id;

    /** 关联培训计划ID */
    @ApiModelProperty("关联培训计划ID")
    private Long planId;

    /** 关联培训计划名称（回填展示用） */
    @ApiModelProperty("关联培训计划名称")
    private String planName;

    /** 关联培训课程ID */
    @ApiModelProperty("关联培训课程ID")
    private Long courseId;

    /** 关联培训课程名称（回填展示用） */
    @ApiModelProperty("关联培训课程名称")
    private String courseName;

    /** 培训主题 */
    @ApiModelProperty("培训主题")
    private String title;

    /** 培训师 */
    @ApiModelProperty("培训师")
    private String trainer;

    /** 培训地点 */
    @ApiModelProperty("培训地点")
    private String location;

    /** 培训开始时间 */
    @ApiModelProperty("培训开始时间")
    private LocalDateTime startTime;

    /** 培训结束时间 */
    @ApiModelProperty("培训结束时间")
    private LocalDateTime endTime;

    /** 实际生效的签到开始时间（已按默认规则补全） */
    @ApiModelProperty("签到开始时间")
    private LocalDateTime signStartTime;

    /** 实际生效的签到结束时间（已按默认规则补全） */
    @ApiModelProperty("签到结束时间")
    private LocalDateTime signEndTime;

    /** 签到码 */
    @ApiModelProperty("签到码")
    private String signCode;

    /** 数据库存储状态：0-未开始 1-进行中 2-已结束 3-已取消 */
    @ApiModelProperty("状态数字")
    private Integer status;

    /** 前端展示阶段：upcoming-未开始 signing-签到中 ended-已结束 cancelled-已取消 */
    @ApiModelProperty("场次阶段")
    private String phase;

    /** 场次阶段中文文案 */
    @ApiModelProperty("场次阶段文案")
    private String phaseText;

    /** 已签到人数 */
    @ApiModelProperty("已签到人数")
    private Long signCount;

    /** 备注 */
    @ApiModelProperty("备注")
    private String remark;

    /** 创建时间 */
    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;
}
