package com.gmp.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gmp.common.BusinessException;
import com.gmp.common.ErrorCode;
import com.gmp.dto.offline.AttendanceManualSignDTO;
import com.gmp.dto.offline.AttendanceRecordVO;
import com.gmp.dto.offline.AttendanceSignDTO;
import com.gmp.dto.offline.OfflineTrainingSaveDTO;
import com.gmp.dto.offline.OfflineTrainingVO;
import com.gmp.dto.offline.StudyOfflineRecordVO;
import com.gmp.dto.offline.StudyOfflineTrainingVO;
import com.gmp.entity.SysDepartment;
import com.gmp.entity.SysUser;
import com.gmp.entity.TrAttendanceRecord;
import com.gmp.entity.TrCourse;
import com.gmp.entity.TrOfflineTraining;
import com.gmp.entity.TrTrainingPlan;
import com.gmp.mapper.SysDepartmentMapper;
import com.gmp.mapper.SysUserMapper;
import com.gmp.mapper.TrAttendanceRecordMapper;
import com.gmp.mapper.TrCourseMapper;
import com.gmp.mapper.TrOfflineTrainingMapper;
import com.gmp.mapper.TrTrainingPlanMapper;
import com.gmp.service.TrOfflineTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 线下培训登记与电子签到 Service 实现类
 * <p>
 * 业务要点：
 * 1. 签到窗口：优先使用场次显式配置的 signStartTime / signEndTime；
 *    未配置时默认「培训开始前30分钟 至 培训结束」。
 * 2. 场次阶段（未开始 / 签到中 / 已结束 / 已取消）根据当前时间动态计算，
 *    避免数据库状态字段与真实时间不一致导致展示错误。
 * 3. 每场培训由系统生成 6 位数字签到码，学员现场凭码签到；管理员可对异常情况补签。
 * 4. 签到记录是 GMP 3.2.4.10「培训文件」要求留存的可追溯凭证，故场次一旦产生签到记录即禁止删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrOfflineTrainingServiceImpl extends ServiceImpl<TrOfflineTrainingMapper, TrOfflineTraining>
        implements TrOfflineTrainingService {

    /** 场次状态：未开始 */
    private static final int STATUS_NOT_STARTED = 0;
    /** 场次状态：进行中 */
    private static final int STATUS_ONGOING = 1;
    /** 场次状态：已结束 */
    private static final int STATUS_ENDED = 2;
    /** 场次状态：已取消 */
    private static final int STATUS_CANCELLED = 3;

    /** 签到方式：学员自主签到 */
    private static final int SIGN_TYPE_SELF = 1;
    /** 签到方式：管理员补签 */
    private static final int SIGN_TYPE_MANUAL = 2;

    /** 默认提前开放签到分钟数（未配置签到开始时间时使用） */
    private static final int DEFAULT_SIGN_AHEAD_MINUTES = 30;

    /** 签到记录 Mapper */
    private final TrAttendanceRecordMapper attendanceMapper;
    /** 用户 Mapper（签到明细回填姓名、工号、部门） */
    private final SysUserMapper sysUserMapper;
    /** 部门 Mapper（签到明细回填部门名称） */
    private final SysDepartmentMapper sysDepartmentMapper;
    /** 培训计划 Mapper（场次列表回填计划名称） */
    private final TrTrainingPlanMapper trTrainingPlanMapper;
    /** 课程 Mapper（场次列表回填课程名称） */
    private final TrCourseMapper trCourseMapper;

    @Override
    public Page<OfflineTrainingVO> pageTrainings(Integer pageNum, Integer pageSize, String title,
                                                 String phase, String startDate, String endDate) {
        // 基础查询条件：培训主题模糊匹配 + 培训开始日期范围
        LambdaQueryWrapper<TrOfflineTraining> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(title)) {
            wrapper.like(TrOfflineTraining::getTitle, title);
        }
        if (StrUtil.isNotBlank(startDate)) {
            // 下界取当日 00:00:00
            wrapper.ge(TrOfflineTraining::getStartTime, LocalDate.parse(startDate).atStartOfDay());
        }
        if (StrUtil.isNotBlank(endDate)) {
            // 上界取当日 23:59:59，避免使用 LocalTime.MAX 的纳秒进位问题
            wrapper.le(TrOfflineTraining::getStartTime, LocalDate.parse(endDate).atTime(LocalTime.of(23, 59, 59)));
        }
        wrapper.orderByDesc(TrOfflineTraining::getStartTime);

        // 未指定阶段时直接走数据库分页，避免不必要的全量加载
        if (StrUtil.isBlank(phase)) {
            Page<TrOfflineTraining> entityPage = baseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
            Page<OfflineTrainingVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
            voPage.setRecords(toTrainingVOList(entityPage.getRecords()));
            return voPage;
        }

        // 指定阶段时：阶段由时间动态计算，无法在 SQL 中直接表达，
        // 故先按主题/日期取出候选集合，再在内存中过滤并手工分页。
        // 线下培训场次属于低频业务数据（年百条量级），此实现可接受；若数据量增大应改为落库状态字段。
        List<TrOfflineTraining> all = baseMapper.selectList(wrapper);
        List<TrOfflineTraining> filtered = all.stream()
                .filter(t -> phase.equals(resolvePhase(t)))
                .collect(Collectors.toList());
        int total = filtered.size();
        int fromIndex = Math.max(0, (pageNum - 1) * pageSize);
        int toIndex = Math.min(total, fromIndex + pageSize);
        List<TrOfflineTraining> pageRecords = fromIndex >= total
                ? Collections.emptyList()
                : filtered.subList(fromIndex, toIndex);

        Page<OfflineTrainingVO> voPage = new Page<>(pageNum, pageSize, total);
        voPage.setRecords(toTrainingVOList(pageRecords));
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addTraining(OfflineTrainingSaveDTO dto) {
        // 校验时间区间合法性（结束时间必须晚于开始时间）
        validateTimeRange(dto);

        TrOfflineTraining entity = buildEntity(dto);
        // 新增场次初始状态为「未开始」，实际阶段由时间动态计算
        entity.setStatus(STATUS_NOT_STARTED);
        // 生成全场唯一的 6 位签到码
        entity.setSignCode(generateUniqueSignCode());
        baseMapper.insert(entity);
        log.info("新增线下培训场次：ID={}, 主题={}, 签到码={}", entity.getId(), entity.getTitle(), entity.getSignCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTraining(OfflineTrainingSaveDTO dto) {
        TrOfflineTraining exist = baseMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "线下培训场次不存在");
        }
        // 已结束或已取消的场次不允许编辑，避免篡改历史培训记录
        String phase = resolvePhase(exist);
        if ("ended".equals(phase) || "cancelled".equals(phase)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已结束或已取消的场次不允许编辑");
        }
        validateTimeRange(dto);

        TrOfflineTraining entity = buildEntity(dto);
        entity.setId(dto.getId());
        // 签到码不允许通过编辑接口修改，需使用「重新生成签到码」操作，防止绕过审计
        entity.setSignCode(null);
        baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTraining(Long id) {
        TrOfflineTraining exist = baseMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "线下培训场次不存在");
        }
        // 已产生签到记录的场次承载培训出勤凭证，禁止删除（GMP 培训记录留存要求）
        Long signedCount = attendanceMapper.selectCount(
                new LambdaQueryWrapper<TrAttendanceRecord>().eq(TrAttendanceRecord::getTrainingId, id));
        if (signedCount != null && signedCount > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "该场次已产生 " + signedCount + " 条签到记录，为保留培训凭证不允许删除，可改为取消场次");
        }
        // 逻辑删除，底层数据保留
        baseMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishTraining(Long id) {
        TrOfflineTraining exist = baseMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "线下培训场次不存在");
        }
        if (exist.getStatus() != null && exist.getStatus() == STATUS_CANCELLED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已取消的场次不能结束");
        }
        TrOfflineTraining update = new TrOfflineTraining();
        update.setId(id);
        update.setStatus(STATUS_ENDED);
        baseMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTraining(Long id) {
        TrOfflineTraining exist = baseMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "线下培训场次不存在");
        }
        if (exist.getStatus() != null && exist.getStatus() == STATUS_ENDED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已结束的场次不能取消");
        }
        TrOfflineTraining update = new TrOfflineTraining();
        update.setId(id);
        update.setStatus(STATUS_CANCELLED);
        baseMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String refreshSignCode(Long id) {
        TrOfflineTraining exist = baseMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "线下培训场次不存在");
        }
        String newCode = generateUniqueSignCode();
        TrOfflineTraining update = new TrOfflineTraining();
        update.setId(id);
        update.setSignCode(newCode);
        baseMapper.updateById(update);
        log.info("重新生成签到码：场次ID={}, 新签到码={}", id, newCode);
        return newCode;
    }

    @Override
    public Page<AttendanceRecordVO> pageAttendance(Long trainingId, Integer pageNum, Integer pageSize, String keyword) {
        assertTrainingExists(trainingId);

        // 关键字（姓名/工号/部门）匹配需先定位用户，再按用户ID过滤签到记录
        LambdaQueryWrapper<TrAttendanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrAttendanceRecord::getTrainingId, trainingId);
        if (StrUtil.isNotBlank(keyword)) {
            List<SysUser> matchedUsers = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .like(SysUser::getRealName, keyword)
                    .or()
                    .like(SysUser::getUsername, keyword));
            if (matchedUsers.isEmpty()) {
                // 无匹配用户时返回空分页，避免退化为全表查询
                return new Page<>(pageNum, pageSize, 0);
            }
            List<Long> userIds = matchedUsers.stream().map(SysUser::getId).collect(Collectors.toList());
            wrapper.in(TrAttendanceRecord::getUserId, userIds);
        }
        wrapper.orderByDesc(TrAttendanceRecord::getSignTime);

        Page<TrAttendanceRecord> entityPage = attendanceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<AttendanceRecordVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(toAttendanceVOList(entityPage.getRecords()));
        return voPage;
    }

    @Override
    public List<AttendanceRecordVO> listAttendanceForExport(Long trainingId) {
        assertTrainingExists(trainingId);
        List<TrAttendanceRecord> records = attendanceMapper.selectList(
                new LambdaQueryWrapper<TrAttendanceRecord>()
                        .eq(TrAttendanceRecord::getTrainingId, trainingId)
                        .orderByAsc(TrAttendanceRecord::getSignTime));
        return toAttendanceVOList(records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualSign(Long trainingId, AttendanceManualSignDTO dto) {
        TrOfflineTraining training = assertTrainingExists(trainingId);
        // 已取消的场次不接收补签
        if (training.getStatus() != null && training.getStatus() == STATUS_CANCELLED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已取消的场次不能补签");
        }
        // 校验被补签学员存在且处于启用状态
        SysUser user = sysUserMapper.selectById(dto.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学员不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该学员账号已停用，不能补签");
        }
        // 防重复：同一场次同一学员仅允许一条签到记录
        Long exists = attendanceMapper.selectCount(new LambdaQueryWrapper<TrAttendanceRecord>()
                .eq(TrAttendanceRecord::getTrainingId, trainingId)
                .eq(TrAttendanceRecord::getUserId, dto.getUserId()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该学员已签到，无需重复补签");
        }

        TrAttendanceRecord record = new TrAttendanceRecord();
        record.setTrainingId(trainingId);
        record.setUserId(dto.getUserId());
        record.setSignTime(LocalDateTime.now());
        record.setSignType(SIGN_TYPE_MANUAL);
        record.setRemark(dto.getRemark());
        attendanceMapper.insert(record);
        log.info("管理员补签：场次ID={}, 学员ID={}, 操作人ID={}", trainingId, dto.getUserId(), StpUtil.getLoginIdAsLong());
    }

    @Override
    public List<StudyOfflineTrainingVO> listSignable() {
        Long userId = StpUtil.getLoginIdAsLong();
        LocalDateTime now = LocalDateTime.now();

        // 仅查询未取消、未手动结束、且培训尚未结束的场次（结束时间晚于当前时间）
        List<TrOfflineTraining> trainings = baseMapper.selectList(new LambdaQueryWrapper<TrOfflineTraining>()
                .notIn(TrOfflineTraining::getStatus, STATUS_CANCELLED, STATUS_ENDED)
                .gt(TrOfflineTraining::getEndTime, now)
                .orderByAsc(TrOfflineTraining::getStartTime));
        if (trainings.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询当前学员在这些场次中的签到记录，用于标记「已签到」
        List<Long> trainingIds = trainings.stream().map(TrOfflineTraining::getId).collect(Collectors.toList());
        List<TrAttendanceRecord> myRecords = attendanceMapper.selectList(new LambdaQueryWrapper<TrAttendanceRecord>()
                .eq(TrAttendanceRecord::getUserId, userId)
                .in(TrAttendanceRecord::getTrainingId, trainingIds));
        Map<Long, LocalDateTime> signedMap = myRecords.stream()
                .collect(Collectors.toMap(TrAttendanceRecord::getTrainingId, TrAttendanceRecord::getSignTime, (a, b) -> a));

        List<StudyOfflineTrainingVO> result = new ArrayList<>();
        for (TrOfflineTraining t : trainings) {
            StudyOfflineTrainingVO vo = new StudyOfflineTrainingVO();
            vo.setId(t.getId());
            vo.setTitle(t.getTitle());
            vo.setTrainer(t.getTrainer());
            vo.setLocation(t.getLocation());
            vo.setStartTime(t.getStartTime());
            vo.setEndTime(t.getEndTime());
            vo.setSignStartTime(effectiveSignStart(t));
            vo.setSignEndTime(effectiveSignEnd(t));
            vo.setPhase(resolvePhase(t));
            vo.setPhaseText(phaseText(vo.getPhase()));
            vo.setSigned(signedMap.containsKey(t.getId()));
            vo.setSignTime(signedMap.get(t.getId()));
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sign(AttendanceSignDTO dto, String ip) {
        Long userId = StpUtil.getLoginIdAsLong();
        TrOfflineTraining training = assertTrainingExists(dto.getTrainingId());

        // 1. 已取消 / 已手动结束的场次不可签到
        if (training.getStatus() != null && training.getStatus() == STATUS_CANCELLED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该培训场次已取消，无法签到");
        }
        if (training.getStatus() != null && training.getStatus() == STATUS_ENDED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该培训场次已结束，无法签到");
        }

        // 2. 签到窗口校验：未开始 / 已结束均拒绝
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime signStart = effectiveSignStart(training);
        LocalDateTime signEnd = effectiveSignEnd(training);
        if (now.isBefore(signStart)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "签到尚未开始，签到时间为 " + formatDateTime(signStart) + " 起");
        }
        if (now.isAfter(signEnd)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "签到已结束，签到截止时间为 " + formatDateTime(signEnd));
        }

        // 3. 签到码校验：忽略首尾空白，防止学员输入多余空格
        if (StrUtil.isBlank(training.getSignCode())
                || !training.getSignCode().equals(StrUtil.trimToEmpty(dto.getSignCode()))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "签到码错误，请向培训管理员确认");
        }

        // 4. 防重复签到
        Long exists = attendanceMapper.selectCount(new LambdaQueryWrapper<TrAttendanceRecord>()
                .eq(TrAttendanceRecord::getTrainingId, training.getId())
                .eq(TrAttendanceRecord::getUserId, userId));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "您已签到，请勿重复签到");
        }

        // 5. 写入签到记录
        TrAttendanceRecord record = new TrAttendanceRecord();
        record.setTrainingId(training.getId());
        record.setUserId(userId);
        record.setSignTime(now);
        record.setSignType(SIGN_TYPE_SELF);
        record.setSignIp(ip);
        attendanceMapper.insert(record);
        log.info("学员自主签到：场次ID={}, 学员ID={}, IP={}", training.getId(), userId, ip);
    }

    @Override
    public List<StudyOfflineRecordVO> myRecords() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<TrAttendanceRecord> records = attendanceMapper.selectList(new LambdaQueryWrapper<TrAttendanceRecord>()
                .eq(TrAttendanceRecord::getUserId, userId)
                .orderByDesc(TrAttendanceRecord::getSignTime));
        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询关联场次，避免 N+1 查询
        Set<Long> trainingIds = records.stream()
                .map(TrAttendanceRecord::getTrainingId)
                .collect(Collectors.toSet());
        Map<Long, TrOfflineTraining> trainingMap = baseMapper.selectBatchIds(trainingIds).stream()
                .collect(Collectors.toMap(TrOfflineTraining::getId, t -> t, (a, b) -> a));

        List<StudyOfflineRecordVO> result = new ArrayList<>();
        for (TrAttendanceRecord r : records) {
            TrOfflineTraining t = trainingMap.get(r.getTrainingId());
            if (t == null) {
                // 场次已被删除时跳过，保证列表不出现空指针
                continue;
            }
            StudyOfflineRecordVO vo = new StudyOfflineRecordVO();
            vo.setId(r.getId());
            vo.setTrainingId(r.getTrainingId());
            vo.setTrainingTitle(t.getTitle());
            vo.setTrainer(t.getTrainer());
            vo.setLocation(t.getLocation());
            vo.setStartTime(t.getStartTime());
            vo.setEndTime(t.getEndTime());
            vo.setSignTime(r.getSignTime());
            vo.setSignType(r.getSignType());
            vo.setSignTypeText(r.getSignType() != null && r.getSignType() == SIGN_TYPE_MANUAL ? "管理员补签" : "自主签到");
            result.add(vo);
        }
        return result;
    }

    // ==================================================================
    // 私有辅助方法
    // ==================================================================

    /**
     * 校验并断言场次存在
     *
     * @param trainingId 场次ID
     * @return 场次实体
     */
    private TrOfflineTraining assertTrainingExists(Long trainingId) {
        TrOfflineTraining training = baseMapper.selectById(trainingId);
        if (training == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "线下培训场次不存在");
        }
        return training;
    }

    /**
     * 校验时间区间：结束时间晚于开始时间；签到窗口合法
     *
     * @param dto 保存参数
     */
    private void validateTimeRange(OfflineTrainingSaveDTO dto) {
        if (dto.getStartTime().isAfter(dto.getEndTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "培训结束时间必须晚于开始时间");
        }
        LocalDateTime signStart = dto.getSignStartTime() != null
                ? dto.getSignStartTime()
                : dto.getStartTime().minusMinutes(DEFAULT_SIGN_AHEAD_MINUTES);
        LocalDateTime signEnd = dto.getSignEndTime() != null ? dto.getSignEndTime() : dto.getEndTime();
        if (signStart.isAfter(signEnd)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "签到开始时间必须早于签到结束时间");
        }
        // 签到窗口不应晚于培训结束时间，否则出现「培训已结束仍可签到」的荒谬记录
        if (signEnd.isAfter(dto.getEndTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "签到结束时间不能晚于培训结束时间");
        }
    }

    /**
     * DTO 转实体
     *
     * @param dto 保存参数
     * @return 场次实体
     */
    private TrOfflineTraining buildEntity(OfflineTrainingSaveDTO dto) {
        TrOfflineTraining entity = new TrOfflineTraining();
        entity.setPlanId(dto.getPlanId());
        entity.setCourseId(dto.getCourseId());
        entity.setTitle(dto.getTitle());
        entity.setTrainer(dto.getTrainer());
        entity.setLocation(dto.getLocation());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setSignStartTime(dto.getSignStartTime());
        entity.setSignEndTime(dto.getSignEndTime());
        entity.setRemark(dto.getRemark());
        return entity;
    }

    /**
     * 计算实际生效的签到开始时间
     *
     * @param training 场次实体
     * @return 签到开始时间
     */
    private LocalDateTime effectiveSignStart(TrOfflineTraining training) {
        return training.getSignStartTime() != null
                ? training.getSignStartTime()
                : training.getStartTime().minusMinutes(DEFAULT_SIGN_AHEAD_MINUTES);
    }

    /**
     * 计算实际生效的签到结束时间
     *
     * @param training 场次实体
     * @return 签到结束时间
     */
    private LocalDateTime effectiveSignEnd(TrOfflineTraining training) {
        return training.getSignEndTime() != null ? training.getSignEndTime() : training.getEndTime();
    }

    /**
     * 根据当前时间动态计算场次阶段
     *
     * @param training 场次实体
     * @return upcoming-未开始 signing-签到中 ended-已结束 cancelled-已取消
     */
    private String resolvePhase(TrOfflineTraining training) {
        // 管理端手动取消优先于时间判断
        if (training.getStatus() != null && training.getStatus() == STATUS_CANCELLED) {
            return "cancelled";
        }
        // 管理端手动结束同样优先
        if (training.getStatus() != null && training.getStatus() == STATUS_ENDED) {
            return "ended";
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(effectiveSignStart(training))) {
            return "upcoming";
        }
        if (now.isAfter(effectiveSignEnd(training))) {
            return "ended";
        }
        return "signing";
    }

    /**
     * 阶段编码转中文文案
     *
     * @param phase 阶段编码
     * @return 中文文案
     */
    private String phaseText(String phase) {
        switch (phase) {
            case "upcoming":
                return "未开始";
            case "signing":
                return "签到中";
            case "ended":
                return "已结束";
            case "cancelled":
                return "已取消";
            default:
                return "未知";
        }
    }

    /**
     * 生成全场唯一的 6 位数字签到码
     * <p>
     * 通过「随机生成 + 查重」循环保证唯一，重试上限 20 次，
     * 避免极端情况下无限循环；超出上限抛业务异常提示稍后重试。
     *
     * @return 6 位数字签到码
     */
    private String generateUniqueSignCode() {
        for (int i = 0; i < 20; i++) {
            String code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
            Long count = baseMapper.selectCount(new LambdaQueryWrapper<TrOfflineTraining>()
                    .eq(TrOfflineTraining::getSignCode, code));
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "签到码生成失败，请稍后重试");
    }

    /**
     * 场次实体列表转 VO 列表（批量回填计划名称、课程名称、签到人数）
     *
     * @param trainings 场次实体列表
     * @return VO 列表
     */
    private List<OfflineTrainingVO> toTrainingVOList(List<TrOfflineTraining> trainings) {
        if (trainings.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> trainingIds = trainings.stream().map(TrOfflineTraining::getId).collect(Collectors.toList());

        // 批量统计各场次签到人数（一次查询后在内存分组，避免逐条 count 的 N+1 问题）
        List<TrAttendanceRecord> allRecords = attendanceMapper.selectList(
                new LambdaQueryWrapper<TrAttendanceRecord>().in(TrAttendanceRecord::getTrainingId, trainingIds));
        Map<Long, Long> signCountMap = allRecords.stream()
                .collect(Collectors.groupingBy(TrAttendanceRecord::getTrainingId, Collectors.counting()));

        // 批量查询计划名称
        Set<Long> planIds = trainings.stream()
                .map(TrOfflineTraining::getPlanId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> planNameMap = planIds.isEmpty()
                ? new HashMap<>()
                : trTrainingPlanMapper.selectBatchIds(planIds).stream()
                .collect(Collectors.toMap(TrTrainingPlan::getId, TrTrainingPlan::getName, (a, b) -> a));

        // 批量查询课程名称
        Set<Long> courseIds = trainings.stream()
                .map(TrOfflineTraining::getCourseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> courseNameMap = courseIds.isEmpty()
                ? new HashMap<>()
                : trCourseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(TrCourse::getId, TrCourse::getName, (a, b) -> a));

        List<OfflineTrainingVO> result = new ArrayList<>();
        for (TrOfflineTraining t : trainings) {
            OfflineTrainingVO vo = new OfflineTrainingVO();
            vo.setId(t.getId());
            vo.setPlanId(t.getPlanId());
            vo.setPlanName(t.getPlanId() == null ? null : planNameMap.get(t.getPlanId()));
            vo.setCourseId(t.getCourseId());
            vo.setCourseName(t.getCourseId() == null ? null : courseNameMap.get(t.getCourseId()));
            vo.setTitle(t.getTitle());
            vo.setTrainer(t.getTrainer());
            vo.setLocation(t.getLocation());
            vo.setStartTime(t.getStartTime());
            vo.setEndTime(t.getEndTime());
            vo.setSignStartTime(effectiveSignStart(t));
            vo.setSignEndTime(effectiveSignEnd(t));
            vo.setSignCode(t.getSignCode());
            vo.setStatus(t.getStatus());
            String phase = resolvePhase(t);
            vo.setPhase(phase);
            vo.setPhaseText(phaseText(phase));
            vo.setSignCount(signCountMap.getOrDefault(t.getId(), 0L));
            vo.setRemark(t.getRemark());
            vo.setCreateTime(t.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    /**
     * 签到记录实体列表转 VO 列表（批量回填姓名、工号、部门名称）
     *
     * @param records 签到记录列表
     * @return VO 列表
     */
    private List<AttendanceRecordVO> toAttendanceVOList(List<TrAttendanceRecord> records) {
        if (records.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> userIds = records.stream().map(TrAttendanceRecord::getUserId).collect(Collectors.toSet());
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        Map<Long, SysUser> userMap = users.stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        // 批量查询部门名称
        Set<Long> deptIds = users.stream()
                .map(SysUser::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> deptNameMap = deptIds.isEmpty()
                ? new HashMap<>()
                : sysDepartmentMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDepartment::getId, SysDepartment::getName, (a, b) -> a));

        List<AttendanceRecordVO> result = new ArrayList<>();
        for (TrAttendanceRecord r : records) {
            AttendanceRecordVO vo = new AttendanceRecordVO();
            vo.setId(r.getId());
            vo.setTrainingId(r.getTrainingId());
            vo.setUserId(r.getUserId());
            SysUser user = userMap.get(r.getUserId());
            if (user != null) {
                vo.setUserName(user.getRealName());
                vo.setEmployeeNo(user.getUsername());
                vo.setDeptName(user.getDepartmentId() == null ? null : deptNameMap.get(user.getDepartmentId()));
            }
            vo.setSignTime(r.getSignTime());
            vo.setSignType(r.getSignType());
            vo.setSignTypeText(r.getSignType() != null && r.getSignType() == SIGN_TYPE_MANUAL ? "管理员补签" : "自主签到");
            vo.setSignIp(r.getSignIp());
            vo.setRemark(r.getRemark());
            result.add(vo);
        }
        return result;
    }

    /**
     * 格式化时间为 yyyy-MM-dd HH:mm:ss（用于异常提示文案）
     *
     * @param time 时间
     * @return 格式化文本
     */
    private String formatDateTime(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
