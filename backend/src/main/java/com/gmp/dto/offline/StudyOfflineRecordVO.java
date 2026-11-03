package com.gmp.dto.offline;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学员端签到记录 VO
 * <p>
 * 展示当前登录学员本人的线下培训签到历史，作为个人培训档案的补充记录。
 */
@Data
@ApiModel("学员端签到记录")
public class StudyOfflineRecordVO {

    /** 签到记录ID */
    @ApiModelProperty("记录ID")
    private Long id;

    /** 场次ID */
    @ApiModelProperty("场次ID")
    private Long trainingId;

    /** 培训主题 */
    @ApiModelProperty("培训主题")
    private String trainingTitle;

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

    /** 签到时间 */
    @ApiModelProperty("签到时间")
    private LocalDateTime signTime;

    /** 签到方式：1-学员自主签到 2-管理员补签 */
    @ApiModelProperty("签到方式数字")
    private Integer signType;

    /** 签到方式中文文案 */
    @ApiModelProperty("签到方式文案")
    private String signTypeText;
}
