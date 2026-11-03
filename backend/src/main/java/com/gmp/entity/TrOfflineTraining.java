package com.gmp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 线下培训场次实体类（对应表 tr_offline_training）
 * <p>
 * 用于登记 GMP 指南 3.2.4.8 节所列线下培训方式（课堂学习、岗位实际操作学习、团队学习等）的
 * 具体场次信息，并与电子签到配合，形成可追溯的培训出勤记录。
 * <p>
 * 状态字段 status 语义：0-未开始 1-进行中 2-已结束 3-已取消。
 * 前端展示的「可签到 / 已结束」等阶段由 Service 层根据时间窗口动态计算，避免状态与时间不一致。
 */
@Data
@TableName("tr_offline_training")
public class TrOfflineTraining {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联培训计划ID（可为空，表示独立组织的线下培训） */
    private Long planId;

    /** 关联培训课程ID（可为空） */
    private Long courseId;

    /** 培训主题 */
    private String title;

    /** 培训师/主讲人 */
    private String trainer;

    /** 培训地点 */
    private String location;

    /** 培训开始时间 */
    private LocalDateTime startTime;

    /** 培训结束时间 */
    private LocalDateTime endTime;

    /** 签到开始时间（为空时取培训开始前30分钟） */
    private LocalDateTime signStartTime;

    /** 签到结束时间（为空时取培训结束时间） */
    private LocalDateTime signEndTime;

    /** 签到码（6位数字，由系统生成，学员凭码自主签到） */
    private String signCode;

    /** 状态：0-未开始 1-进行中 2-已结束 3-已取消 */
    private Integer status;

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
