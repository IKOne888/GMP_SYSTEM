package com.gmp.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gmp.common.Result;
import com.gmp.dto.offline.AttendanceManualSignDTO;
import com.gmp.dto.offline.AttendanceRecordVO;
import com.gmp.dto.offline.OfflineTrainingSaveDTO;
import com.gmp.dto.offline.OfflineTrainingVO;
import com.gmp.service.TrOfflineTrainingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 线下培训登记与电子签到 Controller（管理端）
 * <p>
 * 路径前缀 /training/offline，与前端 views/training/offline/index.vue 的接口契约保持一致。
 * 提供场次登记、编辑、结束/取消、签到码管理、签到明细查询与导出、异常补签等能力。
 * 权限：仅 SUPER_ADMIN / QA_MANAGER / TRAINING_ADMIN（见 SaTokenConfig）。
 */
@Api(tags = "线下培训与电子签到")
@RestController
@RequestMapping("/training/offline")
@RequiredArgsConstructor
@Validated
public class TrOfflineTrainingController {

    /** 分页大小上限，防止恶意大查询拖垮数据库 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 线下培训 Service */
    private final TrOfflineTrainingService offlineTrainingService;

    /**
     * 分页查询线下培训场次
     */
    @ApiOperation("分页查询线下培训场次")
    @GetMapping
    public Result<Page<OfflineTrainingVO>> page(
            @RequestParam(value = "page", defaultValue = "1") @Min(value = 1, message = "页码最小为1") Integer page,
            @RequestParam(value = "size", defaultValue = "10")
            @Min(value = 1, message = "每页条数最小为1")
            @Max(value = MAX_PAGE_SIZE, message = "每页条数最大为100") Integer size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String phase,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(offlineTrainingService.pageTrainings(page, size, title, phase, startDate, endDate));
    }

    /**
     * 新增线下培训场次
     */
    @ApiOperation("新增线下培训场次")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody OfflineTrainingSaveDTO dto) {
        offlineTrainingService.addTraining(dto);
        return Result.success();
    }

    /**
     * 编辑线下培训场次
     */
    @ApiOperation("编辑线下培训场次")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @NotNull(message = "场次ID不能为空") Long id,
                               @Valid @RequestBody OfflineTrainingSaveDTO dto) {
        // 以路径上的ID为准，防止请求体篡改
        dto.setId(id);
        offlineTrainingService.updateTraining(dto);
        return Result.success();
    }

    /**
     * 删除线下培训场次（已产生签到记录的场次不允许删除）
     */
    @ApiOperation("删除线下培训场次")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @NotNull(message = "场次ID不能为空") Long id) {
        offlineTrainingService.deleteTraining(id);
        return Result.success();
    }

    /**
     * 手动结束场次（结束签到窗口）
     */
    @ApiOperation("结束线下培训场次")
    @PutMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable @NotNull(message = "场次ID不能为空") Long id) {
        offlineTrainingService.finishTraining(id);
        return Result.success();
    }

    /**
     * 取消场次
     */
    @ApiOperation("取消线下培训场次")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable @NotNull(message = "场次ID不能为空") Long id) {
        offlineTrainingService.cancelTraining(id);
        return Result.success();
    }

    /**
     * 重新生成签到码（原签到码立即失效）
     */
    @ApiOperation("重新生成签到码")
    @PutMapping("/{id}/refresh-code")
    public Result<String> refreshCode(@PathVariable @NotNull(message = "场次ID不能为空") Long id) {
        return Result.success(offlineTrainingService.refreshSignCode(id));
    }

    /**
     * 分页查询某场次的签到明细
     */
    @ApiOperation("分页查询签到明细")
    @GetMapping("/{id}/attendance")
    public Result<Page<AttendanceRecordVO>> attendance(
            @PathVariable @NotNull(message = "场次ID不能为空") Long id,
            @RequestParam(value = "page", defaultValue = "1") @Min(value = 1, message = "页码最小为1") Integer page,
            @RequestParam(value = "size", defaultValue = "10")
            @Min(value = 1, message = "每页条数最小为1")
            @Max(value = MAX_PAGE_SIZE, message = "每页条数最大为100") Integer size,
            @RequestParam(required = false) String keyword) {
        return Result.success(offlineTrainingService.pageAttendance(id, page, size, keyword));
    }

    /**
     * 管理员补签
     */
    @ApiOperation("管理员补签")
    @PostMapping("/{id}/attendance/manual")
    public Result<Void> manualSign(@PathVariable @NotNull(message = "场次ID不能为空") Long id,
                                   @Valid @RequestBody AttendanceManualSignDTO dto) {
        offlineTrainingService.manualSign(id, dto);
        return Result.success();
    }

    /**
     * 导出某场次的签到明细为 Excel
     */
    @ApiOperation("导出签到明细")
    @GetMapping("/{id}/attendance/export")
    public void exportAttendance(@PathVariable @NotNull(message = "场次ID不能为空") Long id,
                                 HttpServletResponse response) throws IOException {
        List<AttendanceRecordVO> data = offlineTrainingService.listAttendanceForExport(id);
        // 设置 xlsx 下载响应头（文件名 URL 编码，防止中文乱码）
        String fileName = URLEncoder.encode("线下培训签到明细", StandardCharsets.UTF_8.name()).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment; filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), AttendanceRecordVO.class)
                .sheet("签到明细")
                .doWrite(data);
    }
}
