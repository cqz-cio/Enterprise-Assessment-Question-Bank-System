package com.yf.modules.exam.position.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yf.base.api.api.ApiRest;
import com.yf.base.api.api.controller.BaseController;
import com.yf.base.api.api.dto.BaseIdReqDTO;
import com.yf.base.api.api.dto.BaseIdsReqDTO;
import com.yf.base.api.api.dto.PagingReqDTO;
import com.yf.modules.exam.position.dto.PositionDTO;
import com.yf.modules.exam.position.dto.PositionQueryDTO;
import com.yf.modules.exam.position.dto.PositionStatusReqDTO;
import com.yf.modules.exam.position.entity.Position;
import com.yf.modules.exam.position.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "岗位管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exam/position")
public class PositionController extends BaseController {

    private final PositionService positionService;

    @Operation(summary = "新增或修改岗位")
    @RequiresPermissions(value = {"exam:position:add", "exam:position:edit"}, logical = Logical.OR)
    @PostMapping("/save")
    public ApiRest<?> save(@Valid @RequestBody PositionDTO reqDTO) {
        positionService.savePosition(reqDTO);
        return success();
    }

    @Operation(summary = "岗位分页")
    @RequiresPermissions("exam:position:view")
    @PostMapping("/paging")
    public ApiRest<IPage<PositionDTO>> paging(@RequestBody PagingReqDTO<PositionQueryDTO> reqDTO) {
        return success(positionService.paging(reqDTO));
    }

    @Operation(summary = "岗位详情")
    @RequiresPermissions("exam:position:view")
    @PostMapping("/detail")
    public ApiRest<PositionDTO> detail(@RequestBody BaseIdReqDTO reqDTO) {
        return success(positionService.detail(reqDTO.getId()));
    }

    @Operation(summary = "启用岗位列表")
    @RequiresPermissions(value = {"exam:position:view", "exam:assignment:candidate:add"}, logical = Logical.OR)
    @PostMapping("/list-enabled")
    public ApiRest<List<Position>> listEnabled() {
        return success(positionService.listEnabled());
    }

    @Operation(summary = "按部门查询启用岗位及职级")
    @RequiresPermissions(value = {"exam:position:view", "repo:repo:view", "exam:exam:view", "exam:assignment:candidate:add"}, logical = Logical.OR)
    @PostMapping("/list-by-department")
    public ApiRest<List<PositionDTO>> listByDepartment(@RequestBody BaseIdReqDTO reqDTO) {
        return success(positionService.listEnabledByDepartment(reqDTO.getId()));
    }

    @Operation(summary = "启用或停用岗位")
    @RequiresPermissions("exam:position:edit")
    @PostMapping("/change-status")
    public ApiRest<?> changeStatus(@Valid @RequestBody PositionStatusReqDTO reqDTO) {
        positionService.changeStatus(reqDTO);
        return success();
    }

    @Operation(summary = "删除岗位")
    @RequiresPermissions("exam:position:delete")
    @PostMapping("/delete")
    public ApiRest<?> delete(@RequestBody BaseIdsReqDTO reqDTO) {
        positionService.delete(reqDTO.getIds());
        return success();
    }
}
