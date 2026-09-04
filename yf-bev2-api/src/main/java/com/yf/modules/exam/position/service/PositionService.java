package com.yf.modules.exam.position.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yf.base.api.api.dto.PagingReqDTO;
import com.yf.modules.exam.position.dto.PositionDTO;
import com.yf.modules.exam.position.dto.PositionQueryDTO;
import com.yf.modules.exam.position.dto.PositionStatusReqDTO;
import com.yf.modules.exam.position.entity.Position;

import java.util.List;

public interface PositionService extends IService<Position> {

    IPage<PositionDTO> paging(PagingReqDTO<PositionQueryDTO> reqDTO);

    List<Position> listEnabled();

    PositionDTO detail(String id);

    List<PositionDTO> listEnabledByDepartment(String departmentId);

    void savePosition(PositionDTO reqDTO);

    void changeStatus(PositionStatusReqDTO reqDTO);

    void delete(List<String> ids);

    Position requireEnabled(String id);

    void requireDepartmentPosition(String departmentId, String positionId);

    void requireGradeForPosition(String gradeId, String positionId);
}
