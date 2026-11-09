package com.gmp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 外部专项培训与资质证书登记实体类（对应表 tr_external_certificate）
 * <p>
 * 用于登记员工参加外部机构培训、委托培训的情况，以及特殊岗位取得的资质证书，
 * 并对证书有效期实施跟踪（超期即视为不具备上岗资质）。
 * 状态字段 status 语义：0-待审核 1-已生效 2-已驳回。
 */
@Data
@TableName("tr_external_certificate")
public class TrExternalCertificate {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 持证人用户ID（关联 sys_user.id） */
    private Long userId;

    /** 类型：1-外部培训 2-资质证书 */
    private Integer certType;

    /** 证书/培训名称 */
    private String certName;

    /** 证书编号（外部培训可留空） */
    private String certNo;

    /** 发证机构/培训机构 */
    private String issuer;

    /** 发证日期 */
    private LocalDate issueDate;

    /** 有效期至（为空表示长期有效） */
    private LocalDate expireDate;

    /** 证明材料存储路径（扫描件/照片） */
    private String fileUrl;

    /** 审核状态：0-待审核 1-已生效 2-已驳回 */
    private Integer status;

    /** 审核人账号 */
    private String auditBy;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 审核意见（驳回时必填） */
    private String auditRemark;

    /** 备注说明 */
    private String remark;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入/更新时自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建人（插入时自动填充为当前登录账号 username） */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /** 更新人（插入/更新时自动填充为当前登录账号 username） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /** 逻辑删除标记：0-未删除 1-已删除（MyBatis-Plus 自动过滤） */
    @TableLogic
    private Integer deleted;
}
