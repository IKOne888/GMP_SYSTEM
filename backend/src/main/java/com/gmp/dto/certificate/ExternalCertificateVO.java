package com.gmp.dto.certificate;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 外部专项培训 / 资质证书展示 VO
 * <p>
 * 同时作为 EasyExcel 导出的数据模型，导出表头见 @ExcelProperty 注解。
 */
@Data
@ApiModel("资质证书展示对象")
public class ExternalCertificateVO {

    /** 记录ID */
    @ApiModelProperty("记录ID")
    private Long id;

    /** 持证人用户ID */
    @ApiModelProperty("持证人用户ID")
    private Long userId;

    /** 持证人姓名 */
    @ExcelProperty("姓名")
    @ColumnWidth(14)
    @ApiModelProperty("持证人姓名")
    private String userName;

    /** 持人工号（对应 sys_user.username，避免与 userName 仅大小写差异导致访问器冲突） */
    @ExcelProperty("工号")
    @ColumnWidth(18)
    @ApiModelProperty("持人工号")
    private String employeeNo;

    /** 所属部门名称 */
    @ExcelProperty("所属部门")
    @ColumnWidth(20)
    @ApiModelProperty("所属部门")
    private String deptName;

    /** 类型：1-外部培训 2-资质证书 */
    @ApiModelProperty("类型数字")
    private Integer certType;

    /** 类型中文文案 */
    @ExcelProperty("类型")
    @ColumnWidth(12)
    @ApiModelProperty("类型文案")
    private String certTypeText;

    /** 证书/培训名称 */
    @ExcelProperty("证书/培训名称")
    @ColumnWidth(28)
    @ApiModelProperty("证书/培训名称")
    private String certName;

    /** 证书编号 */
    @ExcelProperty("证书编号")
    @ColumnWidth(22)
    @ApiModelProperty("证书编号")
    private String certNo;

    /** 发证机构/培训机构 */
    @ExcelProperty("发证机构")
    @ColumnWidth(24)
    @ApiModelProperty("发证机构")
    private String issuer;

    /** 发证日期 */
    @ExcelProperty("发证日期")
    @DateTimeFormat("yyyy-MM-dd")
    @ColumnWidth(14)
    @ApiModelProperty("发证日期")
    private LocalDate issueDate;

    /** 有效期至（为空表示长期有效） */
    @ExcelProperty("有效期至")
    @DateTimeFormat("yyyy-MM-dd")
    @ColumnWidth(14)
    @ApiModelProperty("有效期至（为空表示长期有效）")
    private LocalDate expireDate;

    /** 证明材料存储路径 */
    @ApiModelProperty("证明材料存储路径")
    private String fileUrl;

    /** 审核状态：0-待审核 1-已生效 2-已驳回 */
    @ApiModelProperty("审核状态数字")
    private Integer status;

    /** 审核状态中文文案 */
    @ExcelProperty("审核状态")
    @ColumnWidth(12)
    @ApiModelProperty("审核状态文案")
    private String statusText;

    /** 审核人账号 */
    @ExcelProperty("审核人")
    @ColumnWidth(14)
    @ApiModelProperty("审核人账号")
    private String auditBy;

    /** 审核时间 */
    @ExcelProperty("审核时间")
    @ColumnWidth(22)
    @ApiModelProperty("审核时间")
    private LocalDateTime auditTime;

    /** 审核意见 */
    @ExcelProperty("审核意见")
    @ColumnWidth(28)
    @ApiModelProperty("审核意见")
    private String auditRemark;

    /** 备注说明 */
    @ExcelProperty("备注")
    @ColumnWidth(28)
    @ApiModelProperty("备注说明")
    private String remark;

    /** 有效期状态：long_term-长期有效 valid-有效 expiring-临期 expired-已过期 */
    @ApiModelProperty("有效期状态")
    private String expireStatus;

    /** 有效期状态中文文案 */
    @ExcelProperty("有效期状态")
    @ColumnWidth(14)
    @ApiModelProperty("有效期状态文案")
    private String expireStatusText;

    /** 距到期天数（已过期时为负数，长期有效/无有效期时为 null） */
    @ApiModelProperty("距到期天数")
    private Long remainDays;

    /** 创建时间 */
    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;
}
