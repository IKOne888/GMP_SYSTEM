package com.gmp.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gmp.common.BusinessException;
import com.gmp.common.ErrorCode;
import com.gmp.dto.certificate.ExternalCertificateAuditDTO;
import com.gmp.dto.certificate.ExternalCertificateSaveDTO;
import com.gmp.dto.certificate.ExternalCertificateSubmitDTO;
import com.gmp.dto.certificate.ExternalCertificateVO;
import com.gmp.entity.SysDepartment;
import com.gmp.entity.SysUser;
import com.gmp.entity.TrExternalCertificate;
import com.gmp.mapper.SysDepartmentMapper;
import com.gmp.mapper.SysUserMapper;
import com.gmp.mapper.TrExternalCertificateMapper;
import com.gmp.service.SysMessageService;
import com.gmp.service.TrExternalCertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 外部专项培训与资质证书登记 Service 实现类
 * <p>
 * 负责证书台账的查询、登记、审核与有效期跟踪，并统一回填
 * 「持证人姓名/工号/所属部门」等展示字段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrExternalCertificateServiceImpl
        extends ServiceImpl<TrExternalCertificateMapper, TrExternalCertificate>
        implements TrExternalCertificateService {

    /** 用户 Mapper（回填持证人姓名、工号、部门，以及关键字按姓名检索） */
    private final SysUserMapper sysUserMapper;

    /** 部门 Mapper（回填所属部门名称） */
    private final SysDepartmentMapper sysDepartmentMapper;

    /** 站内信 Service（审核结果通知学员） */
    private final SysMessageService sysMessageService;

    /** 导出最大行数，避免一次性拉取全表拖垮内存 */
    private static final int EXPORT_LIMIT = 5000;

    @Override
    public Page<ExternalCertificateVO> pageCertificates(Integer pageNum, Integer pageSize, String keyword,
                                                       Integer certType, Integer status, String expireStatus) {
        Page<TrExternalCertificate> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TrExternalCertificate> wrapper = buildQueryWrapper(keyword, certType, status, expireStatus);
        // 待审核的排在最前，便于管理员优先处理；其余按登记时间倒序
        wrapper.orderByDesc(TrExternalCertificate::getCreateTime);

        Page<TrExternalCertificate> entityPage = baseMapper.selectPage(page, wrapper);
        List<ExternalCertificateVO> voList = toVOList(entityPage.getRecords());

        Page<ExternalCertificateVO> voPage =
                new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public ExternalCertificateVO getCertificateDetail(Long id) {
        TrExternalCertificate entity = requireExisting(id);
        return toVOList(Collections.singletonList(entity)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addCertificate(ExternalCertificateSaveDTO dto) {
        // 持证人必须存在，避免登记到无效用户
        checkUserExists(dto.getUserId());
        TrExternalCertificate entity = buildEntity(dto);
        // 管理员代录视为已完成审核，直接生效
        entity.setStatus(STATUS_APPROVED);
        entity.setAuditBy(currentUsername());
        entity.setAuditTime(java.time.LocalDateTime.now());
        entity.setAuditRemark("管理端登记，默认生效");
        baseMapper.insert(entity);
        log.info("管理端登记资质证书：记录ID={}, 持证人={}, 名称={}",
                entity.getId(), dto.getUserId(), dto.getCertName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCertificate(ExternalCertificateSaveDTO dto) {
        TrExternalCertificate exist = requireExisting(dto.getId());
        checkUserExists(dto.getUserId());

        TrExternalCertificate update = buildEntity(dto);
        update.setId(exist.getId());
        // 审核状态与审核信息不随编辑变化，保持原有结论
        baseMapper.updateById(update);
        log.info("管理端编辑资质证书：记录ID={}, 名称={}", exist.getId(), dto.getCertName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditCertificate(Long id, ExternalCertificateAuditDTO dto) {
        TrExternalCertificate exist = requireExisting(id);
        Integer target = dto.getStatus();
        if (target == null || (target != STATUS_APPROVED && target != STATUS_REJECTED)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "审核结论不合法");
        }
        // 驳回必须说明原因，便于学员补充材料后重新提交
        if (target == STATUS_REJECTED && StrUtil.isBlank(dto.getAuditRemark())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "驳回时必须填写审核意见");
        }

        TrExternalCertificate update = new TrExternalCertificate();
        update.setId(id);
        update.setStatus(target);
        update.setAuditBy(currentUsername());
        update.setAuditTime(java.time.LocalDateTime.now());
        update.setAuditRemark(dto.getAuditRemark());
        baseMapper.updateById(update);

        // 审核结果回发站内信，形成闭环
        String conclusion = target == STATUS_APPROVED ? "已通过审核" : "被驳回";
        String content = "您登记的《" + exist.getCertName() + "》" + conclusion
                + (StrUtil.isNotBlank(dto.getAuditRemark()) ? "：" + dto.getAuditRemark() : "。");
        sysMessageService.send(exist.getUserId(), SysMessageService.TYPE_SYSTEM, "资质证书审核结果", content, id);

        log.info("资质证书审核：记录ID={}, 结论={}, 审核人={}", id, target, update.getAuditBy());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCertificate(Long id) {
        requireExisting(id);
        // 逻辑删除，数据物理保留以满足 GMP 记录留存与追溯要求
        baseMapper.deleteById(id);
    }

    @Override
    public List<ExternalCertificateVO> listForExport(String keyword, Integer certType, Integer status,
                                                     String expireStatus) {
        LambdaQueryWrapper<TrExternalCertificate> wrapper = buildQueryWrapper(keyword, certType, status, expireStatus);
        wrapper.orderByDesc(TrExternalCertificate::getCreateTime);
        wrapper.last("LIMIT " + EXPORT_LIMIT);
        return toVOList(baseMapper.selectList(wrapper));
    }

    // ==================== 学员端（用户端） ====================

    @Override
    public List<ExternalCertificateVO> myCertificates(Integer certType) {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<TrExternalCertificate> wrapper = new LambdaQueryWrapper<>();
        // 强制只查本人数据，避免越权
        wrapper.eq(TrExternalCertificate::getUserId, userId);
        if (certType != null) {
            wrapper.eq(TrExternalCertificate::getCertType, certType);
        }
        wrapper.orderByDesc(TrExternalCertificate::getCreateTime);
        return toVOList(baseMapper.selectList(wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitCertificate(ExternalCertificateSubmitDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        TrExternalCertificate entity = new TrExternalCertificate();
        entity.setUserId(userId);
        entity.setCertType(dto.getCertType());
        entity.setCertName(dto.getCertName());
        entity.setCertNo(dto.getCertNo());
        entity.setIssuer(dto.getIssuer());
        entity.setIssueDate(dto.getIssueDate());
        entity.setExpireDate(dto.getExpireDate());
        entity.setFileUrl(dto.getFileUrl());
        entity.setRemark(dto.getRemark());
        // 学员提交一律待审核，忽略任何客户端状态传值
        entity.setStatus(STATUS_PENDING);
        baseMapper.insert(entity);
        log.info("学员提交资质证书登记：记录ID={}, 用户={}, 名称={}",
                entity.getId(), userId, dto.getCertName());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMyCertificate(Long id, ExternalCertificateSubmitDTO dto) {
        TrExternalCertificate exist = requireOwnCertificate(id);
        // 已生效的证书不允许学员自行修改，须联系管理员处理
        if (exist.getStatus() != null && exist.getStatus() == STATUS_APPROVED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "证书已生效，如需修改请联系培训管理员");
        }

        TrExternalCertificate update = new TrExternalCertificate();
        update.setCertType(dto.getCertType());
        update.setCertName(dto.getCertName());
        update.setCertNo(dto.getCertNo());
        update.setIssuer(dto.getIssuer());
        update.setIssueDate(dto.getIssueDate());
        update.setExpireDate(dto.getExpireDate());
        update.setFileUrl(dto.getFileUrl());
        update.setRemark(dto.getRemark());
        // 修改后重新进入待审核，并清空上一次的审核结论
        baseMapper.update(update, new LambdaUpdateWrapper<TrExternalCertificate>()
                .eq(TrExternalCertificate::getId, id)
                .set(TrExternalCertificate::getStatus, STATUS_PENDING)
                .set(TrExternalCertificate::getAuditBy, null)
                .set(TrExternalCertificate::getAuditTime, null)
                .set(TrExternalCertificate::getAuditRemark, null));
        log.info("学员修改资质证书登记：记录ID={}, 用户={}", id, exist.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMyCertificate(Long id) {
        TrExternalCertificate exist = requireOwnCertificate(id);
        if (exist.getStatus() != null && exist.getStatus() == STATUS_APPROVED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "证书已生效，不允许删除");
        }
        baseMapper.deleteById(id);
    }

    // ==================== 内部方法 ====================

    /**
     * 构建查询条件（分页与导出共用）
     */
    private LambdaQueryWrapper<TrExternalCertificate> buildQueryWrapper(String keyword, Integer certType,
                                                                       Integer status, String expireStatus) {
        LambdaQueryWrapper<TrExternalCertificate> wrapper = new LambdaQueryWrapper<>();
        // 关键字：证书名称 / 编号 / 发证机构 / 持证人姓名或工号
        if (StrUtil.isNotBlank(keyword)) {
            String kw = keyword.trim();
            // 先按姓名 / 工号模糊匹配用户，得到用户ID集合（数据量级为内部员工，可接受）
            List<SysUser> matchedUsers = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .like(SysUser::getRealName, kw)
                    .or()
                    .like(SysUser::getUsername, kw));
            List<Long> matchedUserIds = matchedUsers.stream()
                    .map(SysUser::getId)
                    .collect(Collectors.toList());
            wrapper.and(w -> {
                w.like(TrExternalCertificate::getCertName, kw)
                        .or().like(TrExternalCertificate::getCertNo, kw)
                        .or().like(TrExternalCertificate::getIssuer, kw);
                if (!matchedUserIds.isEmpty()) {
                    w.or().in(TrExternalCertificate::getUserId, matchedUserIds);
                }
            });
        }
        if (certType != null) {
            wrapper.eq(TrExternalCertificate::getCertType, certType);
        }
        if (status != null) {
            wrapper.eq(TrExternalCertificate::getStatus, status);
        }
        // 有效期状态按日期区间过滤，不落库
        if (StrUtil.isNotBlank(expireStatus)) {
            LocalDate today = LocalDate.now();
            switch (expireStatus) {
                case "long_term":
                    wrapper.isNull(TrExternalCertificate::getExpireDate);
                    break;
                case "expired":
                    wrapper.isNotNull(TrExternalCertificate::getExpireDate)
                            .lt(TrExternalCertificate::getExpireDate, today);
                    break;
                case "expiring":
                    wrapper.isNotNull(TrExternalCertificate::getExpireDate)
                            .ge(TrExternalCertificate::getExpireDate, today)
                            .le(TrExternalCertificate::getExpireDate, today.plusDays(EXPIRING_DAYS));
                    break;
                case "valid":
                    wrapper.isNotNull(TrExternalCertificate::getExpireDate)
                            .gt(TrExternalCertificate::getExpireDate, today.plusDays(EXPIRING_DAYS));
                    break;
                default:
                    // 无法识别的取值按不筛选处理
                    break;
            }
        }
        return wrapper;
    }

    /**
     * 实体列表转 VO 列表，并批量回填持证人姓名、工号与部门名称
     */
    private List<ExternalCertificateVO> toVOList(List<TrExternalCertificate> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }
        // 批量查用户，避免 N+1
        Set<Long> userIds = entities.stream()
                .map(TrExternalCertificate::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SysUser> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
        }
        // 批量查部门
        Set<Long> deptIds = userMap.values().stream()
                .map(SysUser::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> deptNameMap = new HashMap<>();
        if (!deptIds.isEmpty()) {
            List<SysDepartment> depts = sysDepartmentMapper.selectBatchIds(deptIds);
            deptNameMap = depts.stream()
                    .collect(Collectors.toMap(SysDepartment::getId, SysDepartment::getName, (a, b) -> a));
        }

        List<ExternalCertificateVO> result = new ArrayList<>(entities.size());
        for (TrExternalCertificate entity : entities) {
            ExternalCertificateVO vo = toVO(entity);
            SysUser user = userMap.get(entity.getUserId());
            if (user != null) {
                vo.setUserName(user.getRealName());
                vo.setEmployeeNo(user.getUsername());
                vo.setDeptName(deptNameMap.get(user.getDepartmentId()));
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 实体转展示 VO（含类型/状态/有效期状态文案）
     */
    private ExternalCertificateVO toVO(TrExternalCertificate entity) {
        ExternalCertificateVO vo = new ExternalCertificateVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setCertType(entity.getCertType());
        vo.setCertTypeText(certTypeToText(entity.getCertType()));
        vo.setCertName(entity.getCertName());
        vo.setCertNo(entity.getCertNo());
        vo.setIssuer(entity.getIssuer());
        vo.setIssueDate(entity.getIssueDate());
        vo.setExpireDate(entity.getExpireDate());
        vo.setFileUrl(entity.getFileUrl());
        vo.setStatus(entity.getStatus());
        vo.setStatusText(statusToText(entity.getStatus()));
        vo.setAuditBy(entity.getAuditBy());
        vo.setAuditTime(entity.getAuditTime());
        vo.setAuditRemark(entity.getAuditRemark());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        // 有效期状态按当前日期动态计算
        fillExpireStatus(vo, entity.getExpireDate());
        return vo;
    }

    /**
     * 计算并回填有效期状态与剩余天数
     */
    private void fillExpireStatus(ExternalCertificateVO vo, LocalDate expireDate) {
        if (expireDate == null) {
            vo.setExpireStatus("long_term");
            vo.setExpireStatusText("长期有效");
            vo.setRemainDays(null);
            return;
        }
        long remain = ChronoUnit.DAYS.between(LocalDate.now(), expireDate);
        vo.setRemainDays(remain);
        if (remain < 0) {
            vo.setExpireStatus("expired");
            vo.setExpireStatusText("已过期");
        } else if (remain <= EXPIRING_DAYS) {
            vo.setExpireStatus("expiring");
            vo.setExpireStatusText("临期");
        } else {
            vo.setExpireStatus("valid");
            vo.setExpireStatusText("有效");
        }
    }

    /**
     * 管理端保存参数转实体
     */
    private TrExternalCertificate buildEntity(ExternalCertificateSaveDTO dto) {
        TrExternalCertificate entity = new TrExternalCertificate();
        entity.setUserId(dto.getUserId());
        entity.setCertType(dto.getCertType());
        entity.setCertName(dto.getCertName());
        entity.setCertNo(dto.getCertNo());
        entity.setIssuer(dto.getIssuer());
        entity.setIssueDate(dto.getIssueDate());
        entity.setExpireDate(dto.getExpireDate());
        entity.setFileUrl(dto.getFileUrl());
        entity.setRemark(dto.getRemark());
        return entity;
    }

    /**
     * 查询记录，不存在则抛 404
     */
    private TrExternalCertificate requireExisting(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "记录ID不能为空");
        }
        TrExternalCertificate entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资质证书记录不存在");
        }
        return entity;
    }

    /**
     * 查询本人记录（含越权校验）
     */
    private TrExternalCertificate requireOwnCertificate(Long id) {
        TrExternalCertificate entity = requireExisting(id);
        Long userId = StpUtil.getLoginIdAsLong();
        if (!Objects.equals(entity.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作他人的资质证书");
        }
        return entity;
    }

    /**
     * 校验用户存在
     */
    private void checkUserExists(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择持证人");
        }
        if (sysUserMapper.selectById(userId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "持证人不存在");
        }
    }

    /**
     * 取当前登录账号（用于审核人留痕）
     */
    private String currentUsername() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) {
                return null;
            }
            SysUser user = sysUserMapper.selectById(Long.valueOf(String.valueOf(loginId)));
            return user == null ? String.valueOf(loginId) : user.getUsername();
        } catch (Exception e) {
            // 审核人留痕失败不影响主流程
            log.warn("获取当前登录账号失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 类型数字转中文
     */
    private String certTypeToText(Integer certType) {
        if (certType == null) {
            return "未知";
        }
        return certType == TYPE_TRAINING ? "外部培训" : "资质证书";
    }

    /**
     * 审核状态数字转中文
     */
    private String statusToText(Integer status) {
        if (status == null) {
            return "待审核";
        }
        switch (status) {
            case STATUS_APPROVED:
                return "已生效";
            case STATUS_REJECTED:
                return "已驳回";
            default:
                return "待审核";
        }
    }
}
