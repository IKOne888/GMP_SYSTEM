package com.gmp.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gmp.common.BusinessException;
import com.gmp.common.ErrorCode;
import com.gmp.dto.plan.PlanCourseDTO;
import com.gmp.dto.plan.PlanCourseVO;
import com.gmp.dto.plan.PlanListVO;
import com.gmp.dto.plan.PlanSaveDTO;
import com.gmp.dto.plan.PlanVO;
import com.gmp.entity.SysDepartment;
import com.gmp.entity.SysUser;
import com.gmp.entity.TrCourse;
import com.gmp.entity.TrPlanCourse;
import com.gmp.entity.TrTrainingPlan;
import com.gmp.entity.TrTrainingTask;
import com.gmp.mapper.SysDepartmentMapper;
import com.gmp.mapper.SysUserMapper;
import com.gmp.mapper.TrCourseMapper;
import com.gmp.mapper.TrPlanCourseMapper;
import com.gmp.mapper.TrTrainingPlanMapper;
import com.gmp.mapper.TrTrainingTaskMapper;
import com.gmp.service.SysMessageService;
import com.gmp.service.TrTrainingPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 培训计划 Service 实现类
 * 负责计划的状态流转（草稿/待审批/已通过/已驳回），
 * 以及「制定人、制定部门、关联课程名称」等展示字段的回填。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrTrainingPlanServiceImpl extends ServiceImpl<TrTrainingPlanMapper, TrTrainingPlan>
        implements TrTrainingPlanService {

    /** 计划-课程关联 Mapper */
    private final TrPlanCourseMapper planCourseMapper;
    /** 用户 Mapper（用于回填制定人姓名与部门） */
    private final SysUserMapper sysUserMapper;
    /** 部门 Mapper（用于回填制定部门名称） */
    private final SysDepartmentMapper sysDepartmentMapper;
    /** 课程 Mapper（用于回填课程名称） */
    private final TrCourseMapper trCourseMapper;
    /** 培训任务 Mapper（审批通过后按目标部门自动下发任务） */
    private final TrTrainingTaskMapper trTrainingTaskMapper;
    /** 站内信 Service（任务下发后向学员推送通知） */
    private final SysMessageService sysMessageService;

    /** 状态常量：草稿 */
    private static final int STATUS_DRAFT = 0;
    /** 状态常量：待审批 */
    private static final int STATUS_PENDING = 1;
    /** 状态常量：已通过 */
    private static final int STATUS_APPROVED = 2;
    /** 状态常量：已驳回 */
    private static final int STATUS_REJECTED = 3;
    /** 状态常量：已发布（审批通过并完成下发后的终态） */
    private static final int STATUS_PUBLISHED = 4;
    /** 培训任务状态：未开始 */
    private static final int TASK_STATUS_NOT_STARTED = 0;
    /** 用户状态：启用 */
    private static final int USER_STATUS_ENABLED = 1;

    @Override
    public Page<PlanVO> pagePlans(Integer pageNum, Integer pageSize, String name, String approvalStatus, Integer year) {
        Page<TrTrainingPlan> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TrTrainingPlan> wrapper = new LambdaQueryWrapper<>();
        // 计划名称模糊查询
        if (StrUtil.isNotBlank(name)) {
            wrapper.like(TrTrainingPlan::getName, name);
        }
        // 审批状态过滤（字符串 → 状态数字集合）
        List<Integer> statusList = parseStatusList(approvalStatus);
        if (statusList != null) {
            wrapper.in(TrTrainingPlan::getStatus, statusList);
        }
        // 年度过滤
        if (year != null) {
            wrapper.eq(TrTrainingPlan::getYear, year);
        }
        wrapper.orderByDesc(TrTrainingPlan::getCreateTime);

        Page<TrTrainingPlan> entityPage = baseMapper.selectPage(page, wrapper);

        // 构建返回 Page
        Page<PlanVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        List<PlanVO> voList = entityPage.getRecords().stream().map(this::toBaseVO).collect(Collectors.toList());
        // 回填制定人 / 制定部门
        fillCreatorInfo(entityPage.getRecords(), voList);
        // 回填关联课程
        fillCourses(voList);
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public List<PlanListVO> listPlans() {
        // 下拉列表仅返回未删除的计划，按创建时间倒序
        List<TrTrainingPlan> plans = baseMapper.selectList(
                new LambdaQueryWrapper<TrTrainingPlan>().orderByDesc(TrTrainingPlan::getCreateTime));
        return plans.stream().map(plan -> {
            PlanListVO vo = new PlanListVO();
            vo.setId(plan.getId());
            vo.setName(plan.getName());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addPlan(PlanSaveDTO dto) {
        // 校验计划编号唯一
        checkCodeUnique(dto.getCode(), null);
        TrTrainingPlan plan = buildEntity(dto);
        plan.setStatus(STATUS_DRAFT);
        baseMapper.insert(plan);
        // 保存关联课程
        savePlanCourses(plan.getId(), dto.getCourses());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlan(PlanSaveDTO dto) {
        TrTrainingPlan exist = baseMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "培训计划不存在");
        }
        // 仅草稿或已驳回状态允许编辑，已提交审批的计划锁定
        if (exist.getStatus() != null && exist.getStatus() != STATUS_DRAFT && exist.getStatus() != STATUS_REJECTED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前状态不允许编辑，请先驳回至草稿");
        }
        // 校验计划编号唯一（排除自身）
        checkCodeUnique(dto.getCode(), dto.getId());

        TrTrainingPlan plan = buildEntity(dto);
        plan.setId(dto.getId());
        baseMapper.updateById(plan);

        // 先清除旧的课程关联，再重新写入
        planCourseMapper.delete(new LambdaQueryWrapper<TrPlanCourse>().eq(TrPlanCourse::getPlanId, dto.getId()));
        savePlanCourses(dto.getId(), dto.getCourses());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitApproval(Long id) {
        TrTrainingPlan plan = baseMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "培训计划不存在");
        }
        // 仅草稿（0）或已驳回（3）可提交审批
        if (plan.getStatus() == null || (plan.getStatus() != STATUS_DRAFT && plan.getStatus() != STATUS_REJECTED)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "只有草稿或已驳回的计划可以提交审批");
        }
        TrTrainingPlan update = new TrTrainingPlan();
        update.setId(id);
        update.setStatus(STATUS_PENDING);
        update.setSubmitTime(LocalDateTime.now());
        // 记录提交人用户ID，供审批环节校验「审批人 ≠ 提交人」，防止自提自批
        update.setSubmitUserId(StpUtil.getLoginIdAsLong());
        baseMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String comment) {
        TrTrainingPlan plan = baseMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "培训计划不存在");
        }
        // 仅待审批（1）状态可通过
        if (plan.getStatus() == null || plan.getStatus() != STATUS_PENDING) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "只有待审批的计划可以审批通过");
        }
        // 自提自批校验：审批人不得与提交人相同，保证审批独立性
        assertNotSelfApprove(plan);
        // 审批通过前必须已关联课程，否则下发环节无内容可发，计划将空转
        List<TrPlanCourse> planCourses = planCourseMapper.selectList(
                new LambdaQueryWrapper<TrPlanCourse>().eq(TrPlanCourse::getPlanId, id));
        if (planCourses.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请先为计划绑定课程再提交审批");
        }

        // 1. 状态置为「已发布」，记录审批人与审批意见（已发布 = 审批通过并完成下发）
        TrTrainingPlan update = new TrTrainingPlan();
        update.setId(id);
        update.setStatus(STATUS_PUBLISHED);
        update.setApproveTime(LocalDateTime.now());
        update.setApproveUserId(StpUtil.getLoginIdAsLong());
        update.setApproveRemark(comment);
        baseMapper.updateById(update);

        // 2. 按「目标部门 → 在岗员工 → 计划课程」自动下发培训任务并推送站内信
        int created = publishTasks(plan, planCourses);
        log.info("培训计划审批通过：计划ID={}, 计划名称={}, 自动生成任务数={}", id, plan.getName(), created);
    }

    /**
     * 审批通过后自动下发培训任务
     * <p>
     * 下发范围：计划「培训对象部门」下的全部在岗员工；下发内容：计划关联的每一门课程。
     * 幂等规则：同一员工 + 同一课程若已存在未删除的任务，则跳过，避免重复下发。
     *
     * @param plan        计划实体（用于取目标部门、结束日期等）
     * @param planCourses 计划关联课程行
     * @return 本次实际生成的任务条数
     */
    private int publishTasks(TrTrainingPlan plan, List<TrPlanCourse> planCourses) {
        // 解析培训对象部门ID列表（数据库中以 JSON 文本存储）
        List<Long> deptIds = StrUtil.isBlank(plan.getTargetDepartments())
                ? Collections.emptyList()
                : JSONUtil.toList(plan.getTargetDepartments(), Long.class);
        if (deptIds.isEmpty()) {
            // 未指定培训对象部门时不范围下发，仅完成计划发布
            log.info("培训计划未指定培训对象部门，跳过任务下发：计划ID={}", plan.getId());
            return 0;
        }

        // 查询目标部门下的在岗员工（status=1 且未逻辑删除，逻辑删除由 MyBatis-Plus 自动过滤）
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getDepartmentId, deptIds)
                .eq(SysUser::getStatus, USER_STATUS_ENABLED));
        if (users.isEmpty()) {
            log.info("培训计划目标部门下无在岗员工，跳过任务下发：计划ID={}", plan.getId());
            return 0;
        }

        // 截止时间取计划结束日期的当天 23:59:59；未设置结束日期则默认 30 天后
        // 注意：使用 LocalTime.MAX 会带 .999999999 纳秒，MySQL DATETIME 存储时进位为次日 00:00:00，故固定为 23:59:59
        LocalDateTime deadline = plan.getEndDate() != null
                ? plan.getEndDate().atTime(LocalTime.of(23, 59, 59))
                : LocalDateTime.now().plusDays(30);

        // 批量查询课程名称，用于任务命名与站内信内容
        Set<Long> courseIds = planCourses.stream()
                .map(TrPlanCourse::getCourseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> courseNameMap = trCourseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(TrCourse::getId, TrCourse::getName, (a, b) -> a));

        int created = 0;
        // 逐「员工 × 课程」生成任务
        for (SysUser user : users) {
            for (TrPlanCourse row : planCourses) {
                Long courseId = row.getCourseId();
                if (courseId == null) {
                    continue;
                }
                // 幂等保护：同一员工同一课程已存在未删除任务时跳过
                Long exists = trTrainingTaskMapper.selectCount(new LambdaQueryWrapper<TrTrainingTask>()
                        .eq(TrTrainingTask::getUserId, user.getId())
                        .eq(TrTrainingTask::getCourseId, courseId));
                if (exists != null && exists > 0) {
                    continue;
                }

                String courseName = courseNameMap.getOrDefault(courseId, "培训课程");
                // 任务名称：计划名 + 课程名，便于学员端区分来源
                String taskName = plan.getName() + " - " + courseName;

                TrTrainingTask task = new TrTrainingTask();
                task.setName(taskName);
                task.setPlanId(plan.getId());
                task.setCourseId(courseId);
                task.setUserId(user.getId());
                // 任务类型 1-培训任务
                task.setTaskType(1);
                task.setDescription(plan.getDescription());
                task.setStatus(TASK_STATUS_NOT_STARTED);
                task.setDeadline(deadline);
                task.setVideoProgress(BigDecimal.ZERO);
                task.setRetryCount(0);
                trTrainingTaskMapper.insert(task);
                created++;

                // 任务生成后立即推送站内信，学员端消息中心与铃铛可即时看到
                sysMessageService.send(user.getId(), SysMessageService.TYPE_TASK,
                        "新培训任务通知",
                        "培训计划《" + plan.getName() + "》已审批通过，您有新的培训任务《" + courseName
                                + "》，请于 " + deadline.toLocalDate() + " 前完成学习与考试。",
                        task.getId());
            }
        }
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String comment) {
        TrTrainingPlan plan = baseMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "培训计划不存在");
        }
        // 仅待审批（1）状态可驳回
        if (plan.getStatus() == null || plan.getStatus() != STATUS_PENDING) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "只有待审批的计划可以驳回");
        }
        // 驳回必须填写原因
        if (StrUtil.isBlank(comment)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请填写驳回原因");
        }
        // 自提自批校验：驳回同样要求审批人与提交人不同
        assertNotSelfApprove(plan);
        TrTrainingPlan update = new TrTrainingPlan();
        update.setId(id);
        update.setStatus(STATUS_REJECTED);
        update.setApproveTime(LocalDateTime.now());
        update.setApproveUserId(StpUtil.getLoginIdAsLong());
        update.setApproveRemark(comment);
        baseMapper.updateById(update);
    }

    /**
     * 校验「审批人 ≠ 提交人」，防止同一账号自提自批
     * <p>
     * 兼容存量数据：历史计划 submitUserId 为 NULL 时不做拦截（无法判定提交人），
     * 仅对本次改造后新提交的计划强制执行。
     *
     * @param plan 待审批的培训计划
     */
    private void assertNotSelfApprove(TrTrainingPlan plan) {
        Long submitUserId = plan.getSubmitUserId();
        if (submitUserId == null) {
            return;
        }
        if (submitUserId.equals(StpUtil.getLoginIdAsLong())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能审批自己提交的培训计划，请由其他审批人处理");
        }
    }

    /**
     * 将保存 DTO 转换为计划实体（含培训对象部门 ID 列表的 JSON 序列化）
     *
     * @param dto 保存参数
     * @return 计划实体
     */
    private TrTrainingPlan buildEntity(PlanSaveDTO dto) {
        TrTrainingPlan plan = new TrTrainingPlan();
        plan.setCode(dto.getCode());
        plan.setName(dto.getName());
        plan.setYear(dto.getYear());
        plan.setPlanType(dto.getPlanType() == null ? 1 : dto.getPlanType());
        plan.setDescription(dto.getDescription());
        plan.setStartDate(dto.getStartDate());
        plan.setEndDate(dto.getEndDate());
        // 培训对象部门列表以 JSON 文本存储
        if (dto.getTargetDepartments() == null || dto.getTargetDepartments().isEmpty()) {
            plan.setTargetDepartments(null);
        } else {
            plan.setTargetDepartments(JSONUtil.toJsonStr(dto.getTargetDepartments()));
        }
        return plan;
    }

    /**
     * 保存计划的关联课程
     *
     * @param planId  计划ID
     * @param courses 课程行列表
     */
    private void savePlanCourses(Long planId, List<PlanCourseDTO> courses) {
        if (courses == null || courses.isEmpty()) {
            return;
        }
        for (PlanCourseDTO row : courses) {
            if (row.getCourseId() == null) {
                continue;
            }
            TrPlanCourse planCourse = new TrPlanCourse();
            planCourse.setPlanId(planId);
            planCourse.setCourseId(row.getCourseId());
            planCourse.setTrainer(row.getTrainer());
            planCourse.setPlannedDate(row.getPlannedDate());
            planCourseMapper.insert(planCourse);
        }
    }

    /**
     * 校验计划编号唯一
     *
     * @param code      计划编号
     * @param excludeId 需要排除的计划ID（编辑时传入自身ID，新增时为 null）
     */
    private void checkCodeUnique(String code, Long excludeId) {
        if (StrUtil.isBlank(code)) {
            return;
        }
        LambdaQueryWrapper<TrTrainingPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrTrainingPlan::getCode, code);
        if (excludeId != null) {
            wrapper.ne(TrTrainingPlan::getId, excludeId);
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_KEY, "计划编号已存在");
        }
    }

    /**
     * 实体转基础 VO（完成状态枚举与部门 JSON 的反向转换）
     *
     * @param plan 计划实体
     * @return 计划 VO
     */
    private PlanVO toBaseVO(TrTrainingPlan plan) {
        PlanVO vo = new PlanVO();
        vo.setId(plan.getId());
        vo.setCode(plan.getCode());
        vo.setName(plan.getName());
        vo.setYear(plan.getYear());
        vo.setPlanType(plan.getPlanType());
        vo.setDescription(plan.getDescription());
        vo.setStartDate(plan.getStartDate());
        vo.setEndDate(plan.getEndDate());
        vo.setStatus(plan.getStatus());
        vo.setApprovalStatus(statusToApproval(plan.getStatus()));
        vo.setSubmitTime(plan.getSubmitTime());
        vo.setApproveTime(plan.getApproveTime());
        vo.setApproveRemark(plan.getApproveRemark());
        vo.setCreateTime(plan.getCreateTime());
        // 解析培训对象部门 JSON 为数组
        if (StrUtil.isNotBlank(plan.getTargetDepartments())) {
            vo.setTargetDepartments(JSONUtil.toList(plan.getTargetDepartments(), Long.class));
        } else {
            vo.setTargetDepartments(new ArrayList<>());
        }
        return vo;
    }

    /**
     * 批量回填「制定人姓名」与「制定部门名称」
     * <p>
     * 依据 createBy（用户名）查询用户真实姓名与所属部门，再查询部门名称，避免 N+1 查询。
     *
     * @param plans 计划实体列表
     * @param vos   对应的 VO 列表（顺序一致）
     */
    private void fillCreatorInfo(List<TrTrainingPlan> plans, List<PlanVO> vos) {
        // 收集全部创建人用户名
        Set<String> usernames = plans.stream()
                .map(TrTrainingPlan::getCreateBy)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        if (usernames.isEmpty()) {
            return;
        }
        // 批量查询用户
        List<SysUser> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>().in(SysUser::getUsername, usernames));
        Map<String, SysUser> userMap = users.stream()
                .collect(Collectors.toMap(SysUser::getUsername, u -> u, (a, b) -> a));

        // 批量查询部门
        Set<Long> deptIds = users.stream()
                .map(SysUser::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> deptNameMap = new HashMap<>();
        if (!deptIds.isEmpty()) {
            List<SysDepartment> depts = sysDepartmentMapper.selectBatchIds(deptIds);
            deptNameMap = depts.stream()
                    .collect(Collectors.toMap(SysDepartment::getId, SysDepartment::getName, (a, b) -> a));
        }

        // 按顺序回填
        for (int i = 0; i < plans.size(); i++) {
            PlanVO vo = vos.get(i);
            SysUser user = userMap.get(plans.get(i).getCreateBy());
            if (user != null) {
                vo.setCreatorName(user.getRealName());
                if (user.getDepartmentId() != null) {
                    vo.setDepartmentName(deptNameMap.get(user.getDepartmentId()));
                }
            }
        }
    }

    /**
     * 批量回填计划的关联课程（含课程名称）
     *
     * @param vos 计划 VO 列表
     */
    private void fillCourses(List<PlanVO> vos) {
        if (vos.isEmpty()) {
            return;
        }
        // 收集计划ID
        List<Long> planIds = vos.stream().map(PlanVO::getId).collect(Collectors.toList());
        // 查询计划-课程关联
        List<TrPlanCourse> rows = planCourseMapper.selectList(
                new LambdaQueryWrapper<TrPlanCourse>().in(TrPlanCourse::getPlanId, planIds));
        if (rows.isEmpty()) {
            vos.forEach(vo -> vo.setCourses(new ArrayList<>()));
            return;
        }
        // 批量查询课程名称
        Set<Long> courseIds = rows.stream()
                .map(TrPlanCourse::getCourseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> courseNameMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            List<TrCourse> courses = trCourseMapper.selectBatchIds(courseIds);
            courseNameMap = courses.stream()
                    .collect(Collectors.toMap(TrCourse::getId, TrCourse::getName, (a, b) -> a));
        }

        // 按计划分组
        Map<Long, List<PlanCourseVO>> grouped = new HashMap<>();
        for (TrPlanCourse row : rows) {
            PlanCourseVO courseVO = new PlanCourseVO();
            courseVO.setId(row.getId());
            courseVO.setCourseId(row.getCourseId());
            courseVO.setCourseName(courseNameMap.get(row.getCourseId()));
            courseVO.setTrainer(row.getTrainer());
            courseVO.setPlannedDate(row.getPlannedDate());
            grouped.computeIfAbsent(row.getPlanId(), k -> new ArrayList<>()).add(courseVO);
        }
        // 回填到 VO
        for (PlanVO vo : vos) {
            vo.setCourses(grouped.getOrDefault(vo.getId(), new ArrayList<>()));
        }
    }

    /**
     * 状态数字转前端审批状态字符串
     */
    private String statusToApproval(Integer status) {
        if (status == null) {
            return "draft";
        }
        switch (status) {
            case STATUS_PENDING:
                return "pending";
            case STATUS_APPROVED:
            case 4: // 已发布同样视为"已通过"
                return "approved";
            case STATUS_REJECTED:
                return "rejected";
            default:
                return "draft";
        }
    }

    /**
     * 前端审批状态字符串转状态数字集合（空返回 null 表示不过滤）
     * <p>
     * approved 需同时匹配「已通过(2)」与「已发布(4)」两种状态。
     */
    private List<Integer> parseStatusList(String approvalStatus) {
        if (StrUtil.isBlank(approvalStatus)) {
            return null;
        }
        switch (approvalStatus) {
            case "draft":
                return Collections.singletonList(STATUS_DRAFT);
            case "pending":
                return Collections.singletonList(STATUS_PENDING);
            case "approved":
                return java.util.Arrays.asList(STATUS_APPROVED, 4);
            case "rejected":
                return Collections.singletonList(STATUS_REJECTED);
            default:
                throw new BusinessException(ErrorCode.PARAM_ERROR, "审批状态不合法");
        }
    }
}
