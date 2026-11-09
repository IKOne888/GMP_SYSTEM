package com.gmp.controller;

import com.gmp.common.Result;
import com.gmp.dto.certificate.ExternalCertificateSubmitDTO;
import com.gmp.dto.certificate.ExternalCertificateVO;
import com.gmp.service.TrExternalCertificateService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 外部专项培训与资质证书 Controller（学员端）
 * <p>
 * 路径前缀 /study/certificates，沿用学员端统一接口契约（/study/** 仅校验登录状态）。
 * 数据隔离：所有操作均以 StpUtil.getLoginIdAsLong() 作为学员身份，
 * 不接受前端传入 userId，防止越权查看或伪造他人证书。
 */
@Api(tags = "学员端-我的资质证书")
@RestController
@RequestMapping("/study/certificates")
@RequiredArgsConstructor
@Validated
public class StudyCertificateController {

    /** 资质证书 Service */
    private final TrExternalCertificateService certificateService;

    /**
     * 查询本人登记的证书（可按类型过滤）
     */
    @ApiOperation("我的证书登记")
    @GetMapping
    public Result<List<ExternalCertificateVO>> myList(@RequestParam(required = false) Integer certType) {
        return Result.success(certificateService.myCertificates(certType));
    }

    /**
     * 提交证书登记（提交后进入待审核）
     */
    @ApiOperation("提交证书登记")
    @PostMapping
    public Result<Long> submit(@Valid @RequestBody ExternalCertificateSubmitDTO dto) {
        return Result.success(certificateService.submitCertificate(dto));
    }

    /**
     * 修改本人证书登记（仅待审核 / 已驳回可改）
     */
    @ApiOperation("修改证书登记")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody ExternalCertificateSubmitDTO dto) {
        certificateService.updateMyCertificate(id, dto);
        return Result.success();
    }

    /**
     * 删除本人证书登记（仅待审核 / 已驳回可删）
     */
    @ApiOperation("删除证书登记")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        certificateService.deleteMyCertificate(id);
        return Result.success();
    }
}
