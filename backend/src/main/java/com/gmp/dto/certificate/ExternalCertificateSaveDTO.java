package com.gmp.dto.certificate;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 外部专项培训 / 资质证书保存参数 DTO（管理端登记，新增与编辑共用）
 */
@Data
@ApiModel("资质证书保存参数")
public class ExternalCertificateSaveDTO {

    /** 记录ID：新增时为空，编辑时由 Controller 以路径参数覆盖，防止请求体篡改 */
    @ApiModelProperty("记录ID（编辑时必填）")
    private Long id;

    /** 持证人用户ID，管理端登记时必填 */
    @ApiModelProperty(value = "持证人用户ID", required = true)
    @NotNull(message = "请选择持证人")
    private Long userId;

    /** 类型：1-外部培训 2-资质证书 */
    @ApiModelProperty(value = "类型：1-外部培训 2-资质证书", required = true)
    @NotNull(message = "请选择证书类型")
    private Integer certType;

    /** 证书/培训名称 */
    @ApiModelProperty(value = "证书/培训名称", required = true)
    @NotBlank(message = "证书/培训名称不能为空")
    @Size(max = 200, message = "证书/培训名称长度不能超过200个字符")
    private String certName;

    /** 证书编号 */
    @ApiModelProperty("证书编号")
    @Size(max = 100, message = "证书编号长度不能超过100个字符")
    private String certNo;

    /** 发证机构/培训机构 */
    @ApiModelProperty("发证机构/培训机构")
    @Size(max = 200, message = "发证机构长度不能超过200个字符")
    private String issuer;

    /** 发证日期 */
    @ApiModelProperty("发证日期")
    private LocalDate issueDate;

    /** 有效期至（为空表示长期有效） */
    @ApiModelProperty("有效期至（为空表示长期有效）")
    private LocalDate expireDate;

    /** 证明材料存储路径 */
    @ApiModelProperty("证明材料存储路径")
    @Size(max = 500, message = "证明材料路径长度不能超过500个字符")
    private String fileUrl;

    /** 备注说明 */
    @ApiModelProperty("备注说明")
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
