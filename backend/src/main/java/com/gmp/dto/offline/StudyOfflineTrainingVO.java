package com.gmp.dto.offline;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学员端线下培训场次 VO
 * <p>
 * 供 PC 学员端与微信小程序展示「可签到的线下培训」，并标记当前登录学员是否已签到。
 */
@Data
@ApiModel("学员端线下培训场次")
public class StudyOfflineTrainingVO {

    /** 场次ID */
    @ApiModelProperty("场次ID")
    private Long id;

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

    /** 实际生效的签到开始时间 */
    @ApiModelProperty("签到开始时间")
    private LocalDateTime signStartTime;

    /** 实际生效的签到结束时间 */
    @ApiModelProperty("签到结束时间")
    private LocalDateTime signEndTime;

    /** 场次阶段：upcoming-未开始 signing-签到中 ended-已结束 cancelled-已取消 */
    @ApiModelProperty("场次阶段")
    private String phase;

    /** 场次阶段中文文案 */
    @ApiModelProperty("场次阶段文案")
    private String phaseText;

    /** 当前学员是否已签到 */
    @ApiModelProperty("是否已签到")
    private Boolean signed;

    /** 当前学员签到时间（未签到时为空） */
    @ApiModelProperty("签到时间")
    private LocalDateTime signTime;
}
