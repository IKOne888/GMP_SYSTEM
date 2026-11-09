package com.gmp.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gmp.common.Result;
import com.gmp.dto.certificate.ExternalCertificateAuditDTO;
import com.gmp.dto.certificate.ExternalCertificateSaveDTO;
import com.gmp.dto.certificate.ExternalCertificateVO;
import com.gmp.service.TrExternalCertificateService;
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
 * 外部专项培训与资质证书登记 Controller（管理端）
 * <p>
 * 路径前缀 /training/certificates，与前端 views/training/certificate/index.vue 的接口契约保持一致。
 * 权限：由 SaTokenConfig 限制为 SUPER_ADMIN / QA_MANAGER / TRAINING_ADMIN。
 */
@Api(tags = "资质证书管理")
@RestController
@RequestMapping("/training/certificates")
@RequiredArgsConstructor
@Validated
public class TrExternalCertificateController {

    /** 分页大小上限，防止恶意大查询拖垮数据库（与系统全局约定一致） */
    private static final int MAX_PAGE_SIZE = 100;

    /** 资质证书 Service */
    private final TrExternalCertificateService certificateService;

    /**
     * 分页查询证书台账
     */
    @ApiOperation("分页查询资质证书")
    @GetMapping
    public Result<Page<ExternalCertificateVO>> page(
            @RequestParam(value = "page", defaultValue = "1") @Min(value = 1, message = "页码最小为1") Integer page,
            @RequestParam(value = "size", defaultValue = "10")
            @Min(value = 1, message = "每页条数最小为1")
            @Max(value = MAX_PAGE_SIZE, message = "每页条数最大为100") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer certType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String expireStatus) {
        return Result.success(certificateService.pageCertificates(page, size, keyword, certType, status, expireStatus));
    }

    /**
     * 查询证书详情（编辑回显用）
     */
    @ApiOperation("查询资质证书详情")
    @GetMapping("/{id}")
    public Result<ExternalCertificateVO> detail(@PathVariable @NotNull(message = "记录ID不能为空") Long id) {
        return Result.success(certificateService.getCertificateDetail(id));
    }

    /**
     * 新增登记（管理员代录，默认生效）
     */
    @ApiOperation("登记资质证书")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody ExternalCertificateSaveDTO dto) {
        certificateService.addCertificate(dto);
        return Result.success();
    }

    /**
     * 编辑登记
     */
    @ApiOperation("编辑资质证书")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @NotNull(message = "记录ID不能为空") Long id,
                               @Valid @RequestBody ExternalCertificateSaveDTO dto) {
        // 以路径上的ID为准，防止请求体篡改
        dto.setId(id);
        certificateService.updateCertificate(dto);
        return Result.success();
    }

    /**
     * 审核证书：通过 / 驳回
     */
    @ApiOperation("审核资质证书")
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable @NotNull(message = "记录ID不能为空") Long id,
                              @Valid @RequestBody ExternalCertificateAuditDTO dto) {
        certificateService.auditCertificate(id, dto);
        return Result.success();
    }

    /**
     * 删除登记（逻辑删除）
     */
    @ApiOperation("删除资质证书")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @NotNull(message = "记录ID不能为空") Long id) {
        certificateService.deleteCertificate(id);
        return Result.success();
    }

    /**
     * 导出证书台账为 Excel
     */
    @ApiOperation("导出资质证书台账")
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Integer certType,
                       @RequestParam(required = false) Integer status,
                       @RequestParam(required = false) String expireStatus,
                       HttpServletResponse response) throws IOException {
        List<ExternalCertificateVO> data =
                certificateService.listForExport(keyword, certType, status, expireStatus);
        // 设置 xlsx 下载响应头（文件名 URL 编码，防止中文乱码）
        String fileName = URLEncoder.encode("资质证书台账", StandardCharsets.UTF_8.name()).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment; filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), ExternalCertificateVO.class)
                .sheet("资质证书台账")
                .doWrite(data);
    }
}
