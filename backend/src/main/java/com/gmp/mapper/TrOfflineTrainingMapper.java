package com.gmp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gmp.entity.TrOfflineTraining;
import org.apache.ibatis.annotations.Mapper;

/**
 * 线下培训场次 Mapper
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，直接复用通用 CRUD 能力，无需额外自定义 SQL。
 */
@Mapper
public interface TrOfflineTrainingMapper extends BaseMapper<TrOfflineTraining> {
}
