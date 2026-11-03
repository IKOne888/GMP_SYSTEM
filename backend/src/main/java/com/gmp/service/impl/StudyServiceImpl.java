package com.gmp.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gmp.common.BusinessException;
import com.gmp.common.ErrorCode;
import com.gmp.dto.UserInfoVO;
import com.gmp.dto.exam.PaperRuleDTO;
import com.gmp.dto.exam.QuestionOptionDTO;
import com.gmp.dto.study.AnalysisOverviewVO;
import com.gmp.dto.study.CalendarDayVO;
import com.gmp.dto.study.CertificateVO;
import com.gmp.dto.study.CourseFileVO;
import com.gmp.dto.study.ExamInfoVO;
import com.gmp.dto.study.ExamQuestionVO;
import com.gmp.dto.study.ExamRecordVO;
import com.gmp.dto.study.ExamResultItemVO;
import com.gmp.dto.study.ExamResultVO;
import com.gmp.dto.study.ExamStartVO;
import com.gmp.dto.study.ExamSubmitDTO;
import com.gmp.dto.study.MaterialVO;
import com.gmp.dto.study.PasswordUpdateDTO;
import com.gmp.dto.study.ProfileUpdateDTO;
import com.gmp.dto.study.ProgressReportDTO;
import com.gmp.dto.study.StudyCourseVO;
import com.gmp.dto.study.StudyRecordVO;
import com.gmp.dto.study.StudyStatsVO;
import com.gmp.dto.study.StudyTaskVO;
import com.gmp.dto.study.TaskDetailVO;
import com.gmp.dto.study.VideoStudyVO;
import com.gmp.dto.study.WrongPracticeDTO;
import com.gmp.dto.study.WrongPracticeResultVO;
import com.gmp.dto.study.WrongQuestionStatsVO;
import com.gmp.dto.study.WrongQuestionVO;
import com.gmp.entity.SysDepartment;
import com.gmp.entity.SysMessage;
import com.gmp.entity.SysPosition;
import com.gmp.entity.SysRole;
import com.gmp.entity.SysUser;
import com.gmp.entity.SysUserRole;
import com.gmp.entity.TrCourse;
import com.gmp.entity.TrCourseCategory;
import com.gmp.entity.TrCourseFile;
import com.gmp.entity.TrCourseVideo;
import com.gmp.entity.TrExamAnswer;
import com.gmp.entity.TrExamPaper;
import com.gmp.entity.TrExamRecord;
import com.gmp.entity.TrPaperQuestion;
import com.gmp.entity.TrQuestion;
import com.gmp.entity.TrTrainingPlan;
import com.gmp.entity.TrTrainingTask;
import com.gmp.entity.TrVideoStudyRecord;
import com.gmp.entity.TrWrongQuestion;
import com.gmp.mapper.SysDepartmentMapper;
import com.gmp.mapper.SysMessageMapper;
import com.gmp.mapper.SysPositionMapper;
import com.gmp.mapper.SysRoleMapper;
import com.gmp.mapper.SysUserMapper;
import com.gmp.mapper.SysUserRoleMapper;
import com.gmp.mapper.TrCourseCategoryMapper;
import com.gmp.mapper.TrCourseFileMapper;
import com.gmp.mapper.TrCourseMapper;
import com.gmp.mapper.TrCourseVideoMapper;
import com.gmp.mapper.TrExamAnswerMapper;
import com.gmp.mapper.TrExamPaperMapper;
import com.gmp.mapper.TrExamRecordMapper;
import com.gmp.mapper.TrPaperQuestionMapper;
import com.gmp.mapper.TrQuestionMapper;
import com.gmp.mapper.TrTrainingPlanMapper;
import com.gmp.mapper.TrTrainingTaskMapper;
import com.gmp.mapper.TrVideoStudyRecordMapper;
import com.gmp.mapper.TrWrongQuestionMapper;
import com.gmp.service.StudyService;
import com.gmp.service.TrTrainingTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 学员端业务 Service 实现类
 * <p>
 * 设计要点：
 * 1. 数据隔离：所有查询/写入强制以当前登录用户ID为条件，杜绝越权；
 * 2. 状态计算：任务「已逾期」为动态计算结果（未完成且已过截止时间），不落库；
 * 3. 进度计算：视频进度按「时长加权」回填任务整体进度，绝对值覆盖保证幂等；
 * 4. 考试判分：单选/多选/判断/填空自动判分；简答题采用「参考答案包含匹配」的启发式判分；
 * 5. 题目快照：开考时将所选题目固化到 tr_exam_answer，保证刷新后题目集合稳定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudyServiceImpl implements StudyService {

    // ==================== 任务状态常量 ====================
    /** 待学习 */
    private static final int STATUS_NOT_STARTED = 0;
    /** 学习中 */
    private static final int STATUS_STUDYING = 1;
    /** 待考试 */
    private static final int STATUS_WAITING_EXAM = 2;
    /** 已完成 */
    private static final int STATUS_COMPLETED = 3;
    /** 已逾期（动态计算，不落库） */
    private static final int STATUS_OVERDUE = 4;
    /** 展示状态：待主管审核（status=3 且 reviewStatus=0） */
    private static final int STATUS_DISPLAY_PENDING_LEADER = 5;
    /** 展示状态：待QA复核（status=3 且 reviewStatus=1） */
    private static final int STATUS_DISPLAY_PENDING_QA = 6;
    /** 展示状态：已退回（reviewStatus=3，需重新学习考试） */
    private static final int STATUS_DISPLAY_REJECTED = 7;

    // ==================== 审核状态常量（与 tr_training_task.review_status 对应） ====================
    /** 审核状态：待主管审核 */
    private static final int REVIEW_PENDING_LEADER = 0;
    /** 审核状态：待QA复核 */
    private static final int REVIEW_PENDING_QA = 1;
    /** 审核状态：已复核通过 */
    private static final int REVIEW_APPROVED = 2;
    /** 审核状态：已退回 */
    private static final int REVIEW_REJECTED = 3;

    /** 考试记录状态：考试中 */
    private static final int EXAM_STATUS_IN_PROGRESS = 0;
    /** 考试记录状态：已批阅 */
    private static final int EXAM_STATUS_GRADED = 2;

    /** 单视频完成阈值（百分比）：达到即视为该视频学完 */
    private static final BigDecimal VIDEO_COMPLETE_PROGRESS = new BigDecimal("95");
    /** 证书临期阈值（天）：剩余天数不超过该值视为临期 */
    private static final long CERT_EXPIRING_DAYS = 30L;
    /** 试卷状态：已发布 */
    private static final int PAPER_STATUS_PUBLISHED = 1;
    /** 任务来源：自主报名（课程中心） */
    private static final int TASK_SOURCE_SELF = 2;
    /** 日历查询最大跨度（天） */
    private static final long CALENDAR_MAX_DAYS = 62L;
    /** 日期字符串格式 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ==================== 注入依赖 ====================
    private final TrTrainingTaskMapper taskMapper;
    private final TrTrainingPlanMapper planMapper;
    private final TrCourseMapper courseMapper;
    private final TrCourseVideoMapper videoMapper;
    private final TrCourseFileMapper courseFileMapper;
    private final TrVideoStudyRecordMapper recordMapper;
    private final TrExamPaperMapper paperMapper;
    private final TrPaperQuestionMapper paperQuestionMapper;
    private final TrQuestionMapper questionMapper;
    private final TrExamRecordMapper examRecordMapper;
    private final TrExamAnswerMapper examAnswerMapper;
    private final SysMessageMapper messageMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    /** 错题本 Mapper（错题收集 / 重做 / 统计） */
    private final TrWrongQuestionMapper wrongQuestionMapper;
    /** 课程分类 Mapper（课程中心分类名称回填） */
    private final TrCourseCategoryMapper categoryMapper;
    /** 部门 Mapper（课程详情部门名称回填） */
    private final SysDepartmentMapper departmentMapper;
    /** 岗位 Mapper（课程详情岗位名称回填） */
    private final SysPositionMapper positionMapper;
    /**
     * 培训任务 Service（学员考试通过后进入「两级审核链」：置为待主管审核并通知一级审核人）
     * <p>
     * 说明：TrTrainingTaskService 不反向依赖 StudyService，故此处构造器注入不会形成循环依赖。
     */
    private final TrTrainingTaskService trainingTaskService;

    // ==================================================================
    // 模块一：我的任务
    // ==================================================================

    @Override
    public Page<StudyTaskVO> pageTasks(Integer pageNum, Integer pageSize, String status, String keyword) {
        Long userId = currentUserId();
        Page<TrTrainingTask> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TrTrainingTask> wrapper = new LambdaQueryWrapper<>();
        // 数据隔离：仅查询本人任务
        wrapper.eq(TrTrainingTask::getUserId, userId);
        // 任务名称模糊搜索
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(TrTrainingTask::getName, keyword);
        }
        // 状态筛选（含动态「已逾期」）
        applyStatusFilter(wrapper, status);
        // 按创建时间倒序，最新任务在前
        wrapper.orderByDesc(TrTrainingTask::getCreateTime);

        Page<TrTrainingTask> entityPage = taskMapper.selectPage(page, wrapper);

        Page<StudyTaskVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(this::toStudyTaskVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public StudyStatsVO taskStats() {
        Long userId = currentUserId();
        StudyStatsVO vo = new StudyStatsVO();
        vo.setTotal(countTasks(userId, null));
        vo.setNotStarted(countTasks(userId, "not_started"));
        vo.setStudying(countTasks(userId, "studying"));
        vo.setWaitingExam(countTasks(userId, "waiting_exam"));
        vo.setCompleted(countTasks(userId, "completed"));
        vo.setOverdue(countTasks(userId, "overdue"));
        // 待审核：已达标并进入两级审核链、尚未复核通过的任务数
        vo.setPendingReview(countTasks(userId, "pending_review"));
        // 已退回：审核未通过、需重新学习考试的任务数（使各分类之和与 total 保持一致）
        vo.setRejected(countTasks(userId, "rejected"));
        return vo;
    }

    @Override
    public TaskDetailVO taskDetail(Long taskId) {
        Long userId = currentUserId();
        TrTrainingTask task = requireOwnTask(taskId, userId);
        TrCourse course = task.getCourseId() == null ? null : courseMapper.selectById(task.getCourseId());

        // 课程视频与本人学习记录
        List<TrCourseVideo> videos = listCourseVideos(course == null ? null : course.getId());
        Map<Long, TrVideoStudyRecord> recordMap = mapRecords(taskId, userId);

        // 懒同步状态：若视频已全部学完（或课程无视频），自动推进到「待考试」
        if (task.getStatus() != null && task.getStatus() < STATUS_WAITING_EXAM
                && isAllVideoCompleted(videos, recordMap)) {
            task.setStatus(STATUS_WAITING_EXAM);
            TrTrainingTask update = new TrTrainingTask();
            update.setId(task.getId());
            update.setStatus(STATUS_WAITING_EXAM);
            taskMapper.updateById(update);
        }

        TaskDetailVO vo = new TaskDetailVO();
        // ---- 任务基础字段 ----
        int displayStatus = displayStatusCode(task);
        vo.setId(task.getId());
        vo.setName(task.getName());
        vo.setDescription(task.getDescription());
        vo.setDeadline(task.getDeadline());
        vo.setStatusCode(displayStatus);
        vo.setStatus(statusKey(displayStatus));
        // 审核维度字段：状态文案 + 退回意见，供详情页展示审核进度与退回原因
        vo.setReviewStatus(task.getReviewStatus());
        vo.setReviewStatusText(reviewStatusText(task.getReviewStatus()));
        vo.setRejectRemark(resolveRejectRemark(task));
        vo.setVideoProgress(task.getVideoProgress());
        vo.setStudyDuration(task.getStudyDuration());
        vo.setExamScore(task.getExamScore());
        vo.setExamPass(task.getExamPass());
        vo.setCreateTime(task.getCreateTime());
        // 计划名称回填
        if (task.getPlanId() != null) {
            TrTrainingPlan plan = planMapper.selectById(task.getPlanId());
            if (plan != null) {
                vo.setPlanName(plan.getName());
            }
        }
        // ---- 课程字段 ----
        if (course != null) {
            vo.setCourseId(course.getId());
            vo.setCourseName(course.getName());
            vo.setCourseCode(course.getCode());
            vo.setSopCode(course.getSopCode());
            vo.setSopVersion(course.getSopVersion());
            vo.setTrainingObjective(course.getTrainingObjective());
            vo.setTrainingHours(course.getTrainingHours());
            vo.setPassScore(course.getPassScore());
            vo.setRetrainPeriod(course.getRetrainPeriod());
        }
        // ---- 视频列表（含本人进度） ----
        vo.setVideos(videos.stream().map(v -> toVideoStudyVO(v, recordMap.get(v.getId()))).collect(Collectors.toList()));
        // ---- 课程资料 ----
        vo.setFiles(course == null ? new ArrayList<>() : listCourseFiles(course.getId()));
        // ---- 考试信息 ----
        ExamInfoVO examInfo = buildExamInfo(task, course, videos, recordMap);
        vo.setExam(examInfo);
        vo.setCanExam(examInfo != null && Boolean.TRUE.equals(examInfo.getCanExam()));
        return vo;
    }

    @Override
    public List<VideoStudyVO> taskVideos(Long taskId) {
        Long userId = currentUserId();
        TrTrainingTask task = requireOwnTask(taskId, userId);
        List<TrCourseVideo> videos = listCourseVideos(task.getCourseId());
        Map<Long, TrVideoStudyRecord> recordMap = mapRecords(taskId, userId);
        return videos.stream().map(v -> toVideoStudyVO(v, recordMap.get(v.getId()))).collect(Collectors.toList());
    }

    // ==================================================================
    // 模块二：在线学习
    // ==================================================================

    @Override
    public VideoStudyVO videoRecord(Long taskId, Long videoId) {
        Long userId = currentUserId();
        requireOwnTask(taskId, userId);
        TrCourseVideo video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "视频课件不存在");
        }
        TrVideoStudyRecord record = recordMapper.selectOne(new LambdaQueryWrapper<TrVideoStudyRecord>()
                .eq(TrVideoStudyRecord::getTaskId, taskId)
                .eq(TrVideoStudyRecord::getVideoId, videoId)
                .eq(TrVideoStudyRecord::getUserId, userId));
        return toVideoStudyVO(video, record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportProgress(ProgressReportDTO dto) {
        Long userId = currentUserId();
        TrTrainingTask task = requireOwnTask(dto.getTaskId(), userId);
        TrCourseVideo video = videoMapper.selectById(dto.getVideoId());
        if (video == null || !Objects.equals(video.getCourseId(), task.getCourseId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "视频与培训任务不匹配");
        }

        // 规范化上报值：进度限制在 0~100
        BigDecimal progress = dto.getProgress() == null ? BigDecimal.ZERO : dto.getProgress();
        if (progress.compareTo(BigDecimal.ZERO) < 0) {
            progress = BigDecimal.ZERO;
        }
        if (progress.compareTo(new BigDecimal("100")) > 0) {
            progress = new BigDecimal("100");
        }
        int lastPosition = dto.getLastPosition() == null ? 0 : Math.max(dto.getLastPosition(), 0);
        int studyDuration = dto.getStudyDuration() == null ? 0 : Math.max(dto.getStudyDuration(), 0);
        int completed = progress.compareTo(VIDEO_COMPLETE_PROGRESS) >= 0 ? 1 : 0;

        // 查询已有学习记录，做幂等更新（相同数据不重复写库，降低数据库压力）
        TrVideoStudyRecord exist = recordMapper.selectOne(new LambdaQueryWrapper<TrVideoStudyRecord>()
                .eq(TrVideoStudyRecord::getTaskId, task.getId())
                .eq(TrVideoStudyRecord::getVideoId, video.getId())
                .eq(TrVideoStudyRecord::getUserId, userId));
        boolean changed = exist == null
                || exist.getVideoProgress() == null || exist.getVideoProgress().compareTo(progress) != 0
                || !Objects.equals(exist.getLastPosition(), lastPosition)
                || !Objects.equals(exist.getStudyDuration(), studyDuration);
        if (!changed) {
            return;
        }
        if (exist == null) {
            exist = new TrVideoStudyRecord();
            exist.setTaskId(task.getId());
            exist.setVideoId(video.getId());
            exist.setUserId(userId);
            exist.setStudyDuration(studyDuration);
            exist.setVideoProgress(progress);
            exist.setLastPosition(lastPosition);
            exist.setIsCompleted(completed);
            exist.setLastStudyTime(LocalDateTime.now());
            recordMapper.insert(exist);
        } else {
            exist.setStudyDuration(studyDuration);
            exist.setVideoProgress(progress);
            exist.setLastPosition(lastPosition);
            exist.setIsCompleted(completed);
            exist.setLastStudyTime(LocalDateTime.now());
            recordMapper.updateById(exist);
        }

        // 回填任务整体进度：视频进度按时长加权，学习时长取各视频之和
        List<TrCourseVideo> videos = listCourseVideos(task.getCourseId());
        Map<Long, TrVideoStudyRecord> recordMap = mapRecords(task.getId(), userId);
        BigDecimal taskProgress = calcWeightedProgress(videos, recordMap);
        int totalDuration = recordMap.values().stream()
                .mapToInt(r -> r.getStudyDuration() == null ? 0 : r.getStudyDuration()).sum();

        TrTrainingTask update = new TrTrainingTask();
        update.setId(task.getId());
        update.setVideoProgress(taskProgress);
        update.setStudyDuration(totalDuration);
        // 首次上报进入「学习中」并记录开始时间
        if (task.getStatus() != null && task.getStatus() == STATUS_NOT_STARTED) {
            update.setStatus(STATUS_STUDYING);
            if (task.getStartTime() == null) {
                update.setStartTime(LocalDateTime.now());
            }
        }
        // 所有视频学完 → 进入「待考试」
        if (task.getStatus() == null || task.getStatus() < STATUS_COMPLETED) {
            if (isAllVideoCompleted(videos, recordMap)) {
                update.setStatus(STATUS_WAITING_EXAM);
            }
        }
        taskMapper.updateById(update);
    }

    @Override
    public List<CourseFileVO> courseFiles(Long courseId) {
        if (courseMapper.selectById(courseId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        return listCourseFiles(courseId);
    }

    // ==================================================================
    // 模块三：在线考试
    // ==================================================================

    @Override
    public ExamInfoVO examInfo(Long taskId) {
        Long userId = currentUserId();
        TrTrainingTask task = requireOwnTask(taskId, userId);
        TrCourse course = task.getCourseId() == null ? null : courseMapper.selectById(task.getCourseId());
        List<TrCourseVideo> videos = listCourseVideos(task.getCourseId());
        return buildExamInfo(task, course, videos, mapRecords(taskId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamStartVO startExam(Long taskId) {
        Long userId = currentUserId();
        TrTrainingTask task = requireOwnTask(taskId, userId);

        // 已通过则不允许再考；但被审核退回（reviewStatus=3）的任务需放行，允许学员重新学习并补考
        boolean rejected = Objects.equals(task.getReviewStatus(), REVIEW_REJECTED);
        if (!rejected && (Objects.equals(task.getExamPass(), 1) || Objects.equals(task.getStatus(), STATUS_COMPLETED))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该任务考试已通过，无需重复考试");
        }
        // 必须先完成学习
        List<TrCourseVideo> videos = listCourseVideos(task.getCourseId());
        if (!isAllVideoCompleted(videos, mapRecords(taskId, userId))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请先完成全部视频学习后再参加考试");
        }
        // 校验试卷
        TrExamPaper paper = findPublishedPaper(task.getCourseId());
        if (paper == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该课程尚未配置已发布的试卷，请联系培训管理员");
        }

        // 若存在「考试中」的记录，直接返回该记录（避免重复开考）
        TrExamRecord inProgress = examRecordMapper.selectOne(new LambdaQueryWrapper<TrExamRecord>()
                .eq(TrExamRecord::getTaskId, taskId)
                .eq(TrExamRecord::getUserId, userId)
                .eq(TrExamRecord::getStatus, EXAM_STATUS_IN_PROGRESS)
                .orderByDesc(TrExamRecord::getCreateTime)
                .last("LIMIT 1"));
        if (inProgress != null) {
            return buildExamStartVO(inProgress, paper);
        }

        // 校验剩余考试次数
        int usedRetry = countSubmittedRecords(taskId, userId);
        int maxRetry = resolveMaxRetry(task, paper);
        if (usedRetry >= maxRetry) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "考试次数已用完，请联系培训管理员");
        }

        // 创建考试记录（status=考试中）
        TrExamRecord record = new TrExamRecord();
        record.setTaskId(taskId);
        record.setPaperId(paper.getId());
        record.setUserId(userId);
        record.setRetryCount(usedRetry + 1);
        record.setPass(0);
        record.setStartTime(LocalDateTime.now());
        record.setStatus(EXAM_STATUS_IN_PROGRESS);
        examRecordMapper.insert(record);

        // 固化本次题目快照到 tr_exam_answer（随机组卷也能保证刷新后题目稳定）
        List<AnswerSnapshot> snapshots = buildQuestionSnapshots(paper);
        if (snapshots.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "试卷暂无可用题目，请联系培训管理员");
        }
        for (AnswerSnapshot snap : snapshots) {
            TrExamAnswer answer = new TrExamAnswer();
            answer.setRecordId(record.getId());
            answer.setQuestionId(snap.questionId);
            answer.setIsCorrect(0);
            answer.setScore(snap.fullScore);
            examAnswerMapper.insert(answer);
        }

        return buildExamStartVO(record, paper);
    }

    @Override
    public ExamStartVO getExamRecord(Long recordId) {
        Long userId = currentUserId();
        TrExamRecord record = requireOwnRecord(recordId, userId);
        TrExamPaper paper = requirePaper(record.getPaperId());
        return buildExamStartVO(record, paper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamResultVO submitExam(Long recordId, ExamSubmitDTO dto) {
        Long userId = currentUserId();
        TrExamRecord record = requireOwnRecord(recordId, userId);
        if (!Objects.equals(record.getStatus(), EXAM_STATUS_IN_PROGRESS)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "本次考试已交卷，请勿重复提交");
        }
        TrExamPaper paper = requirePaper(record.getPaperId());

        // 超时保护：允许在考试时长基础上放宽 20% 容错
        if (record.getStartTime() != null && paper.getExamDuration() != null) {
            long allowedSeconds = (long) (paper.getExamDuration() * 60 * 1.2);
            LocalDateTime allowedUntil = record.getStartTime().plusSeconds(allowedSeconds);
            if (LocalDateTime.now().isAfter(allowedUntil)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "考试时间已结束，本次交卷无效");
            }
        }

        // 本次题目快照
        List<TrExamAnswer> answerRows = examAnswerMapper.selectList(new LambdaQueryWrapper<TrExamAnswer>()
                .eq(TrExamAnswer::getRecordId, recordId)
                .orderByAsc(TrExamAnswer::getId));
        if (answerRows.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "本次考试题目数据缺失，请联系管理员");
        }
        // 提交答案按题目ID建索引
        Map<Long, String> submitted = new HashMap<>();
        for (ExamSubmitDTO.AnswerItem item : dto.getAnswers()) {
            if (item.getQuestionId() != null) {
                submitted.put(item.getQuestionId(), item.getAnswer());
            }
        }

        // 逐题判分
        int totalScore = 0;
        int correctCount = 0;
        for (TrExamAnswer row : answerRows) {
            TrQuestion question = questionMapper.selectById(row.getQuestionId());
            if (question == null) {
                continue;
            }
            String userAnswer = submitted.get(row.getQuestionId());
            boolean correct = judge(question, userAnswer);
            int fullScore = row.getScore() == null ? (question.getScore() == null ? 0 : question.getScore()) : row.getScore();
            int earned = correct ? fullScore : 0;
            row.setAnswer(userAnswer);
            row.setIsCorrect(correct ? 1 : 0);
            row.setScore(earned);
            examAnswerMapper.updateById(row);
            totalScore += earned;
            if (correct) {
                correctCount++;
            } else {
                // 答错题自动收集到「错题本」：同一学员同一题累加答错次数并重置为未掌握
                saveWrongQuestion(userId, question);
            }
        }

        // 更新考试记录
        int passScore = paper.getPassScore() == null ? 60 : paper.getPassScore();
        int pass = totalScore >= passScore ? 1 : 0;
        LocalDateTime now = LocalDateTime.now();
        record.setScore(totalScore);
        record.setPass(pass);
        record.setSubmitTime(now);
        record.setDuration(record.getStartTime() == null ? null
                : (int) Duration.between(record.getStartTime(), now).getSeconds());
        record.setStatus(EXAM_STATUS_GRADED);
        examRecordMapper.updateById(record);

        // 回填任务：成绩、是否通过、已考次数
        // 考试通过不再直接置为「已完成」，而是进入两级审核链（待主管审核），由主管与 QA 依次复核后方可归档
        TrTrainingTask task = taskMapper.selectById(record.getTaskId());
        if (task != null) {
            TrTrainingTask update = new TrTrainingTask();
            update.setId(task.getId());
            update.setExamScore(totalScore);
            update.setExamPass(pass);
            update.setRetryCount(record.getRetryCount());
            if (pass != 1) {
                // 未通过则回到「待考试」，允许补考
                update.setStatus(STATUS_WAITING_EXAM);
            }
            taskMapper.updateById(update);
            if (pass == 1) {
                // 通过后调用任务服务的审核链入口：置 status=3、review_status=0，并通知部门主管审核
                trainingTaskService.enterReviewChain(task.getId());
            }
        }

        // 通过后推送站内消息通知（此时任务进入「待主管审核」，文案同步调整）
        if (pass == 1) {
            SysMessage message = new SysMessage();
            message.setUserId(userId);
            message.setTitle("培训考试通过通知");
            message.setContent("恭喜您通过《" + paper.getName() + "》考试，成绩 " + totalScore + " 分，培训记录已提交主管审核。");
            message.setMessageType(1);
            message.setIsRead(0);
            message.setRelatedId(record.getTaskId());
            messageMapper.insert(message);
        }

        log.info("学员 {} 提交考试记录 {}，得分 {}，是否通过 {}", userId, recordId, totalScore, pass);
        return buildExamResult(record, paper, answerRows, correctCount);
    }

    @Override
    public ExamResultVO examResult(Long recordId) {
        Long userId = currentUserId();
        TrExamRecord record = requireOwnRecord(recordId, userId);
        if (Objects.equals(record.getStatus(), EXAM_STATUS_IN_PROGRESS)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "本次考试尚未交卷，暂无成绩");
        }
        TrExamPaper paper = requirePaper(record.getPaperId());
        List<TrExamAnswer> answerRows = examAnswerMapper.selectList(new LambdaQueryWrapper<TrExamAnswer>()
                .eq(TrExamAnswer::getRecordId, recordId)
                .orderByAsc(TrExamAnswer::getId));
        int correctCount = (int) answerRows.stream()
                .filter(a -> Objects.equals(a.getIsCorrect(), 1)).count();
        return buildExamResult(record, paper, answerRows, correctCount);
    }

    // ==================================================================
    // 模块四：个人中心与培训档案
    // ==================================================================

    @Override
    public UserInfoVO profile() {
        SysUser user = userMapper.selectById(currentUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        UserInfoVO vo = UserInfoVO.from(user);
        vo.setRoles(roleCodes(user.getId()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(ProfileUpdateDTO dto) {
        Long userId = currentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setRealName(dto.getRealName());
        update.setPhone(dto.getPhone());
        update.setEmail(dto.getEmail());
        userMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(PasswordUpdateDTO dto) {
        Long userId = currentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        // 校验旧密码
        if (!BCrypt.checkpw(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "旧密码不正确");
        }
        // 新密码不得与旧密码相同
        if (BCrypt.checkpw(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "新密码不能与旧密码相同");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(BCrypt.hashpw(dto.getNewPassword()));
        userMapper.updateById(update);
    }

    @Override
    public Page<StudyRecordVO> pageStudyRecords(Integer pageNum, Integer pageSize, String keyword) {
        // 学员端：委托给带 userId 的版本，强制隔离本人数据
        return pageStudyRecords(currentUserId(), pageNum, pageSize, keyword);
    }

    @Override
    public Page<StudyRecordVO> pageStudyRecords(Long userId, Integer pageNum, Integer pageSize, String keyword) {
        Page<TrVideoStudyRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TrVideoStudyRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrVideoStudyRecord::getUserId, userId);
        // 关键字：按视频名称模糊匹配，先查视频ID再过滤
        if (StrUtil.isNotBlank(keyword)) {
            List<Long> videoIds = videoMapper.selectList(new LambdaQueryWrapper<TrCourseVideo>()
                            .like(TrCourseVideo::getName, keyword))
                    .stream().map(TrCourseVideo::getId).collect(Collectors.toList());
            if (videoIds.isEmpty()) {
                wrapper.eq(TrVideoStudyRecord::getVideoId, -1L);
            } else {
                wrapper.in(TrVideoStudyRecord::getVideoId, videoIds);
            }
        }
        wrapper.orderByDesc(TrVideoStudyRecord::getLastStudyTime);

        Page<TrVideoStudyRecord> entityPage = recordMapper.selectPage(page, wrapper);
        Page<StudyRecordVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(this::toStudyRecordVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public Page<ExamRecordVO> pageExamRecords(Integer pageNum, Integer pageSize, String keyword) {
        // 学员端：委托给带 userId 的版本
        return pageExamRecords(currentUserId(), pageNum, pageSize, keyword);
    }

    @Override
    public Page<ExamRecordVO> pageExamRecords(Long userId, Integer pageNum, Integer pageSize, String keyword) {
        Page<TrExamRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TrExamRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrExamRecord::getUserId, userId);
        // 仅展示已交卷的记录
        wrapper.ne(TrExamRecord::getStatus, EXAM_STATUS_IN_PROGRESS);
        // 关键字：试卷名称或任务名称
        if (StrUtil.isNotBlank(keyword)) {
            List<Long> paperIds = paperMapper.selectList(new LambdaQueryWrapper<TrExamPaper>()
                            .like(TrExamPaper::getName, keyword))
                    .stream().map(TrExamPaper::getId).collect(Collectors.toList());
            List<Long> taskIds = taskMapper.selectList(new LambdaQueryWrapper<TrTrainingTask>()
                            .like(TrTrainingTask::getName, keyword))
                    .stream().map(TrTrainingTask::getId).collect(Collectors.toList());
            if (paperIds.isEmpty() && taskIds.isEmpty()) {
                wrapper.eq(TrExamRecord::getId, -1L);
            } else {
                wrapper.and(w -> {
                    if (!paperIds.isEmpty()) {
                        w.in(TrExamRecord::getPaperId, paperIds);
                    }
                    if (!taskIds.isEmpty()) {
                        if (!paperIds.isEmpty()) {
                            w.or();
                        }
                        w.in(TrExamRecord::getTaskId, taskIds);
                    }
                });
            }
        }
        wrapper.orderByDesc(TrExamRecord::getSubmitTime);

        Page<TrExamRecord> entityPage = examRecordMapper.selectPage(page, wrapper);
        Page<ExamRecordVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(this::toExamRecordVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public List<CertificateVO> certificates() {
        // 学员端：委托给带 userId 的版本
        return certificates(currentUserId());
    }

    @Override
    public List<CertificateVO> certificates(Long userId) {
        // 查询指定用户所有通过的考试记录
        List<TrExamRecord> passed = examRecordMapper.selectList(new LambdaQueryWrapper<TrExamRecord>()
                .eq(TrExamRecord::getUserId, userId)
                .eq(TrExamRecord::getPass, 1)
                .ne(TrExamRecord::getStatus, EXAM_STATUS_IN_PROGRESS)
                .orderByDesc(TrExamRecord::getSubmitTime));
        if (passed.isEmpty()) {
            return new ArrayList<>();
        }

        // 按课程分组，保留最近一次通过记录
        Map<Long, TrExamRecord> latestByCourse = new LinkedHashMap<>();
        for (TrExamRecord record : passed) {
            TrTrainingTask task = taskMapper.selectById(record.getTaskId());
            if (task == null || task.getCourseId() == null) {
                continue;
            }
            // 因已按提交时间倒序，首次出现即为最近一次
            latestByCourse.putIfAbsent(task.getCourseId(), record);
        }

        List<CertificateVO> list = new ArrayList<>();
        for (Map.Entry<Long, TrExamRecord> entry : latestByCourse.entrySet()) {
            TrCourse course = courseMapper.selectById(entry.getKey());
            if (course == null) {
                continue;
            }
            TrExamRecord record = entry.getValue();
            CertificateVO vo = new CertificateVO();
            vo.setCourseId(course.getId());
            vo.setCourseName(course.getName());
            vo.setCourseCode(course.getCode());
            vo.setSopCode(course.getSopCode());
            vo.setSopVersion(course.getSopVersion());
            vo.setTrainingHours(course.getTrainingHours());
            vo.setRetrainPeriod(course.getRetrainPeriod());
            vo.setExamScore(record.getScore());
            LocalDateTime passTime = record.getSubmitTime() == null ? record.getUpdateTime() : record.getSubmitTime();
            vo.setLastPassTime(passTime);
            // 有效期至 = 最近一次通过时间 + 再培训周期（天），未配置周期时默认 365 天
            int period = course.getRetrainPeriod() == null || course.getRetrainPeriod() <= 0 ? 365 : course.getRetrainPeriod();
            LocalDateTime validUntil = passTime == null ? null : passTime.plusDays(period);
            vo.setValidUntil(validUntil);
            if (validUntil == null) {
                vo.setStatus("valid");
            } else {
                long remainDays = Duration.between(LocalDateTime.now(), validUntil).toDays();
                vo.setRemainDays(remainDays);
                if (remainDays < 0) {
                    vo.setStatus("expired");
                } else if (remainDays <= CERT_EXPIRING_DAYS) {
                    vo.setStatus("expiring");
                } else {
                    vo.setStatus("valid");
                }
            }
            list.add(vo);
        }
        return list;
    }

    @Override
    public Page<SysMessage> pageMessages(Integer pageNum, Integer pageSize, Integer isRead, Integer messageType) {
        Long userId = currentUserId();
        Page<SysMessage> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysMessage> wrapper = new LambdaQueryWrapper<>();
        // 数据隔离：仅查询本人消息
        wrapper.eq(SysMessage::getUserId, userId);
        // 已读状态筛选（不传则不过滤，保持顶部铃铛原有行为）
        if (isRead != null) {
            wrapper.eq(SysMessage::getIsRead, isRead);
        }
        // 消息类型筛选（消息中心页面使用）
        if (messageType != null) {
            wrapper.eq(SysMessage::getMessageType, messageType);
        }
        wrapper.orderByDesc(SysMessage::getCreateTime);
        return messageMapper.selectPage(page, wrapper);
    }

    @Override
    public Long unreadCount() {
        return messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getUserId, currentUserId())
                .eq(SysMessage::getIsRead, 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readMessage(Long id) {
        Long userId = currentUserId();
        SysMessage message = messageMapper.selectById(id);
        if (message == null || !Objects.equals(message.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        if (Objects.equals(message.getIsRead(), 1)) {
            return;
        }
        SysMessage update = new SysMessage();
        update.setId(id);
        update.setIsRead(1);
        update.setReadTime(LocalDateTime.now());
        messageMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readAllMessages() {
        Long userId = currentUserId();
        messageMapper.update(null, new LambdaUpdateWrapper<SysMessage>()
                .eq(SysMessage::getUserId, userId)
                .eq(SysMessage::getIsRead, 0)
                .set(SysMessage::getIsRead, 1)
                .set(SysMessage::getReadTime, LocalDateTime.now()));
    }

    // ==================================================================
    // 模块五：学习看板
    // ==================================================================

    @Override
    public AnalysisOverviewVO analysisOverview() {
        Long userId = currentUserId();
        // 拉取本人全部任务，在内存中聚合（单学员数据量小，避免多次聚合查询）
        List<TrTrainingTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<TrTrainingTask>()
                .eq(TrTrainingTask::getUserId, userId));

        AnalysisOverviewVO vo = new AnalysisOverviewVO();
        // ---- 指标卡：任务维度 ----
        int totalDuration = 0;
        int completed = 0;
        int studying = 0;
        int overdue = 0;
        Map<Integer, Integer> statusCounter = new LinkedHashMap<>();
        // 初始化 0~7 全部展示状态桶，保证前端状态分布图例完整（含 5/6/7 审核相关状态）
        for (int i = 0; i <= STATUS_DISPLAY_REJECTED; i++) {
            statusCounter.put(i, 0);
        }
        for (TrTrainingTask task : tasks) {
            totalDuration += task.getStudyDuration() == null ? 0 : task.getStudyDuration();
            int displayStatus = displayStatusCode(task);
            statusCounter.merge(displayStatus, 1, Integer::sum);
            if (displayStatus == STATUS_COMPLETED) {
                completed++;
            } else if (displayStatus == STATUS_STUDYING) {
                studying++;
            } else if (displayStatus == STATUS_OVERDUE) {
                overdue++;
            }
        }
        vo.setTotalStudyDuration(totalDuration);
        vo.setTotalTask(tasks.size());
        vo.setCompletedTask(completed);
        vo.setStudyingTask(studying);
        vo.setOverdueTask(overdue);
        // 证书数复用培训档案逻辑（按课程保留最近一次通过记录）
        vo.setCertificateCount(certificates().size());
        vo.setStatusDist(statusCounter.entrySet().stream()
                .map(e -> new AnalysisOverviewVO.StatusCountVO(e.getKey(), e.getValue()))
                .collect(Collectors.toList()));

        // ---- 考试记录聚合：平均分 / 通过率 / 成绩分布 / 月度趋势 ----
        List<TrExamRecord> records = examRecordMapper.selectList(new LambdaQueryWrapper<TrExamRecord>()
                .eq(TrExamRecord::getUserId, userId)
                .ne(TrExamRecord::getStatus, EXAM_STATUS_IN_PROGRESS));

        int scoreSum = 0;
        int scoreCount = 0;
        int passCount = 0;
        int[] scoreBuckets = new int[5];
        for (TrExamRecord record : records) {
            if (record.getScore() != null) {
                scoreSum += record.getScore();
                scoreCount++;
                scoreBuckets[scoreBucketIndex(record.getScore())]++;
            }
            if (Objects.equals(record.getPass(), 1)) {
                passCount++;
            }
        }
        vo.setAvgScore(scoreCount == 0 ? 0 : Math.round((float) scoreSum / scoreCount));
        vo.setPassRate(records.isEmpty() ? 0 : (int) Math.round(passCount * 100.0 / records.size()));
        vo.setScoreDist(buildScoreDist(scoreBuckets));
        vo.setMonthly(buildMonthlyTrend(userId, records));
        return vo;
    }

    // ==================================================================
    // 模块六：课程中心（自主选学）
    // ==================================================================

    @Override
    public Page<StudyCourseVO> pageCourses(Integer pageNum, Integer pageSize, String keyword,
                                           Long categoryId, Boolean enrolled) {
        Long userId = currentUserId();
        SysUser user = userMapper.selectById(userId);
        // 本人任务：courseId -> 最新任务（用于计算报名状态与跳转任务详情）
        Map<Long, TrTrainingTask> taskByCourse = mapUserTasksByCourse(userId);
        Set<Long> enrolledCourseIds = taskByCourse.keySet();

        Page<TrCourse> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TrCourse> wrapper = new LambdaQueryWrapper<>();
        // 仅返回启用课程
        wrapper.eq(TrCourse::getStatus, 1);
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(TrCourse::getName, keyword);
        }
        if (categoryId != null) {
            wrapper.eq(TrCourse::getCategoryId, categoryId);
        }
        // 推荐范围：本部门课程 / 本岗位课程 / 全校通用课程（部门与岗位均为空）
        Long deptId = user == null ? null : user.getDepartmentId();
        Long posId = user == null ? null : user.getPositionId();
        if (deptId != null || posId != null) {
            wrapper.and(w -> {
                boolean hasCondition = false;
                if (deptId != null) {
                    w.eq(TrCourse::getDepartmentId, deptId);
                    hasCondition = true;
                }
                if (posId != null) {
                    if (hasCondition) {
                        w.or();
                    }
                    w.eq(TrCourse::getPositionId, posId);
                    hasCondition = true;
                }
                // 追加「全校通用课程」条件
                w.or();
                w.isNull(TrCourse::getDepartmentId).isNull(TrCourse::getPositionId);
            });
        }
        // 报名状态筛选
        if (enrolled != null) {
            if (Boolean.TRUE.equals(enrolled)) {
                if (enrolledCourseIds.isEmpty()) {
                    return new Page<>(pageNum, pageSize, 0);
                }
                wrapper.in(TrCourse::getId, enrolledCourseIds);
            } else if (!enrolledCourseIds.isEmpty()) {
                wrapper.notIn(TrCourse::getId, enrolledCourseIds);
            }
        }
        wrapper.orderByDesc(TrCourse::getCreateTime);

        Page<TrCourse> entityPage = courseMapper.selectPage(page, wrapper);
        Map<Long, String> categoryNames = categoryNameMap(entityPage.getRecords());
        // 批量查每个课程的第一个启用视频ID（用于前端加载封面）
        Map<Long, Long> firstVideoIds = firstVideoIdMap(entityPage.getRecords());
        Page<StudyCourseVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream()
                .map(c -> toStudyCourseVO(c, categoryNames.get(c.getCategoryId()), taskByCourse, firstVideoIds))
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public StudyCourseVO courseDetail(Long courseId) {
        TrCourse course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        Map<Long, TrTrainingTask> taskByCourse = mapUserTasksByCourse(currentUserId());
        // 回填第一个启用视频ID（供前端加载封面）
        Map<Long, Long> firstVideoIds = firstVideoIdMap(Collections.singletonList(course));
        StudyCourseVO vo = toStudyCourseVO(course, resolveCategoryName(course.getCategoryId()), taskByCourse, firstVideoIds);
        // 资料数 / 视频数 / 题目数
        vo.setFileCount(courseFileMapper.selectCount(new LambdaQueryWrapper<TrCourseFile>()
                .eq(TrCourseFile::getCourseId, courseId)
                .eq(TrCourseFile::getStatus, 1)).intValue());
        vo.setVideoCount(videoMapper.selectCount(new LambdaQueryWrapper<TrCourseVideo>()
                .eq(TrCourseVideo::getCourseId, courseId)
                .eq(TrCourseVideo::getStatus, 1)).intValue());
        vo.setQuestionCount(questionMapper.selectCount(new LambdaQueryWrapper<TrQuestion>()
                .eq(TrQuestion::getCourseId, courseId)
                .eq(TrQuestion::getStatus, 1)).intValue());
        // 部门 / 岗位名称回填
        if (course.getDepartmentId() != null) {
            SysDepartment dept = departmentMapper.selectById(course.getDepartmentId());
            vo.setDepartmentName(dept == null ? null : dept.getName());
        }
        if (course.getPositionId() != null) {
            SysPosition position = positionMapper.selectById(course.getPositionId());
            vo.setPositionName(position == null ? null : position.getName());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long enrollCourse(Long courseId) {
        Long userId = currentUserId();
        TrCourse course = courseMapper.selectById(courseId);
        if (course == null || !Objects.equals(course.getStatus(), 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在或已停用");
        }
        // 已存在未完成的任务则不允许重复报名
        Long activeCount = taskMapper.selectCount(new LambdaQueryWrapper<TrTrainingTask>()
                .eq(TrTrainingTask::getUserId, userId)
                .eq(TrTrainingTask::getCourseId, courseId)
                .ne(TrTrainingTask::getStatus, STATUS_COMPLETED));
        if (activeCount != null && activeCount > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该课程已有进行中的学习任务");
        }

        // 生成自主学习任务：来源标记为「自主报名」
        TrTrainingTask task = new TrTrainingTask();
        task.setName(course.getName());
        task.setCourseId(courseId);
        task.setUserId(userId);
        task.setTaskType(1);
        task.setSource(TASK_SOURCE_SELF);
        task.setDescription("学员自主报名学习");
        task.setStatus(STATUS_NOT_STARTED);
        task.setStudyDuration(0);
        task.setVideoProgress(BigDecimal.ZERO);
        task.setExamPass(0);
        task.setRetryCount(0);
        task.setMaxRetry(3);
        // 截止时间 = 当前时间 + 课程复训周期；课程未配置复训周期时不设截止时间
        if (course.getRetrainPeriod() != null && course.getRetrainPeriod() > 0) {
            task.setDeadline(LocalDateTime.now().plusDays(course.getRetrainPeriod()));
        }
        taskMapper.insert(task);
        log.info("学员 {} 自主报名课程 {}，生成任务 {}", userId, courseId, task.getId());
        return task.getId();
    }

    // ==================================================================
    // 模块七：培训日历
    // ==================================================================

    @Override
    public List<CalendarDayVO> calendar(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "起止日期不能为空");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结束日期不能早于起始日期");
        }
        if (Duration.between(startDate.atStartOfDay(), endDate.atStartOfDay()).toDays() > CALENDAR_MAX_DAYS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "日历查询跨度不得超过 " + CALENDAR_MAX_DAYS + " 天");
        }
        Long userId = currentUserId();
        LocalDateTime start = startDate.atStartOfDay();
        // 结束时间取下一天零点，配合 lt 实现「含结束日」
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        // 使用 TreeMap 按日期升序聚合
        Map<LocalDate, List<CalendarDayVO.CalendarEventVO>> grouped = new TreeMap<>();

        // 事件类型 1：任务截止
        List<TrTrainingTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<TrTrainingTask>()
                .eq(TrTrainingTask::getUserId, userId)
                .isNotNull(TrTrainingTask::getDeadline)
                .ge(TrTrainingTask::getDeadline, start)
                .lt(TrTrainingTask::getDeadline, end));
        for (TrTrainingTask task : tasks) {
            CalendarDayVO.CalendarEventVO event = new CalendarDayVO.CalendarEventVO();
            event.setType(1);
            event.setTitle(task.getName() + " 截止");
            event.setTaskId(task.getId());
            event.setCourseId(task.getCourseId());
            event.setTime(task.getDeadline());
            event.setStatus(displayStatusCode(task));
            grouped.computeIfAbsent(task.getDeadline().toLocalDate(), k -> new ArrayList<>()).add(event);
        }

        // 事件类型 2：考试安排（取考试记录的开始时间）
        List<TrExamRecord> records = examRecordMapper.selectList(new LambdaQueryWrapper<TrExamRecord>()
                .eq(TrExamRecord::getUserId, userId)
                .isNotNull(TrExamRecord::getStartTime)
                .ge(TrExamRecord::getStartTime, start)
                .lt(TrExamRecord::getStartTime, end));
        for (TrExamRecord record : records) {
            TrTrainingTask task = record.getTaskId() == null ? null : taskMapper.selectById(record.getTaskId());
            CalendarDayVO.CalendarEventVO event = new CalendarDayVO.CalendarEventVO();
            event.setType(2);
            event.setTitle((task == null ? "考试" : task.getName()) + " 考试");
            event.setTaskId(record.getTaskId());
            event.setCourseId(task == null ? null : task.getCourseId());
            event.setRecordId(record.getId());
            event.setTime(record.getStartTime());
            event.setStatus(record.getStatus());
            grouped.computeIfAbsent(record.getStartTime().toLocalDate(), k -> new ArrayList<>()).add(event);
        }

        List<CalendarDayVO> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<CalendarDayVO.CalendarEventVO>> entry : grouped.entrySet()) {
            CalendarDayVO day = new CalendarDayVO();
            day.setDate(entry.getKey().format(DATE_FORMATTER));
            day.setEvents(entry.getValue());
            result.add(day);
        }
        return result;
    }

    // ==================================================================
    // 模块八：错题本与题库练习
    // ==================================================================

    @Override
    public Page<WrongQuestionVO> pageWrongQuestions(Integer pageNum, Integer pageSize, Long courseId,
                                                    Integer questionType, Boolean mastered) {
        Long userId = currentUserId();
        Page<TrWrongQuestion> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TrWrongQuestion> wrapper = new LambdaQueryWrapper<>();
        // 数据隔离：仅查询本人错题
        wrapper.eq(TrWrongQuestion::getUserId, userId);
        // 掌握状态：默认只看未掌握（mastered = 0）
        wrapper.eq(TrWrongQuestion::getMastered, Boolean.TRUE.equals(mastered) ? 1 : 0);
        if (courseId != null) {
            wrapper.eq(TrWrongQuestion::getCourseId, courseId);
        }
        // 题型筛选：错题表未冗余题型，先取匹配题目ID集合再过滤
        if (questionType != null) {
            List<Long> questionIds = questionMapper.selectList(new LambdaQueryWrapper<TrQuestion>()
                            .eq(TrQuestion::getQuestionType, questionType))
                    .stream().map(TrQuestion::getId).collect(Collectors.toList());
            if (questionIds.isEmpty()) {
                return new Page<>(pageNum, pageSize, 0);
            }
            wrapper.in(TrWrongQuestion::getQuestionId, questionIds);
        }
        wrapper.orderByDesc(TrWrongQuestion::getLastWrongTime);

        Page<TrWrongQuestion> entityPage = wrongQuestionMapper.selectPage(page, wrapper);
        List<TrWrongQuestion> rows = entityPage.getRecords();
        // 批量回填题目、课程名称与本人最近一次错误答案，避免 N+1 查询
        Map<Long, TrQuestion> questionMap = mapQuestions(rows);
        Map<Long, String> courseNames = courseNameMapByIds(rows.stream()
                .map(TrWrongQuestion::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> latestWrongAnswers = latestWrongAnswers(userId);

        Page<WrongQuestionVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        List<WrongQuestionVO> list = new ArrayList<>();
        for (TrWrongQuestion row : rows) {
            TrQuestion question = questionMap.get(row.getQuestionId());
            if (question == null) {
                continue;
            }
            WrongQuestionVO vo = new WrongQuestionVO();
            vo.setId(row.getId());
            vo.setQuestionId(row.getQuestionId());
            vo.setCourseId(row.getCourseId());
            vo.setCourseName(courseNames.get(row.getCourseId()));
            vo.setQuestionType(question.getQuestionType());
            vo.setTypeName(typeName(question.getQuestionType()));
            vo.setContent(question.getContent());
            vo.setOptions(parseOptions(question.getOptions()));
            vo.setCorrectAnswer(question.getAnswer());
            vo.setAnalysis(question.getAnalysis());
            vo.setMyAnswer(latestWrongAnswers.get(row.getQuestionId()));
            vo.setWrongCount(row.getWrongCount());
            vo.setLastWrongTime(row.getLastWrongTime());
            vo.setMastered(row.getMastered());
            list.add(vo);
        }
        voPage.setRecords(list);
        return voPage;
    }

    @Override
    public WrongQuestionStatsVO wrongQuestionStats() {
        Long userId = currentUserId();
        List<TrWrongQuestion> rows = wrongQuestionMapper.selectList(new LambdaQueryWrapper<TrWrongQuestion>()
                .eq(TrWrongQuestion::getUserId, userId));

        WrongQuestionStatsVO vo = new WrongQuestionStatsVO();
        vo.setTotal((long) rows.size());
        long masteredCount = rows.stream().filter(r -> Objects.equals(r.getMastered(), 1)).count();
        vo.setMastered(masteredCount);
        vo.setNotMastered(rows.size() - masteredCount);

        // 题型与课程分布
        Map<Long, TrQuestion> questionMap = mapQuestions(rows);
        Map<Integer, Long> typeCounter = new LinkedHashMap<>();
        Map<Long, Long> courseCounter = new LinkedHashMap<>();
        for (TrWrongQuestion row : rows) {
            TrQuestion question = questionMap.get(row.getQuestionId());
            if (question != null && question.getQuestionType() != null) {
                typeCounter.merge(question.getQuestionType(), 1L, Long::sum);
            }
            if (row.getCourseId() != null) {
                courseCounter.merge(row.getCourseId(), 1L, Long::sum);
            }
        }
        vo.setByType(typeCounter.entrySet().stream()
                .map(e -> new WrongQuestionStatsVO.TypeCountVO(e.getKey(), typeName(e.getKey()), e.getValue()))
                .collect(Collectors.toList()));

        List<WrongQuestionStatsVO.CourseCountVO> byCourse = new ArrayList<>();
        Map<Long, String> courseNames = courseNameMapByIds(courseCounter.keySet());
        for (Map.Entry<Long, Long> entry : courseCounter.entrySet()) {
            byCourse.add(new WrongQuestionStatsVO.CourseCountVO(entry.getKey(),
                    courseNames.getOrDefault(entry.getKey(), "未知课程"), entry.getValue()));
        }
        vo.setByCourse(byCourse);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WrongPracticeResultVO practiceWrongQuestion(WrongPracticeDTO dto) {
        Long userId = currentUserId();
        TrWrongQuestion wrong = wrongQuestionMapper.selectOne(new LambdaQueryWrapper<TrWrongQuestion>()
                .eq(TrWrongQuestion::getUserId, userId)
                .eq(TrWrongQuestion::getQuestionId, dto.getQuestionId())
                .last("LIMIT 1"));
        if (wrong == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "错题记录不存在");
        }
        TrQuestion question = questionMapper.selectById(dto.getQuestionId());
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        // 复用考试判分规则（多选字母忽略顺序、判断题归一化）
        boolean correct = judge(question, dto.getAnswer());

        TrWrongQuestion update = new TrWrongQuestion();
        update.setId(wrong.getId());
        update.setLastPracticeCorrect(correct ? 1 : 0);
        if (correct) {
            // 重做答对即视为已掌握，自动移出错题本
            update.setMastered(1);
        }
        wrongQuestionMapper.updateById(update);

        WrongPracticeResultVO vo = new WrongPracticeResultVO();
        vo.setCorrect(correct);
        vo.setCorrectAnswer(question.getAnswer());
        vo.setAnalysis(question.getAnalysis());
        vo.setMastered(correct);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeWrongQuestion(Long id) {
        Long userId = currentUserId();
        TrWrongQuestion wrong = wrongQuestionMapper.selectById(id);
        if (wrong == null || !Objects.equals(wrong.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "错题记录不存在");
        }
        // 手动移除等价于标记为已掌握
        TrWrongQuestion update = new TrWrongQuestion();
        update.setId(id);
        update.setMastered(1);
        wrongQuestionMapper.updateById(update);
    }

    // ==================================================================
    // 模块九：资料中心
    // ==================================================================

    @Override
    public Page<MaterialVO> pageMaterials(Integer pageNum, Integer pageSize, Long courseId,
                                          String fileType, String keyword) {
        Long userId = currentUserId();
        // 资料范围：本人存在培训任务（任意状态）的课程，避免越权访问未授权课程资料
        Set<Long> scopeCourseIds = mapUserTasksByCourse(userId).keySet();
        if (scopeCourseIds.isEmpty()) {
            return new Page<>(pageNum, pageSize, 0);
        }
        // 指定课程时必须落在本人可访问范围内
        if (courseId != null && !scopeCourseIds.contains(courseId)) {
            return new Page<>(pageNum, pageSize, 0);
        }

        Page<TrCourseFile> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TrCourseFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrCourseFile::getStatus, 1);
        wrapper.in(TrCourseFile::getCourseId, courseId == null ? scopeCourseIds : Collections.singleton(courseId));
        if (StrUtil.isNotBlank(fileType)) {
            wrapper.eq(TrCourseFile::getFileType, fileType.toUpperCase());
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(TrCourseFile::getName, keyword);
        }
        wrapper.orderByDesc(TrCourseFile::getUploadTime);

        Page<TrCourseFile> entityPage = courseFileMapper.selectPage(page, wrapper);
        Map<Long, String> courseNames = courseNameMapByIds(entityPage.getRecords().stream()
                .map(TrCourseFile::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Page<MaterialVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(f -> {
            MaterialVO vo = new MaterialVO();
            vo.setId(f.getId());
            vo.setName(f.getName());
            vo.setFileName(f.getFileName());
            vo.setFileType(f.getFileType());
            vo.setFileSize(f.getFileSize());
            vo.setCourseId(f.getCourseId());
            vo.setCourseName(courseNames.get(f.getCourseId()));
            vo.setUploadTime(f.getUploadTime());
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    // ==================================================================
    // 私有辅助方法
    // ==================================================================

    /**
     * 获取当前登录用户ID
     */
    private Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 校验任务存在且属于当前用户
     */
    private TrTrainingTask requireOwnTask(Long taskId, Long userId) {
        TrTrainingTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "培训任务不存在");
        }
        if (!Objects.equals(task.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该培训任务");
        }
        return task;
    }

    /**
     * 校验考试记录存在且属于当前用户
     */
    private TrExamRecord requireOwnRecord(Long recordId, Long userId) {
        TrExamRecord record = examRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "考试记录不存在");
        }
        if (!Objects.equals(record.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该考试记录");
        }
        return record;
    }

    /**
     * 校验试卷存在
     */
    private TrExamPaper requirePaper(Long paperId) {
        TrExamPaper paper = paperId == null ? null : paperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "试卷不存在，请联系培训管理员");
        }
        return paper;
    }

    /**
     * 查询课程下已发布试卷（无则返回 null）
     */
    private TrExamPaper findPublishedPaper(Long courseId) {
        if (courseId == null) {
            return null;
        }
        return paperMapper.selectOne(new LambdaQueryWrapper<TrExamPaper>()
                .eq(TrExamPaper::getCourseId, courseId)
                .eq(TrExamPaper::getStatus, PAPER_STATUS_PUBLISHED)
                .orderByDesc(TrExamPaper::getCreateTime)
                .last("LIMIT 1"));
    }

    /**
     * 查询课程下的视频课件（按排序号升序）
     */
    private List<TrCourseVideo> listCourseVideos(Long courseId) {
        if (courseId == null) {
            return new ArrayList<>();
        }
        return videoMapper.selectList(new LambdaQueryWrapper<TrCourseVideo>()
                .eq(TrCourseVideo::getCourseId, courseId)
                .eq(TrCourseVideo::getStatus, 1)
                .orderByAsc(TrCourseVideo::getSort)
                .orderByAsc(TrCourseVideo::getId));
    }

    /**
     * 查询课程资料附件（仅启用状态）
     */
    private List<CourseFileVO> listCourseFiles(Long courseId) {
        List<TrCourseFile> files = courseFileMapper.selectList(new LambdaQueryWrapper<TrCourseFile>()
                .eq(TrCourseFile::getCourseId, courseId)
                .eq(TrCourseFile::getStatus, 1)
                .orderByDesc(TrCourseFile::getUploadTime));
        return files.stream().map(f -> {
            CourseFileVO vo = new CourseFileVO();
            vo.setId(f.getId());
            vo.setName(f.getName());
            vo.setFileName(f.getFileName());
            vo.setFileType(f.getFileType());
            vo.setFileSize(f.getFileSize());
            vo.setUploadTime(f.getUploadTime());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 查询当前用户在指定任务下的全部视频学习记录，按视频ID建索引
     */
    private Map<Long, TrVideoStudyRecord> mapRecords(Long taskId, Long userId) {
        List<TrVideoStudyRecord> records = recordMapper.selectList(new LambdaQueryWrapper<TrVideoStudyRecord>()
                .eq(TrVideoStudyRecord::getTaskId, taskId)
                .eq(TrVideoStudyRecord::getUserId, userId));
        Map<Long, TrVideoStudyRecord> map = new HashMap<>();
        for (TrVideoStudyRecord record : records) {
            map.put(record.getVideoId(), record);
        }
        return map;
    }

    /**
     * 判断课程视频是否全部学完（课程无视频时视为已满足考试前置条件）
     */
    private boolean isAllVideoCompleted(List<TrCourseVideo> videos, Map<Long, TrVideoStudyRecord> recordMap) {
        if (videos == null || videos.isEmpty()) {
            return true;
        }
        for (TrCourseVideo video : videos) {
            TrVideoStudyRecord record = recordMap.get(video.getId());
            if (record == null || !Objects.equals(record.getIsCompleted(), 1)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 计算按视频时长加权的整体进度（0~100，保留两位小数）
     */
    private BigDecimal calcWeightedProgress(List<TrCourseVideo> videos, Map<Long, TrVideoStudyRecord> recordMap) {
        if (videos == null || videos.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double totalWeight = 0D;
        double weighted = 0D;
        for (TrCourseVideo video : videos) {
            // 无时长信息的视频按权重 1 处理，保证不出现除零
            double weight = (video.getDuration() == null || video.getDuration() <= 0) ? 1D : video.getDuration();
            TrVideoStudyRecord record = recordMap.get(video.getId());
            double progress = 0D;
            if (record != null && record.getVideoProgress() != null) {
                progress = Math.min(record.getVideoProgress().doubleValue(), 100D);
            }
            weighted += progress * weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(weighted / totalWeight).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 任务实体转列表 VO
     */
    private StudyTaskVO toStudyTaskVO(TrTrainingTask task) {
        StudyTaskVO vo = new StudyTaskVO();
        int displayStatus = displayStatusCode(task);
        vo.setId(task.getId());
        vo.setName(task.getName());
        vo.setCourseId(task.getCourseId());
        vo.setDeadline(task.getDeadline());
        vo.setStatusCode(displayStatus);
        vo.setStatus(statusKey(displayStatus));
        vo.setVideoProgress(task.getVideoProgress());
        vo.setStudyDuration(task.getStudyDuration());
        vo.setExamScore(task.getExamScore());
        vo.setExamPass(task.getExamPass());
        vo.setRetryCount(task.getRetryCount());
        vo.setMaxRetry(resolveMaxRetry(task, null));
        vo.setDescription(task.getDescription());
        vo.setCreateTime(task.getCreateTime());
        // 回填审核维度字段，供前端渲染「待主管审核 / 待QA复核 / 已退回」及退回意见
        vo.setReviewStatus(task.getReviewStatus());
        vo.setReviewStatusText(reviewStatusText(task.getReviewStatus()));
        vo.setRejectRemark(resolveRejectRemark(task));
        // 能否考试：处于「待考试」或被退回（需重新学习补考）
        vo.setCanExam(displayStatus == STATUS_WAITING_EXAM || displayStatus == STATUS_DISPLAY_REJECTED);
        // 回填课程名与计划名
        if (task.getCourseId() != null) {
            TrCourse course = courseMapper.selectById(task.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getName());
            }
        }
        if (task.getPlanId() != null) {
            TrTrainingPlan plan = planMapper.selectById(task.getPlanId());
            if (plan != null) {
                vo.setPlanName(plan.getName());
            }
        }
        return vo;
    }

    /**
     * 视频实体 + 学习记录 → 视频学习情况 VO
     */
    private VideoStudyVO toVideoStudyVO(TrCourseVideo video, TrVideoStudyRecord record) {
        VideoStudyVO vo = new VideoStudyVO();
        vo.setId(video.getId());
        vo.setName(video.getName());
        vo.setFileName(video.getFileName());
        vo.setDuration(video.getDuration());
        vo.setSort(video.getSort());
        if (record == null) {
            vo.setProgress(BigDecimal.ZERO);
            vo.setLastPosition(0);
            vo.setIsCompleted(0);
            vo.setStudyDuration(0);
        } else {
            vo.setProgress(record.getVideoProgress() == null ? BigDecimal.ZERO : record.getVideoProgress());
            vo.setLastPosition(record.getLastPosition() == null ? 0 : record.getLastPosition());
            vo.setIsCompleted(record.getIsCompleted() == null ? 0 : record.getIsCompleted());
            vo.setStudyDuration(record.getStudyDuration() == null ? 0 : record.getStudyDuration());
        }
        return vo;
    }

    /**
     * 学习记录实体转 VO（回填任务名、课程名、视频名）
     */
    private StudyRecordVO toStudyRecordVO(TrVideoStudyRecord record) {
        StudyRecordVO vo = new StudyRecordVO();
        vo.setId(record.getId());
        vo.setTaskId(record.getTaskId());
        vo.setVideoId(record.getVideoId());
        vo.setStudyDuration(record.getStudyDuration());
        vo.setVideoProgress(record.getVideoProgress());
        vo.setLastPosition(record.getLastPosition());
        vo.setIsCompleted(record.getIsCompleted());
        vo.setLastStudyTime(record.getLastStudyTime());
        TrTrainingTask task = record.getTaskId() == null ? null : taskMapper.selectById(record.getTaskId());
        if (task != null) {
            vo.setTaskName(task.getName());
            vo.setCourseId(task.getCourseId());
            if (task.getCourseId() != null) {
                TrCourse course = courseMapper.selectById(task.getCourseId());
                if (course != null) {
                    vo.setCourseName(course.getName());
                }
            }
        }
        TrCourseVideo video = record.getVideoId() == null ? null : videoMapper.selectById(record.getVideoId());
        if (video != null) {
            vo.setVideoName(video.getName());
        }
        return vo;
    }

    /**
     * 考试记录实体转 VO（回填任务名、课程名、试卷名、及格分）
     */
    private ExamRecordVO toExamRecordVO(TrExamRecord record) {
        ExamRecordVO vo = new ExamRecordVO();
        vo.setRecordId(record.getId());
        vo.setTaskId(record.getTaskId());
        vo.setScore(record.getScore());
        vo.setPass(record.getPass());
        vo.setRetryCount(record.getRetryCount());
        vo.setStatus(record.getStatus());
        vo.setSubmitTime(record.getSubmitTime());
        vo.setDuration(record.getDuration());
        TrExamPaper paper = record.getPaperId() == null ? null : paperMapper.selectById(record.getPaperId());
        if (paper != null) {
            vo.setPaperName(paper.getName());
            vo.setPassScore(paper.getPassScore());
        }
        TrTrainingTask task = record.getTaskId() == null ? null : taskMapper.selectById(record.getTaskId());
        if (task != null) {
            vo.setTaskName(task.getName());
            if (task.getCourseId() != null) {
                TrCourse course = courseMapper.selectById(task.getCourseId());
                if (course != null) {
                    vo.setCourseName(course.getName());
                }
            }
        }
        return vo;
    }

    /**
     * 构建考试信息 VO（含是否可考与不可考原因）
     */
    private ExamInfoVO buildExamInfo(TrTrainingTask task, TrCourse course,
                                     List<TrCourseVideo> videos, Map<Long, TrVideoStudyRecord> recordMap) {
        ExamInfoVO vo = new ExamInfoVO();
        vo.setTaskId(task.getId());
        TrExamPaper paper = findPublishedPaper(task.getCourseId());
        if (paper == null) {
            vo.setCanExam(false);
            vo.setReason("该课程尚未配置已发布的试卷");
            vo.setUsedRetry(countSubmittedRecords(task.getId(), task.getUserId()));
            vo.setMaxRetry(resolveMaxRetry(task, null));
            return vo;
        }
        vo.setPaperId(paper.getId());
        vo.setPaperName(paper.getName());
        vo.setDuration(paper.getExamDuration());
        vo.setTotalScore(paper.getTotalScore());
        vo.setPassScore(paper.getPassScore());
        vo.setQuestionCount(paper.getQuestionCount());
        int maxRetry = resolveMaxRetry(task, paper);
        int used = countSubmittedRecords(task.getId(), task.getUserId());
        vo.setMaxRetry(maxRetry);
        vo.setUsedRetry(used);
        vo.setRemainRetry(Math.max(maxRetry - used, 0));

        // 上次成绩
        TrExamRecord last = examRecordMapper.selectOne(new LambdaQueryWrapper<TrExamRecord>()
                .eq(TrExamRecord::getTaskId, task.getId())
                .eq(TrExamRecord::getUserId, task.getUserId())
                .ne(TrExamRecord::getStatus, EXAM_STATUS_IN_PROGRESS)
                .orderByDesc(TrExamRecord::getSubmitTime)
                .last("LIMIT 1"));
        if (last != null) {
            vo.setLastScore(last.getScore());
            vo.setLastPass(last.getPass());
        }

        // 是否可考：未通过 && 有剩余次数 && 已完成学习；被退回的任务同样放行，允许补考
        boolean rejected = Objects.equals(task.getReviewStatus(), REVIEW_REJECTED);
        if (!rejected && (Objects.equals(task.getExamPass(), 1) || Objects.equals(task.getStatus(), STATUS_COMPLETED))) {
            vo.setCanExam(false);
            vo.setReason("考试已通过，无需重复考试");
        } else if (vo.getRemainRetry() <= 0) {
            vo.setCanExam(false);
            vo.setReason("考试次数已用完，请联系培训管理员");
        } else if (!isAllVideoCompleted(videos, recordMap)) {
            vo.setCanExam(false);
            vo.setReason("请先完成全部视频学习后再参加考试");
        } else {
            vo.setCanExam(true);
        }
        return vo;
    }

    /**
     * 统计任务下已交卷的考试次数
     */
    private int countSubmittedRecords(Long taskId, Long userId) {
        return examRecordMapper.selectCount(new LambdaQueryWrapper<TrExamRecord>()
                .eq(TrExamRecord::getTaskId, taskId)
                .eq(TrExamRecord::getUserId, userId)
                .ne(TrExamRecord::getStatus, EXAM_STATUS_IN_PROGRESS)).intValue();
    }

    /**
     * 解析最大考试次数：优先取任务配置，其次试卷配置，最后默认 3
     */
    private int resolveMaxRetry(TrTrainingTask task, TrExamPaper paper) {
        if (task.getMaxRetry() != null && task.getMaxRetry() > 0) {
            return task.getMaxRetry();
        }
        if (paper != null && paper.getMaxRetry() != null && paper.getMaxRetry() > 0) {
            return paper.getMaxRetry();
        }
        return 3;
    }

    /**
     * 判断任务是否已逾期（未完成且已过截止时间）
     * <p>
     * 已进入审核链（reviewStatus 非空）的任务不再判定逾期：
     * 学习与考试已完成，仅剩审核流转，不应因审核耗时被判为逾期。
     */
    private boolean isOverdue(TrTrainingTask task) {
        if (task.getReviewStatus() != null) {
            return false;
        }
        return !Objects.equals(task.getStatus(), STATUS_COMPLETED)
                && task.getDeadline() != null
                && task.getDeadline().isBefore(LocalDateTime.now());
    }

    /**
     * 计算任务展示状态码
     * <p>
     * 优先级：已退回(7) > 审核中(5/6) > 已完成(3) > 已逾期(4) > 执行状态(0/1/2)。
     */
    private int displayStatusCode(TrTrainingTask task) {
        Integer reviewStatus = task.getReviewStatus();
        Integer status = task.getStatus();
        // 1. 被退回：优先展示「已退回」，提示学员需重新学习考试
        if (Objects.equals(reviewStatus, REVIEW_REJECTED)) {
            return STATUS_DISPLAY_REJECTED;
        }
        // 2. 学习考试已达标：按审核阶段展示
        if (Objects.equals(status, STATUS_COMPLETED)) {
            if (Objects.equals(reviewStatus, REVIEW_PENDING_LEADER)) {
                return STATUS_DISPLAY_PENDING_LEADER;
            }
            if (Objects.equals(reviewStatus, REVIEW_PENDING_QA)) {
                return STATUS_DISPLAY_PENDING_QA;
            }
            // reviewStatus=2（已复核通过）或 null（历史数据）均按「已完成」展示
            return STATUS_COMPLETED;
        }
        // 3. 未达标：动态逾期判断，否则取原始执行状态
        if (isOverdue(task)) {
            return STATUS_OVERDUE;
        }
        return status == null ? STATUS_NOT_STARTED : status;
    }

    /**
     * 状态码转前端字符串枚举
     */
    private String statusKey(int statusCode) {
        switch (statusCode) {
            case STATUS_STUDYING:
                return "studying";
            case STATUS_WAITING_EXAM:
                return "waiting_exam";
            case STATUS_COMPLETED:
                return "completed";
            case STATUS_OVERDUE:
                return "overdue";
            case STATUS_DISPLAY_PENDING_LEADER:
                return "pending_leader_review";
            case STATUS_DISPLAY_PENDING_QA:
                return "pending_qa_review";
            case STATUS_DISPLAY_REJECTED:
                return "rejected";
            default:
                return "not_started";
        }
    }

    /**
     * 审核状态数字转中文文案
     */
    private String reviewStatusText(Integer reviewStatus) {
        if (reviewStatus == null) {
            return null;
        }
        switch (reviewStatus) {
            case REVIEW_PENDING_LEADER:
                return "待主管审核";
            case REVIEW_PENDING_QA:
                return "待QA复核";
            case REVIEW_APPROVED:
                return "已复核通过";
            case REVIEW_REJECTED:
                return "已退回";
            default:
                return null;
        }
    }

    /**
     * 解析退回意见
     * <p>
     * 仅当任务处于「已退回」（reviewStatus=3）时返回意见：优先取 QA 复核意见，
     * 无则取主管审核意见（两级审核任一环节退回都会写入对应阶段的备注）。
     */
    private String resolveRejectRemark(TrTrainingTask task) {
        if (!Objects.equals(task.getReviewStatus(), REVIEW_REJECTED)) {
            return null;
        }
        if (StrUtil.isNotBlank(task.getQaReviewRemark())) {
            return task.getQaReviewRemark();
        }
        return task.getLeaderReviewRemark();
    }

    /**
     * 应用状态筛选条件（支持动态「已逾期」）
     */
    private void applyStatusFilter(LambdaQueryWrapper<TrTrainingTask> wrapper, String status) {
        if (StrUtil.isBlank(status)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        switch (status) {
            case "not_started":
                wrapper.eq(TrTrainingTask::getStatus, STATUS_NOT_STARTED)
                        .and(w -> w.isNull(TrTrainingTask::getDeadline).or().ge(TrTrainingTask::getDeadline, now));
                break;
            case "studying":
                wrapper.eq(TrTrainingTask::getStatus, STATUS_STUDYING)
                        .and(w -> w.isNull(TrTrainingTask::getDeadline).or().ge(TrTrainingTask::getDeadline, now));
                break;
            case "waiting_exam":
                // 排除「已退回」的任务：被退回任务虽回到待考试，但应归入「已退回」状态单独展示
                wrapper.eq(TrTrainingTask::getStatus, STATUS_WAITING_EXAM)
                        .and(w -> w.isNull(TrTrainingTask::getDeadline).or().ge(TrTrainingTask::getDeadline, now))
                        .and(w -> w.isNull(TrTrainingTask::getReviewStatus)
                                .or().ne(TrTrainingTask::getReviewStatus, REVIEW_REJECTED));
                break;
            case "completed":
                // 已完成 = 学习考试达标 且（未进入审核链 或 已通过两级审核）
                wrapper.eq(TrTrainingTask::getStatus, STATUS_COMPLETED)
                        .and(w -> w.isNull(TrTrainingTask::getReviewStatus)
                                .or().eq(TrTrainingTask::getReviewStatus, REVIEW_APPROVED));
                break;
            case "overdue":
                // 逾期仅针对未进入审核链的任务
                wrapper.ne(TrTrainingTask::getStatus, STATUS_COMPLETED)
                        .isNull(TrTrainingTask::getReviewStatus)
                        .isNotNull(TrTrainingTask::getDeadline)
                        .lt(TrTrainingTask::getDeadline, now);
                break;
            case "pending_review":
                // 待审核（含待主管审核与待QA复核）
                wrapper.in(TrTrainingTask::getReviewStatus, REVIEW_PENDING_LEADER, REVIEW_PENDING_QA);
                break;
            case "rejected":
                // 已退回：需学员重新学习考试
                wrapper.eq(TrTrainingTask::getReviewStatus, REVIEW_REJECTED);
                break;
            default:
                throw new BusinessException(ErrorCode.PARAM_ERROR, "任务状态不合法");
        }
    }

    /**
     * 统计指定状态任务数量（status 为空表示全部）
     */
    private Long countTasks(Long userId, String status) {
        LambdaQueryWrapper<TrTrainingTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrTrainingTask::getUserId, userId);
        applyStatusFilter(wrapper, status);
        return taskMapper.selectCount(wrapper);
    }

    /**
     * 构建开考/续考返回模型
     */
    private ExamStartVO buildExamStartVO(TrExamRecord record, TrExamPaper paper) {
        ExamStartVO vo = new ExamStartVO();
        vo.setRecordId(record.getId());
        vo.setTaskId(record.getTaskId());
        vo.setPaperId(paper.getId());
        vo.setPaperName(paper.getName());
        vo.setDuration(paper.getExamDuration());
        vo.setTotalScore(paper.getTotalScore());
        vo.setPassScore(paper.getPassScore());
        vo.setStartTime(record.getStartTime());
        vo.setRetryCount(record.getRetryCount());
        // 交卷截止时间 = 开始时间 + 考试时长
        LocalDateTime deadline = null;
        if (record.getStartTime() != null && paper.getExamDuration() != null) {
            deadline = record.getStartTime().plusMinutes(paper.getExamDuration());
        }
        vo.setDeadline(deadline);
        vo.setRemainSeconds(deadline == null ? null
                : Math.max(Duration.between(LocalDateTime.now(), deadline).getSeconds(), 0));

        // 读取题目快照并组装题目（不含答案与解析）
        List<TrExamAnswer> rows = examAnswerMapper.selectList(new LambdaQueryWrapper<TrExamAnswer>()
                .eq(TrExamAnswer::getRecordId, record.getId())
                .orderByAsc(TrExamAnswer::getId));
        List<ExamQuestionVO> questions = new ArrayList<>();
        List<ExamSubmitDTO.AnswerItem> savedAnswers = new ArrayList<>();
        int sort = 1;
        for (TrExamAnswer row : rows) {
            TrQuestion question = questionMapper.selectById(row.getQuestionId());
            if (question == null) {
                continue;
            }
            ExamQuestionVO qv = new ExamQuestionVO();
            qv.setId(question.getId());
            qv.setQuestionType(question.getQuestionType());
            qv.setTypeName(typeName(question.getQuestionType()));
            qv.setContent(question.getContent());
            // 答题阶段严禁下发正确答案：选项中的 isAnswer 标记必须清除，
            // 否则学员可通过抓包/浏览器 F12 直接看到正确答案（交卷后的成绩单才会返回答案与解析）
            List<QuestionOptionDTO> safeOptions = parseOptions(question.getOptions());
            for (QuestionOptionDTO opt : safeOptions) {
                opt.setIsAnswer(null);
            }
            qv.setOptions(safeOptions);
            qv.setScore(row.getScore());
            qv.setSort(sort++);
            questions.add(qv);
            // 已作答内容用于刷新恢复
            if (StrUtil.isNotBlank(row.getAnswer())) {
                ExamSubmitDTO.AnswerItem item = new ExamSubmitDTO.AnswerItem();
                item.setQuestionId(question.getId());
                item.setAnswer(row.getAnswer());
                savedAnswers.add(item);
            }
        }
        vo.setQuestions(questions);
        vo.setSavedAnswers(savedAnswers);
        return vo;
    }

    /**
     * 构建成绩单（含逐题解析）
     */
    private ExamResultVO buildExamResult(TrExamRecord record, TrExamPaper paper,
                                         List<TrExamAnswer> rows, int correctCount) {
        ExamResultVO vo = new ExamResultVO();
        vo.setRecordId(record.getId());
        vo.setTaskId(record.getTaskId());
        vo.setPaperId(paper.getId());
        vo.setPaperName(paper.getName());
        vo.setScore(record.getScore());
        vo.setTotalScore(paper.getTotalScore());
        vo.setPassScore(paper.getPassScore());
        vo.setPass(record.getPass());
        vo.setStatus(record.getStatus());
        vo.setRetryCount(record.getRetryCount());
        vo.setSubmitTime(record.getSubmitTime());
        vo.setDuration(record.getDuration());
        vo.setQuestionCount(rows.size());
        vo.setCorrectCount(correctCount);

        List<ExamResultItemVO> items = new ArrayList<>();
        int sort = 1;
        for (TrExamAnswer row : rows) {
            TrQuestion question = questionMapper.selectById(row.getQuestionId());
            if (question == null) {
                continue;
            }
            ExamResultItemVO item = new ExamResultItemVO();
            item.setId(question.getId());
            item.setQuestionType(question.getQuestionType());
            item.setTypeName(typeName(question.getQuestionType()));
            item.setContent(question.getContent());
            item.setOptions(parseOptions(question.getOptions()));
            item.setScore(row.getScore());
            item.setUserAnswer(row.getAnswer());
            item.setCorrectAnswer(question.getAnswer());
            item.setIsCorrect(row.getIsCorrect());
            item.setEarnedScore(row.getScore());
            item.setAnalysis(question.getAnalysis());
            item.setSort(sort++);
            items.add(item);
        }
        vo.setItems(items);
        return vo;
    }

    /**
     * 构建本次考试的题目快照（题号 + 满分）
     * <p>
     * 手动组卷：直接读取 tr_paper_question；随机组卷：按规则随机抽取并固化。
     */
    private List<AnswerSnapshot> buildQuestionSnapshots(TrExamPaper paper) {
        List<AnswerSnapshot> snapshots = new ArrayList<>();
        if (!"random".equalsIgnoreCase(paper.getCompositionType())) {
            // 手动组卷：按排序读取试卷题目
            List<TrPaperQuestion> paperQuestions = paperQuestionMapper.selectList(
                    new LambdaQueryWrapper<TrPaperQuestion>()
                            .eq(TrPaperQuestion::getPaperId, paper.getId())
                            .orderByAsc(TrPaperQuestion::getSort));
            for (TrPaperQuestion pq : paperQuestions) {
                snapshots.add(new AnswerSnapshot(pq.getQuestionId(),
                        pq.getScore() == null ? 0 : pq.getScore()));
            }
            return snapshots;
        }

        // 随机组卷：按规则抽取
        if (StrUtil.isBlank(paper.getRules())) {
            return snapshots;
        }
        List<PaperRuleDTO> rules = JSONUtil.toList(paper.getRules(), PaperRuleDTO.class);
        Set<Long> used = new java.util.HashSet<>();
        for (PaperRuleDTO rule : rules) {
            Integer typeInt = typeToInt(rule.getType());
            if (typeInt == null || rule.getCount() == null || rule.getCount() <= 0) {
                continue;
            }
            LambdaQueryWrapper<TrQuestion> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TrQuestion::getQuestionType, typeInt);
            wrapper.eq(TrQuestion::getStatus, 1);
            Integer diffInt = difficultyToInt(rule.getDifficulty());
            if (diffInt != null) {
                wrapper.eq(TrQuestion::getDifficulty, diffInt);
            }
            // 试卷绑定课程时，仅从该课程题库抽取
            if (paper.getCourseId() != null) {
                wrapper.eq(TrQuestion::getCourseId, paper.getCourseId());
            }
            if (!used.isEmpty()) {
                wrapper.notIn(TrQuestion::getId, used);
            }
            List<TrQuestion> candidates = questionMapper.selectList(wrapper);
            if (candidates.isEmpty()) {
                continue;
            }
            // 打乱后取前 N 题
            Collections.shuffle(candidates);
            int take = Math.min(rule.getCount(), candidates.size());
            for (int i = 0; i < take; i++) {
                TrQuestion q = candidates.get(i);
                used.add(q.getId());
                int score = rule.getScore() == null ? (q.getScore() == null ? 0 : q.getScore()) : rule.getScore();
                snapshots.add(new AnswerSnapshot(q.getId(), score));
            }
        }
        return snapshots;
    }

    /**
     * 判分：按题型分别处理
     *
     * @param question   题目
     * @param userAnswer 考生答案（可能为 null，表示未作答）
     * @return 是否答对
     */
    private boolean judge(TrQuestion question, String userAnswer) {
        if (StrUtil.isBlank(userAnswer)) {
            return false;
        }
        String correct = StrUtil.trimToEmpty(question.getAnswer());
        if (StrUtil.isBlank(correct)) {
            return false;
        }
        Integer type = question.getQuestionType();
        if (type == null) {
            return false;
        }
        switch (type) {
            case 1:
                // 单选：忽略大小写与空白
                return correct.trim().equalsIgnoreCase(userAnswer.trim());
            case 2:
                // 多选：字母集合一致即正确（排序后比较）
                return normalizeLetters(correct).equals(normalizeLetters(userAnswer));
            case 3:
                // 判断：归一化「正确/错误」的多种写法
                return normalizeJudge(correct).equals(normalizeJudge(userAnswer));
            case 4:
                // 填空：忽略大小写与首尾空白
                return correct.trim().equalsIgnoreCase(userAnswer.trim());
            case 5:
                // 简答：采用「参考答案与考生答案互相包含」的启发式判分
                String ua = userAnswer.trim();
                String ca = correct.trim();
                return !ua.isEmpty() && !ca.isEmpty() && (ua.contains(ca) || ca.contains(ua));
            default:
                return false;
        }
    }

    /**
     * 多选答案归一化：仅保留字母并升序排列（如 "BA" → "AB"）
     */
    private String normalizeLetters(String value) {
        if (value == null) {
            return "";
        }
        char[] chars = value.replaceAll("[^A-Za-z]", "").toUpperCase().toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }

    /**
     * 判断题答案归一化：统一为「正确」或「错误」
     */
    private String normalizeJudge(String value) {
        String v = StrUtil.trimToEmpty(value).toUpperCase();
        if (v.equals("正确") || v.equals("对") || v.equals("A") || v.equals("TRUE") || v.equals("T") || v.equals("1")) {
            return "正确";
        }
        if (v.equals("错误") || v.equals("错") || v.equals("B") || v.equals("FALSE") || v.equals("F") || v.equals("0")) {
            return "错误";
        }
        return v;
    }

    /**
     * 解析题目选项 JSON 为列表（非选择题返回空列表）
     */
    private List<QuestionOptionDTO> parseOptions(String options) {
        if (StrUtil.isBlank(options)) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(options, QuestionOptionDTO.class);
        } catch (Exception e) {
            log.warn("题目选项解析失败：{}", options);
            return new ArrayList<>();
        }
    }

    /**
     * 题型数字转名称
     */
    private String typeName(Integer type) {
        if (type == null) {
            return "";
        }
        switch (type) {
            case 1:
                return "单选题";
            case 2:
                return "多选题";
            case 3:
                return "判断题";
            case 4:
                return "填空题";
            case 5:
                return "简答题";
            default:
                return "";
        }
    }

    /**
     * 组卷规则题型字符串转数字
     */
    private Integer typeToInt(String type) {
        if (StrUtil.isBlank(type)) {
            return null;
        }
        switch (type) {
            case "single":
                return 1;
            case "multiple":
                return 2;
            case "judge":
                return 3;
            case "fill":
                return 4;
            case "short_answer":
                return 5;
            default:
                return null;
        }
    }

    /**
     * 组卷规则难度字符串转数字
     */
    private Integer difficultyToInt(String difficulty) {
        if (StrUtil.isBlank(difficulty)) {
            return null;
        }
        switch (difficulty) {
            case "easy":
                return 1;
            case "medium":
                return 2;
            case "hard":
                return 3;
            default:
                return null;
        }
    }

    /**
     * 查询用户角色编码列表
     */
    private List<String> roleCodes(Long userId) {
        List<SysUserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(SysRole::getCode).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 错题收集：同一学员同一题已存在则累加答错次数并重置为未掌握，否则新增错题记录
     *
     * @param userId   学员ID
     * @param question 答错题目
     */
    private void saveWrongQuestion(Long userId, TrQuestion question) {
        if (question == null || question.getId() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        TrWrongQuestion exist = wrongQuestionMapper.selectOne(new LambdaQueryWrapper<TrWrongQuestion>()
                .eq(TrWrongQuestion::getUserId, userId)
                .eq(TrWrongQuestion::getQuestionId, question.getId())
                .last("LIMIT 1"));
        if (exist == null) {
            TrWrongQuestion wrong = new TrWrongQuestion();
            wrong.setUserId(userId);
            wrong.setQuestionId(question.getId());
            wrong.setCourseId(question.getCourseId());
            wrong.setWrongCount(1);
            wrong.setLastWrongTime(now);
            wrong.setMastered(0);
            wrong.setLastPracticeCorrect(0);
            wrongQuestionMapper.insert(wrong);
        } else {
            TrWrongQuestion update = new TrWrongQuestion();
            update.setId(exist.getId());
            update.setWrongCount((exist.getWrongCount() == null ? 0 : exist.getWrongCount()) + 1);
            update.setLastWrongTime(now);
            update.setMastered(0);
            update.setLastPracticeCorrect(0);
            wrongQuestionMapper.updateById(update);
        }
    }

    /**
     * 查询本人全部任务并按课程ID索引（同一课程存在多条任务时保留最新一条）
     */
    private Map<Long, TrTrainingTask> mapUserTasksByCourse(Long userId) {
        List<TrTrainingTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<TrTrainingTask>()
                .eq(TrTrainingTask::getUserId, userId)
                .orderByAsc(TrTrainingTask::getCreateTime));
        Map<Long, TrTrainingTask> map = new HashMap<>();
        for (TrTrainingTask task : tasks) {
            if (task.getCourseId() != null) {
                // 按创建时间升序读取，后写入的自然覆盖先前的，最终保留最新任务
                map.put(task.getCourseId(), task);
            }
        }
        return map;
    }

    /**
     * 课程实体转课程 VO（列表/详情共用，详情额外字段由调用方补充）
     */
    private StudyCourseVO toStudyCourseVO(TrCourse course, String categoryName,
                                          Map<Long, TrTrainingTask> taskByCourse,
                                          Map<Long, Long> firstVideoIds) {
        StudyCourseVO vo = new StudyCourseVO();
        vo.setId(course.getId());
        vo.setName(course.getName());
        vo.setCode(course.getCode());
        vo.setCategoryName(categoryName);
        vo.setSopCode(course.getSopCode());
        vo.setSopVersion(course.getSopVersion());
        vo.setTrainingHours(course.getTrainingHours());
        vo.setPassScore(course.getPassScore());
        vo.setRetrainPeriod(course.getRetrainPeriod());
        vo.setObjective(course.getTrainingObjective());
        // 回填第一个启用视频ID（供前端加载封面）
        vo.setFirstVideoId(firstVideoIds == null ? null : firstVideoIds.get(course.getId()));
        // 报名状态由本人任务反推
        TrTrainingTask task = taskByCourse.get(course.getId());
        vo.setEnrolled(task != null);
        vo.setTaskId(task == null ? null : task.getId());
        return vo;
    }

    /**
     * 批量查询课程分类名称（categoryId -> name）
     */
    private Map<Long, String> categoryNameMap(List<TrCourse> courses) {
        Set<Long> ids = courses.stream().map(TrCourse::getCategoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(TrCourseCategory::getId, TrCourseCategory::getName, (a, b) -> a));
    }

    /**
     * 批量回填每个课程的第一个启用视频ID（用于前端加载视频封面）
     * <p>
     * 一次性查询避免 N+1，按课程分组后取每个组内最早创建的启用视频。
     *
     * @param courses 课程列表
     * @return Map<课程ID, 第一个启用视频ID>；课程无视频时 key 不存在（非 null value）
     */
    private Map<Long, Long> firstVideoIdMap(List<TrCourse> courses) {
        Set<Long> courseIds = courses.stream().map(TrCourse::getId).collect(Collectors.toSet());
        if (courseIds.isEmpty()) {
            return new HashMap<>();
        }
        // 查所有启用视频，按课程分组后取每组第一个
        List<TrCourseVideo> videos = videoMapper.selectList(new LambdaQueryWrapper<TrCourseVideo>()
                .in(TrCourseVideo::getCourseId, courseIds)
                .eq(TrCourseVideo::getStatus, 1)
                .orderByAsc(TrCourseVideo::getUploadTime));
        return videos.stream().collect(Collectors.groupingBy(
                TrCourseVideo::getCourseId,
                Collectors.collectingAndThen(Collectors.toList(), list -> list.get(0).getId())
        ));
    }

    /**
     * 查询单个课程分类名称
     */
    private String resolveCategoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        TrCourseCategory category = categoryMapper.selectById(categoryId);
        return category == null ? null : category.getName();
    }

    /**
     * 批量查询课程名称（courseId -> name）
     */
    private Map<Long, String> courseNameMapByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        return courseMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(TrCourse::getId, TrCourse::getName, (a, b) -> a));
    }

    /**
     * 批量查询题目（questionId -> 题目实体）
     */
    private Map<Long, TrQuestion> mapQuestions(List<TrWrongQuestion> rows) {
        Set<Long> ids = rows.stream().map(TrWrongQuestion::getQuestionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return questionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(TrQuestion::getId, q -> q, (a, b) -> a));
    }

    /**
     * 查询本人最近一次错误答案（questionId -> 答案文本）
     * <p>
     * 依赖自增ID顺序判定先后：后出现的记录覆盖先前的，最终保留最近一次答错答案。
     */
    private Map<Long, String> latestWrongAnswers(Long userId) {
        List<TrExamRecord> records = examRecordMapper.selectList(new LambdaQueryWrapper<TrExamRecord>()
                .eq(TrExamRecord::getUserId, userId));
        if (records.isEmpty()) {
            return new HashMap<>();
        }
        List<Long> recordIds = records.stream().map(TrExamRecord::getId).collect(Collectors.toList());
        List<TrExamAnswer> answers = examAnswerMapper.selectList(new LambdaQueryWrapper<TrExamAnswer>()
                .in(TrExamAnswer::getRecordId, recordIds)
                .eq(TrExamAnswer::getIsCorrect, 0)
                .orderByAsc(TrExamAnswer::getId));
        Map<Long, String> map = new HashMap<>();
        for (TrExamAnswer answer : answers) {
            map.put(answer.getQuestionId(), answer.getAnswer());
        }
        return map;
    }

    /**
     * 计算成绩所属区间下标：0-59 / 60-69 / 70-79 / 80-89 / 90-100
     */
    private int scoreBucketIndex(int score) {
        if (score < 60) {
            return 0;
        }
        if (score < 70) {
            return 1;
        }
        if (score < 80) {
            return 2;
        }
        if (score < 90) {
            return 3;
        }
        return 4;
    }

    /**
     * 由区间计数构建成绩分布列表
     */
    private List<AnalysisOverviewVO.ScoreRangeVO> buildScoreDist(int[] buckets) {
        String[] ranges = {"0-59", "60-69", "70-79", "80-89", "90-100"};
        List<AnalysisOverviewVO.ScoreRangeVO> list = new ArrayList<>();
        for (int i = 0; i < ranges.length; i++) {
            list.add(new AnalysisOverviewVO.ScoreRangeVO(ranges[i], buckets[i]));
        }
        return list;
    }

    /**
     * 构建近 6 个月学习趋势（含当月）
     * <p>
     * 学习时长按视频学习记录的「最后学习时间」归属月份累加；
     * 考试次数与通过次数按考试记录的提交时间归属月份统计。
     */
    private List<AnalysisOverviewVO.MonthlyStatVO> buildMonthlyTrend(Long userId, List<TrExamRecord> records) {
        YearMonth current = YearMonth.now();
        Map<String, AnalysisOverviewVO.MonthlyStatVO> monthMap = new LinkedHashMap<>();
        List<AnalysisOverviewVO.MonthlyStatVO> monthly = new ArrayList<>();
        // 时间升序：从 5 个月前到当月
        for (int i = 5; i >= 0; i--) {
            String key = current.minusMonths(i).toString();
            AnalysisOverviewVO.MonthlyStatVO item = new AnalysisOverviewVO.MonthlyStatVO(key);
            monthMap.put(key, item);
            monthly.add(item);
        }
        // 学习时长归集
        List<TrVideoStudyRecord> videoRecords = recordMapper.selectList(new LambdaQueryWrapper<TrVideoStudyRecord>()
                .eq(TrVideoStudyRecord::getUserId, userId));
        for (TrVideoStudyRecord record : videoRecords) {
            if (record.getLastStudyTime() == null) {
                continue;
            }
            AnalysisOverviewVO.MonthlyStatVO item = monthMap.get(YearMonth.from(record.getLastStudyTime()).toString());
            if (item != null) {
                item.setStudyDuration(item.getStudyDuration()
                        + (record.getStudyDuration() == null ? 0 : record.getStudyDuration()));
            }
        }
        // 考试次数与通过次数归集
        for (TrExamRecord record : records) {
            if (record.getSubmitTime() == null) {
                continue;
            }
            AnalysisOverviewVO.MonthlyStatVO item = monthMap.get(YearMonth.from(record.getSubmitTime()).toString());
            if (item != null) {
                item.setExamCount(item.getExamCount() + 1);
                if (Objects.equals(record.getPass(), 1)) {
                    item.setPassCount(item.getPassCount() + 1);
                }
            }
        }
        return monthly;
    }

    /**
     * 题目快照内部模型（题号 + 满分）
     */
    private static class AnswerSnapshot {
        /** 题目ID */
        final Long questionId;
        /** 题目满分 */
        final int fullScore;

        AnswerSnapshot(Long questionId, int fullScore) {
            this.questionId = questionId;
            this.fullScore = fullScore;
        }
    }
}
