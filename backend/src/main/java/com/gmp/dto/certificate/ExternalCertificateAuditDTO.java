package com.gmp.dto.certificate;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 资质证书审核参数 DTO
 */
@Data
@ApiModel("资质证书审核参数")
public class ExternalCertificateAuditDTO {

    /** 审核结论：1-通过（已生效） 2-驳回 */
    @ApiModelProperty(value = "审核结论：1-通过 2-驳回", required = true)
    @NotNull(message = "请选择审核结论")
    private Integer status;

    /** 审核意见（驳回时必填） */
    @ApiModelProperty("审核意见（驳回时必填）")
    @Size(max = 500, message = "审核意见长度不能超过500个字符")
    private String auditRemark;
}
