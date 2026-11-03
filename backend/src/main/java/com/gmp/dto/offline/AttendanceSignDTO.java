package com.gmp.dto.offline;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 学员自主签到参数 DTO
 */
@Data
@ApiModel("学员签到参数")
public class AttendanceSignDTO {

    /** 线下培训场次ID */
    @ApiModelProperty(value = "场次ID", required = true)
    @NotNull(message = "场次ID不能为空")
    private Long trainingId;

    /** 签到码（6位数字，由培训管理员在管理端获取并现场公布） */
    @ApiModelProperty(value = "签到码", required = true)
    @NotBlank(message = "签到码不能为空")
    private String signCode;
}
