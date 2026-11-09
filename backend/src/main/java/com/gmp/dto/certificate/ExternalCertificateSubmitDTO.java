package com.gmp.dto.certificate;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 学员端提交外部专项培训 / 资质证书登记的 DTO
 * <p>
 * 与管理端保存参数的区别：持证人固定为当前登录用户，不接受客户端传入 userId，
 * 提交后状态固定为「待审核」，须经培训管理员或 QA 审核后才生效。
 */
@Data
@ApiModel("学员资质证书提交参数")
public class ExternalCertificateSubmitDTO {

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
