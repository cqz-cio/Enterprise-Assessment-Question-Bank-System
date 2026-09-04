package com.yf.modules.exam.assignment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yf.modules.exam.assignment.dto.request.AssignmentQueryReqDTO;
import com.yf.modules.exam.assignment.dto.response.AssignmentListRespDTO;
import com.yf.modules.exam.assignment.entity.ExamAssignment;
import org.apache.ibatis.annotations.Param;

public interface ExamAssignmentMapper extends BaseMapper<ExamAssignment> {

    ExamAssignment selectByIdForUpdate(@Param("id") String id);

    IPage<AssignmentListRespDTO> paging(Page<AssignmentListRespDTO> page,
                                        @Param("query") AssignmentQueryReqDTO query);
}
