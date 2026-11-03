package com.gmp.dto.offline;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 线下培训签到记录展示 VO（管理端签到明细列表使用）
 * <p>
 * 同时作为 EasyExcel 导出的数据模型，导出表头见 @ExcelProperty 注解。
 */
@Data
@ApiModel("线下培训签到记录")
public class AttendanceRecordVO {

    /** 记录ID */
    @ApiModelProperty("记录ID")
    private Long id;

    /** 场次ID */
    @ApiModelProperty("场次ID")
    private Long trainingId;

    /** 学员用户ID */
    @ApiModelProperty("学员用户ID")
    private Long userId;

    /** 学员姓名 */
    @ExcelProperty("姓名")
    @ColumnWidth(14)
    @ApiModelProperty("学员姓名")
    private String userName;

    /** 学员工号（对应 sys_user.username，避免与 userName 仅大小写差异导致访问器冲突） */
    @ExcelProperty("工号")
    @ColumnWidth(18)
    @ApiModelProperty("学员工号")
    private String employeeNo;

    /** 所属部门名称 */
    @ExcelProperty("所属部门")
    @ColumnWidth(20)
    @ApiModelProperty("所属部门")
    private String deptName;

    /** 签到时间 */
    @ExcelProperty("签到时间")
    @ColumnWidth(22)
    @ApiModelProperty("签到时间")
    private LocalDateTime signTime;

    /** 签到方式：1-学员自主签到 2-管理员补签 */
    @ApiModelProperty("签到方式数字")
    private Integer signType;

    /** 签到方式中文文案 */
    @ExcelProperty("签到方式")
    @ColumnWidth(16)
    @ApiModelProperty("签到方式文案")
    private String signTypeText;

    /** 签到来源IP */
    @ExcelProperty("来源IP")
    @ColumnWidth(18)
    @ApiModelProperty("签到来源IP")
    private String signIp;

    /** 备注（补签原因等） */
    @ExcelProperty("备注")
    @ColumnWidth(24)
    @ApiModelProperty("备注")
    private String remark;
}
