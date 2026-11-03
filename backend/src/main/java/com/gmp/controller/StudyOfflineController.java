package com.gmp.controller;

import com.gmp.common.Result;
import com.gmp.dto.offline.AttendanceSignDTO;
import com.gmp.dto.offline.StudyOfflineRecordVO;
import com.gmp.dto.offline.StudyOfflineTrainingVO;
import com.gmp.service.TrOfflineTrainingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

/**
 * 线下培训签到 Controller（学员端）
 * <p>
 * 路径前缀 /study/offline，沿用学员端统一接口契约（/study/** 仅校验登录状态）。
 * 数据隔离：签到与记录查询均以 StpUtil.getLoginIdAsLong() 作为学员身份，
 * 不接受前端传入 userId，防止越权查看/伪造他人签到。
 */
@Api(tags = "学员端-线下培训签到")
@RestController
@RequestMapping("/study/offline")
@RequiredArgsConstructor
@Validated
public class StudyOfflineController {

    /** 线下培训 Service */
    private final TrOfflineTrainingService offlineTrainingService;

    /**
     * 查询本人可参与签到的线下培训场次
     */
    @ApiOperation("查询可签到场次")
    @GetMapping("/signable")
    public Result<List<StudyOfflineTrainingVO>> signable() {
        return Result.success(offlineTrainingService.listSignable());
    }

    /**
     * 学员自主签到
     */
    @ApiOperation("学员签到")
    @PostMapping("/sign")
    public Result<Void> sign(@Valid @RequestBody AttendanceSignDTO dto, HttpServletRequest request) {
        offlineTrainingService.sign(dto, resolveClientIp(request));
        return Result.success();
    }

    /**
     * 查询本人签到历史
     */
    @ApiOperation("我的签到记录")
    @GetMapping("/records")
    public Result<List<StudyOfflineRecordVO>> records() {
        return Result.success(offlineTrainingService.myRecords());
    }

    /**
     * 解析客户端真实IP
     * <p>
     * 依次尝试代理转发头 X-Forwarded-For、X-Real-IP，最后回退到 RemoteAddr，
     * 取第一个非空值，适配 nginx 等反向代理部署场景。
     *
     * @param request HTTP 请求
     * @return 客户端IP
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            // X-Forwarded-For 可能是「客户端IP, 代理1IP, 代理2IP」，取第一个
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
