package com.yf.modules.exam.paper.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yf.base.api.api.dto.PagingReqDTO;
import com.yf.modules.exam.paper.dto.PaperDTO;
import com.yf.modules.exam.paper.dto.response.PaperCheckRespDTO;
import com.yf.modules.exam.paper.dto.response.PaperDetailRespDTO;
import com.yf.modules.exam.paper.dto.response.PaperRealTimeRespDTO;
import com.yf.modules.exam.paper.entity.Paper;
import com.yf.modules.exam.assignment.entity.ExamAssignment;

/**
 * <p>
 * 试卷业务接口类
 * </p>
 *
 * @author 聪明笨狗
 * @since 2025-04-14 17:40
 */
public interface PaperService extends IService<Paper> {

    /**
     * 分页查询数据
     *
     * @param reqDTO
     * @return
     */
    IPage<PaperDTO> paging(PagingReqDTO<PaperDTO> reqDTO);

    /**
     * 查找详情
     *
     * @param id
     * @return
     */
    PaperDTO detail(String id, String userId);

    /**
     * 校验考试
     *
     * @param examId
     * @param userId
     */
    PaperCheckRespDTO preCheck(String examId, String userId);

    /**
     * 创建试卷，用于考试
     *
     * @param examId
     * @param userId
     */
    String createPaper(String examId, String userId);

    /**
     * 按考核分配创建唯一试卷，不使用旧版考试次数规则。
     */
    String createPaperForAssignment(ExamAssignment assignment);

    /**
     * 交卷
     *
     * @param paperId
     * @return
     */
    void handPaper(String paperId);

    /**
     * 当前登录考生主动交卷。
     */
    void handPaper(String paperId, String userId);


    /**
     * 获取实时状态
     *
     * @param paperId
     * @return
     */
    PaperRealTimeRespDTO realTimeState(String paperId, String userId);

    /**
     * 完整试卷详情
     * @param id
     * @return
     */
    PaperDetailRespDTO fullDetail(String id);
}
