package com.yf.modules.exam.assignment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yf.base.api.api.dto.PagingReqDTO;
import com.yf.modules.exam.assignment.dto.request.AssignmentQueryReqDTO;
import com.yf.modules.exam.assignment.dto.request.AssignmentStatusReqDTO;
import com.yf.modules.exam.assignment.dto.request.CandidateCreateReqDTO;
import com.yf.modules.exam.assignment.dto.request.CandidateVerifyReqDTO;
import com.yf.modules.exam.assignment.dto.response.AssignmentCurrentRespDTO;
import com.yf.modules.exam.assignment.dto.response.AssignmentListRespDTO;
import com.yf.modules.exam.assignment.dto.response.AssignmentResultRespDTO;
import com.yf.modules.exam.assignment.dto.response.AssignmentStartRespDTO;
import com.yf.modules.exam.assignment.dto.response.CandidateCreateRespDTO;
import com.yf.modules.exam.assignment.dto.response.CandidateVerifyRespDTO;
import com.yf.modules.exam.assignment.entity.ExamAssignment;
import com.yf.modules.exam.paper.dto.response.PaperDetailRespDTO;

public interface ExamAssignmentService extends IService<ExamAssignment> {

    CandidateCreateRespDTO createCandidate(CandidateCreateReqDTO reqDTO);

    CandidateVerifyRespDTO verifyCandidate(CandidateVerifyReqDTO reqDTO);

    IPage<AssignmentListRespDTO> candidatePaging(PagingReqDTO<AssignmentQueryReqDTO> reqDTO);

    PaperDetailRespDTO candidateResultDetail(String id);

    CandidateCreateRespDTO resetCode(String id);

    void changeStatus(AssignmentStatusReqDTO reqDTO);

    AssignmentCurrentRespDTO current(String id, String userId);

    AssignmentStartRespDTO start(String id, String userId);

    AssignmentResultRespDTO myResult(String id, String userId);
}
