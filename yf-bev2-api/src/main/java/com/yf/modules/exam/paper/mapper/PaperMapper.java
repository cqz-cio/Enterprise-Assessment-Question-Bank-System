package com.yf.modules.exam.paper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yf.modules.exam.paper.dto.response.PaperDetailRespDTO;
import com.yf.modules.exam.paper.entity.Paper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 试卷Mapper
 * </p>
 *
 * @author 聪明笨狗
 * @since 2025-04-14 17:40
 */
public interface PaperMapper extends BaseMapper<Paper> {

    @org.apache.ibatis.annotations.Select("SELECT * FROM el_paper WHERE id = #{id} FOR UPDATE")
    Paper selectByIdForUpdate(@Param("id") String id);

    @org.apache.ibatis.annotations.Select("SELECT id FROM el_paper WHERE hand_state = 0 AND limit_time <= CURRENT_TIMESTAMP AND id > #{afterId} ORDER BY id LIMIT 100")
    java.util.List<String> selectOverdueIds(@Param("afterId") String afterId);

    /**
     * 查找试卷详情，包含全部试题及答案
     * @param id
     * @return
     */
    PaperDetailRespDTO selectPaperDetail(@Param("id") String id);
}
