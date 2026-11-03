package com.gmp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gmp.entity.TrAttendanceRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 线下培训签到记录 Mapper
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，直接复用通用 CRUD 能力，无需额外自定义 SQL。
 */
@Mapper
public interface TrAttendanceRecordMapper extends BaseMapper<TrAttendanceRecord> {
}
