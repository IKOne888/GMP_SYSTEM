package com.gmp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gmp.dto.certificate.ExternalCertificateAuditDTO;
import com.gmp.dto.certificate.ExternalCertificateSaveDTO;
import com.gmp.dto.certificate.ExternalCertificateSubmitDTO;
import com.gmp.dto.certificate.ExternalCertificateVO;

import java.util.List;

/**
 * 外部专项培训与资质证书登记 Service 接口
 * <p>
 * 覆盖两类使用场景：
 * 1. 管理端：全员证书台账的分页查询、代录、编辑、审核、删除与 Excel 导出；
 * 2. 学员端：本人证书的提交、修改、删除与查询（提交后进入待审核状态）。
 * <p>
 * 有效期状态（long_term / valid / expiring / expired）由有效期至动态计算，不落库，
 * 避免因时间推移导致库内状态与实际不一致。
 */
public interface TrExternalCertificateService {

    /** 审核状态：待审核 */
    int STATUS_PENDING = 0;
    /** 审核状态：已生效 */
    int STATUS_APPROVED = 1;
    /** 审核状态：已驳回 */
    int STATUS_REJECTED = 2;

    /** 类型：外部培训 */
    int TYPE_TRAINING = 1;
    /** 类型：资质证书 */
    int TYPE_CERTIFICATE = 2;

    /** 临期判定阈值（天）：有效期剩余天数小于等于该值视为临期 */
    int EXPIRING_DAYS = 30;

    /**
     * 分页查询证书台账（管理端）
     *
     * @param pageNum      页码
     * @param pageSize     每页条数
     * @param keyword      证书名称 / 编号 / 发证机构 / 持证人姓名或工号 模糊关键字
     * @param certType     类型（1-外部培训 2-资质证书），可为空
     * @param status       审核状态（0-待审核 1-已生效 2-已驳回），可为空
     * @param expireStatus 有效期状态（long_term / valid / expiring / expired），可为空
     * @return 分页结果
     */
    Page<ExternalCertificateVO> pageCertificates(Integer pageNum, Integer pageSize, String keyword,
                                                 Integer certType, Integer status, String expireStatus);

    /**
     * 查询证书详情（管理端编辑回显）
     *
     * @param id 记录ID
     * @return 证书展示对象
     */
    ExternalCertificateVO getCertificateDetail(Long id);

    /**
     * 管理端新增登记（由管理员代录，默认直接生效）
     *
     * @param dto 保存参数
     */
    void addCertificate(ExternalCertificateSaveDTO dto);

    /**
     * 管理端编辑登记
     *
     * @param dto 保存参数（id 必填）
     */
    void updateCertificate(ExternalCertificateSaveDTO dto);

    /**
     * 审核证书（通过 / 驳回）
     *
     * @param id  记录ID
     * @param dto 审核参数
     */
    void auditCertificate(Long id, ExternalCertificateAuditDTO dto);

    /**
     * 删除证书登记（逻辑删除，数据物理保留以满足追溯要求）
     *
     * @param id 记录ID
     */
    void deleteCertificate(Long id);

    /**
     * 导出台账数据（不分页，导出上限由 Service 内部限制）
     *
     * @param keyword      关键字
     * @param certType     类型
     * @param status       审核状态
     * @param expireStatus 有效期状态
     * @return 证书展示对象列表
     */
    List<ExternalCertificateVO> listForExport(String keyword, Integer certType, Integer status, String expireStatus);

    // ==================== 学员端（用户端） ====================

    /**
     * 查询本人提交的证书登记列表
     *
     * @param certType 类型，可为空
     * @return 证书展示对象列表（按创建时间倒序）
     */
    List<ExternalCertificateVO> myCertificates(Integer certType);

    /**
     * 学员提交证书登记（状态固定为待审核）
     *
     * @param dto 提交参数
     * @return 新记录ID
     */
    Long submitCertificate(ExternalCertificateSubmitDTO dto);

    /**
     * 学员修改本人证书登记（仅待审核 / 已驳回状态可改，修改后回到待审核）
     *
     * @param id  记录ID
     * @param dto 提交参数
     */
    void updateMyCertificate(Long id, ExternalCertificateSubmitDTO dto);

    /**
     * 学员删除本人证书登记（仅待审核 / 已驳回状态可删）
     *
     * @param id 记录ID
     */
    void deleteMyCertificate(Long id);
}
