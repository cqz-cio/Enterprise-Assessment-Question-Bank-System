package com.yf.modules.exam.assignment.controller;

import com.yf.base.api.api.ApiRest;
import com.yf.base.api.api.controller.BaseController;
import com.yf.base.api.api.dto.BaseIdReqDTO;
import com.yf.modules.exam.assignment.dto.request.*;
import com.yf.modules.exam.assignment.service.EmployeeAssignmentService;
import com.yf.system.modules.user.UserUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exam/assignment")
public class EmployeeAssignmentController extends BaseController {
    private final EmployeeAssignmentService service;
    @PostMapping("/employee/create") @RequiresPermissions("exam:assignment:employee:add")
    public ApiRest<?> create(@Valid @RequestBody EmployeeAssignmentCreateDTO req) { return success(service.create(req)); }
    @PostMapping("/employee/options") @RequiresPermissions("exam:assignment:employee:add")
    public ApiRest<?> options(@Valid @RequestBody EmployeeAssignmentQueryDTO req) { return success(service.employees(req)); }
    @PostMapping("/employee/templates") @RequiresPermissions("exam:assignment:employee:add")
    public ApiRest<?> templates(@Valid @RequestBody EmployeeAssignmentQueryDTO req) { return success(service.templates(req)); }
    @PostMapping("/employee/paging") @RequiresPermissions("exam:assignment:employee:view")
    public ApiRest<?> paging(@Valid @RequestBody EmployeeAssignmentQueryDTO req) { return success(service.paging(req,null)); }
    @PostMapping("/employee/change-status") @RequiresPermissions("exam:assignment:employee:edit")
    public ApiRest<?> status(@Valid @RequestBody AssignmentStatusReqDTO req) { service.changeStatus(req); return success(); }
    @PostMapping("/employee/result-detail") @RequiresPermissions("exam:assignment:employee:result")
    public ApiRest<?> result(@Valid @RequestBody BaseIdReqDTO req) { return success(service.result(req.getId())); }
    @PostMapping("/my-paging") @RequiresPermissions("exam:assignment:my:view")
    public ApiRest<?> mine(@Valid @RequestBody EmployeeAssignmentQueryDTO req) { return success(service.paging(req,UserUtils.getUserId())); }
}
