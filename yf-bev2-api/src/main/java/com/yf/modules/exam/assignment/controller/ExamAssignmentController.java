package com.yf.modules.exam.assignment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yf.base.api.api.ApiRest;
import com.yf.base.api.api.controller.BaseController;
import com.yf.base.api.api.dto.BaseIdReqDTO;
import com.yf.base.api.api.dto.PagingReqDTO;
import com.yf.modules.exam.assignment.dto.request.AssignmentQueryReqDTO;
import com.yf.modules.exam.assignment.dto.request.AssignmentStatusReqDTO;
import com.yf.modules.exam.assignment.dto.request.CandidateCreateReqDTO;
import com.yf.modules.exam.assignment.dto.request.CandidateVerifyReqDTO;
import com.yf.modules.exam.assignment.dto.response.AssignmentCurrentRespDTO;
import com.yf.modules.exam.assignment.dto.response.AssignmentListRespDTO;
import com.yf.modules.exam.assignment.dto.response.AssignmentResultRespDTO;
import com.yf.modules.exam.assignment.dto.response.CandidateCreateRespDTO;
import com.yf.modules.exam.assignment.dto.response.CandidateVerifyRespDTO;
import com.yf.modules.exam.assignment.service.CandidateVerificationThrottle;
import com.yf.modules.exam.assignment.service.ExamAssignmentService;
import com.yf.modules.exam.paper.dto.response.PaperDetailRespDTO;
import com.yf.system.modules.user.UserUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "考核分配与候选人")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exam/assignment")
public class ExamAssignmentController extends BaseController {

    private final ExamAssignmentService assignmentService;
    private final CandidateVerificationThrottle verificationThrottle;

    @Operation(summary = "创建候选人考核")
    @RequiresPermissions("exam:assignment:candidate:add")
    @PostMapping("/candidate/create")
    public ApiRest<CandidateCreateRespDTO> createCandidate(@Valid @RequestBody CandidateCreateReqDTO reqDTO) {
        return success(assignmentService.createCandidate(reqDTO));
    }

    @Operation(summary = "候选人姓名和考核码验证")
    @PostMapping("/candidate/verify")
    public ApiRest<CandidateVerifyRespDTO> verifyCandidate(@Valid @RequestBody CandidateVerifyReqDTO reqDTO,
                                                            HttpServletRequest request) {
        String throttleKey = verificationThrottle.check(request.getRemoteAddr(), reqDTO.getAccessCode());
        CandidateVerifyRespDTO response = assignmentService.verifyCandidate(reqDTO);
        verificationThrottle.clear(throttleKey);
        return success(response);
    }

    @Operation(summary = "候选人考核分页")
    @RequiresPermissions("exam:assignment:candidate:view")
    @PostMapping("/candidate/paging")
    public ApiRest<IPage<AssignmentListRespDTO>> candidatePaging(
            @RequestBody PagingReqDTO<AssignmentQueryReqDTO> reqDTO) {
        return success(assignmentService.candidatePaging(reqDTO));
    }

    @Operation(summary = "查看候选人考核结果详情")
    @RequiresPermissions("exam:assignment:candidate:result")
    @PostMapping("/candidate/result-detail")
    public ApiRest<PaperDetailRespDTO> candidateResultDetail(@RequestBody BaseIdReqDTO reqDTO) {
        return success(assignmentService.candidateResultDetail(reqDTO.getId()));
    }

    @Operation(summary = "重置候选人考核码")
    @RequiresPermissions("exam:assignment:candidate:code")
    @PostMapping("/candidate/reset-code")
    public ApiRest<CandidateCreateRespDTO> resetCode(@RequestBody BaseIdReqDTO reqDTO) {
        return success(assignmentService.resetCode(reqDTO.getId()));
    }

    @Operation(summary = "停用或恢复考核")
    @RequiresPermissions("exam:assignment:edit")
    @PostMapping("/change-status")
    public ApiRest<?> changeStatus(@Valid @RequestBody AssignmentStatusReqDTO reqDTO) {
        assignmentService.changeStatus(reqDTO);
        return success();
    }

    @Operation(summary = "查询本人考核分配")
    @RequiresPermissions("exam:client:enter")
    @PostMapping("/current")
    public ApiRest<AssignmentCurrentRespDTO> current(@RequestBody BaseIdReqDTO reqDTO) {
        return success(assignmentService.current(reqDTO.getId(), UserUtils.getUserId()));
    }

    @Operation(summary = "查询本人最终结果")
    @RequiresPermissions("exam:client:enter")
    @PostMapping("/my-result")
    public ApiRest<AssignmentResultRespDTO> myResult(@RequestBody BaseIdReqDTO reqDTO) {
        return success(assignmentService.myResult(reqDTO.getId(), UserUtils.getUserId()));
    }
}
