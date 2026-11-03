package com.gmp.dto.offline;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 线下培训场次保存参数 DTO（新增 / 编辑共用）
 */
@Data
@ApiModel("线下培训场次保存参数")
public class OfflineTrainingSaveDTO {

    /** 场次ID：新增时为空，编辑时由 Controller 以路径参数覆盖，防止请求体篡改 */
    @ApiModelProperty("场次ID（编辑时必填）")
    private Long id;

    /** 关联培训计划ID，可为空 */
    @ApiModelProperty("关联培训计划ID")
    private Long planId;

    /** 关联培训课程ID，可为空 */
    @ApiModelProperty("关联培训课程ID")
    private Long courseId;

    /** 培训主题，必填 */
    @ApiModelProperty(value = "培训主题", required = true)
    @NotBlank(message = "培训主题不能为空")
    @Size(max = 200, message = "培训主题长度不能超过200个字符")
    private String title;

    /** 培训师/主讲人，可选 */
    @ApiModelProperty("培训师/主讲人")
    @Size(max = 50, message = "培训师姓名长度不能超过50个字符")
    private String trainer;

    /** 培训地点，可选 */
    @ApiModelProperty("培训地点")
    @Size(max = 200, message = "培训地点长度不能超过200个字符")
    private String location;

    /** 培训开始时间，必填 */
    @ApiModelProperty(value = "培训开始时间", required = true)
    @NotNull(message = "培训开始时间不能为空")
    private LocalDateTime startTime;

    /** 培训结束时间，必填 */
    @ApiModelProperty(value = "培训结束时间", required = true)
    @NotNull(message = "培训结束时间不能为空")
    private LocalDateTime endTime;

    /** 签到开始时间，可为空（为空时取培训开始前30分钟） */
    @ApiModelProperty("签到开始时间")
    private LocalDateTime signStartTime;

    /** 签到结束时间，可为空（为空时取培训结束时间） */
    @ApiModelProperty("签到结束时间")
    private LocalDateTime signEndTime;

    /** 备注说明，可选 */
    @ApiModelProperty("备注说明")
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
