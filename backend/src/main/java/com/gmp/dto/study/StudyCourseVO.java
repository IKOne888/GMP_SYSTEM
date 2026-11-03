package com.gmp.dto.study;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程中心课程 VO
 * <p>
 * 列表页仅填充基础字段与报名状态；详情页额外填充培训目标、资料/视频/题目数量与部门岗位名称。
 */
@Data
@ApiModel("学员端课程信息")
public class StudyCourseVO {

    @ApiModelProperty("课程ID")
    private Long id;

    @ApiModelProperty("课程名称")
    private String name;

    @ApiModelProperty("课程编码")
    private String code;

    @ApiModelProperty("课程分类名称")
    private String categoryName;

    @ApiModelProperty("SOP编号")
    private String sopCode;

    @ApiModelProperty("SOP版本号")
    private String sopVersion;

    @ApiModelProperty("培训学时")
    private BigDecimal trainingHours;

    @ApiModelProperty("及格分数")
    private Integer passScore;

    @ApiModelProperty("复训周期（天）")
    private Integer retrainPeriod;

    @ApiModelProperty("培训目标")
    private String objective;

    @ApiModelProperty("是否已报名（存在培训任务）")
    private Boolean enrolled;

    @ApiModelProperty("已报名时的任务ID（未报名为 null）")
    private Long taskId;

    @ApiModelProperty("资料数量（仅详情返回）")
    private Integer fileCount;

    @ApiModelProperty("视频数量（仅详情返回）")
    private Integer videoCount;

    @ApiModelProperty("题目数量（仅详情返回）")
    private Integer questionCount;

    @ApiModelProperty("所属部门名称（仅详情返回）")
    private String departmentName;

    @ApiModelProperty("适用岗位名称（仅详情返回）")
    private String positionName;

    @ApiModelProperty("课程关联的第一个启用视频ID（列表/详情均返回，前端用于加载视频封面）")
    private Long firstVideoId;
}
