package com.gmp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 线下培训签到记录实体类（对应表 tr_attendance_record）
 * <p>
 * 每条记录代表一名学员对某一场线下培训的签到行为，是 GMP 指南 3.2.4.10 节
 * 「培训文件与记录应予以保存」要求的具体落地凭证。
 * <p>
 * 表上建有 (training_id, user_id) 唯一索引，用于数据库层兜底防止重复签到。
 */
@Data
@TableName("tr_attendance_record")
public class TrAttendanceRecord {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 线下培训场次ID */
    private Long trainingId;

    /** 签到学员用户ID */
    private Long userId;

    /** 签到时间 */
    private LocalDateTime signTime;

    /** 签到方式：1-学员自主签到 2-管理员补签 */
    private Integer signType;

    /** 签到来源IP（自主签到时记录，便于异常签到排查） */
    private String signIp;

    /** 备注（补签原因等） */
    private String remark;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
