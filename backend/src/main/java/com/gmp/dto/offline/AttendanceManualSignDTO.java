package com.gmp.dto.offline;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 管理员补签参数 DTO
 * <p>
 * 用于学员因设备、网络等原因未能自主签到时，由培训管理员在管理端为其补录出勤记录。
 * 补签记录会标记 signType=2，与学员自主签到（signType=1）区分，便于审计核查。
 */
@Data
@ApiModel("管理员补签参数")
public class AttendanceManualSignDTO {

    /** 被补签的学员用户ID */
    @ApiModelProperty(value = "学员用户ID", required = true)
    @NotNull(message = "学员用户ID不能为空")
    private Long userId;

    /** 补签原因（可选，建议填写以便审计追溯） */
    @ApiModelProperty("补签原因")
    @Size(max = 200, message = "补签原因长度不能超过200个字符")
    private String remark;
}
