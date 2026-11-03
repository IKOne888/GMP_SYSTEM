package com.gmp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gmp.dto.offline.AttendanceManualSignDTO;
import com.gmp.dto.offline.AttendanceRecordVO;
import com.gmp.dto.offline.AttendanceSignDTO;
import com.gmp.dto.offline.OfflineTrainingSaveDTO;
import com.gmp.dto.offline.OfflineTrainingVO;
import com.gmp.dto.offline.StudyOfflineRecordVO;
import com.gmp.dto.offline.StudyOfflineTrainingVO;
import com.gmp.entity.TrOfflineTraining;

import java.util.List;

/**
 * 线下培训登记与电子签到 Service 接口
 * <p>
 * 管理端负责线下培训场次的登记、签到码发放、签到记录查询与导出、异常补签；
 * 学员端负责查看可签到场次、凭签到码自主签到、查询本人签到历史。
 */
public interface TrOfflineTrainingService extends IService<TrOfflineTraining> {

    /**
     * 管理端分页查询线下培训场次
     *
     * @param pageNum   页码
     * @param pageSize  每页条数
     * @param title     培训主题（模糊匹配，可为空）
     * @param phase     场次阶段过滤（upcoming/signing/ended/cancelled，可为空）
     * @param startDate 培训开始日期下界（yyyy-MM-dd，可为空）
     * @param endDate   培训开始日期上界（yyyy-MM-dd，可为空）
     * @return 分页结果
     */
    Page<OfflineTrainingVO> pageTrainings(Integer pageNum, Integer pageSize, String title,
                                          String phase, String startDate, String endDate);

    /**
     * 新增线下培训场次（自动生成签到码）
     *
     * @param dto 保存参数
     */
    void addTraining(OfflineTrainingSaveDTO dto);

    /**
     * 编辑线下培训场次（仅未结束且未取消的场次允许编辑）
     *
     * @param dto 保存参数
     */
    void updateTraining(OfflineTrainingSaveDTO dto);

    /**
     * 删除线下培训场次（逻辑删除，已产生签到记录的场次禁止删除）
     *
     * @param id 场次ID
     */
    void deleteTraining(Long id);

    /**
     * 手动结束场次（结束签到窗口）
     *
     * @param id 场次ID
     */
    void finishTraining(Long id);

    /**
     * 取消场次
     *
     * @param id 场次ID
     */
    void cancelTraining(Long id);

    /**
     * 重新生成签到码（原签到码立即失效）
     *
     * @param id 场次ID
     * @return 新的签到码
     */
    String refreshSignCode(Long id);

    /**
     * 分页查询某场次的签到明细
     *
     * @param trainingId 场次ID
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @param keyword    姓名 / 工号 / 部门关键字（可为空）
     * @return 签到明细分页
     */
    Page<AttendanceRecordVO> pageAttendance(Long trainingId, Integer pageNum, Integer pageSize, String keyword);

    /**
     * 查询某场次的全部签到明细（用于 Excel 导出）
     *
     * @param trainingId 场次ID
     * @return 签到明细列表
     */
    List<AttendanceRecordVO> listAttendanceForExport(Long trainingId);

    /**
     * 管理员补签
     *
     * @param trainingId 场次ID
     * @param dto        补签参数
     */
    void manualSign(Long trainingId, AttendanceManualSignDTO dto);

    /**
     * 学员端查询本人可参与签到的线下培训场次
     * <p>
     * 返回未取消、未结束的场次，并标记当前学员是否已签到。
     *
     * @return 场次列表
     */
    List<StudyOfflineTrainingVO> listSignable();

    /**
     * 学员自主签到
     *
     * @param dto 签到参数（场次ID + 签到码）
     * @param ip  请求来源IP（用于异常签到排查）
     */
    void sign(AttendanceSignDTO dto, String ip);

    /**
     * 学员端查询本人签到历史
     *
     * @return 签到记录列表
     */
    List<StudyOfflineRecordVO> myRecords();
}
