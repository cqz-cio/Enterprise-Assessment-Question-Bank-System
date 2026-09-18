package com.yf.modules.exam.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yf.base.api.api.dto.PagingReqDTO;
import com.yf.base.utils.DecimalUtils;
import com.yf.modules.exam.exam.dto.ExamRecordDTO;
import com.yf.modules.exam.exam.dto.request.ExamRecordListReqDTO;
import com.yf.modules.exam.exam.entity.ExamRecord;
import com.yf.modules.exam.exam.mapper.ExamRecordMapper;
import com.yf.modules.exam.exam.service.ExamRecordService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * <p>
 * 考试记录业务实现类
 * </p>
 *
 * @author 沉醉寒风
 * @since 2025-04-17 14:59
 */
@Service
public class ExamRecordServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamRecordService {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Override
    public IPage<ExamRecordDTO> paging(PagingReqDTO<ExamRecordListReqDTO> reqDTO) {
        return baseMapper.paging(reqDTO.toPage(), reqDTO.getParams());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class, timeout=20)
    public void joinRecord(String examId, String userId, String paperId, BigDecimal score, Boolean passed) {

        // Separate assignments for the same user may settle concurrently. Serialize their aggregate record.
        jdbc.queryForList("SELECT id FROM el_sys_user WHERE id=? FOR UPDATE", userId);

        //查询条件
        QueryWrapper<ExamRecord> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .select(ExamRecord::getId, ExamRecord::getMaxScore, ExamRecord::getTryCount, ExamRecord::getPaperId)
                .eq(ExamRecord::getUserId, userId)
                .eq(ExamRecord::getExamId, examId);

        ExamRecord record = this.getOne(wrapper, false);

        if (record == null) {
            record = new ExamRecord();
            record.setUserId(userId);
            record.setExamId(examId);
            record.setPaperId(paperId);
            record.setMaxScore(score);
            record.setLastScore(score);
            record.setPassed(passed);
            record.setTryCount(1);
            this.save(record);
        } else if (paperId.equals(record.getPaperId())) {
            // A quarantined legacy subjective paper may already have an aggregate record.
            // Rebuild from final papers so its old provisional mark is never counted twice.
            var finalPapers = jdbc.queryForList("SELECT id,user_score,passed,hand_time FROM el_paper WHERE exam_id=? AND user_id=? AND hand_state=1 AND grading_state<>'PENDING' ORDER BY user_score DESC,hand_time DESC,id DESC", examId,userId);
            var best = finalPapers.get(0);
            record.setPaperId((String)best.get("id"));
            record.setMaxScore((BigDecimal)best.get("user_score"));
            record.setPassed(Boolean.TRUE.equals(best.get("passed")) || Integer.valueOf(1).equals(best.get("passed")));
            record.setTryCount(finalPapers.size());
            record.setLastScore(score);
            updateById(record);
        } else {

            // 更新最高分数
            BigDecimal maxScore = record.getMaxScore();
            if (DecimalUtils.gt(score, maxScore)) {
                record.setMaxScore(score);
                record.setPassed(passed);
                record.setPaperId(paperId);
            }
            record.setTryCount(record.getTryCount() + 1);
            record.setLastScore(score);
            this.updateById(record);
        }
    }

    @Override
    public int findTryCount(String examId, String userId) {

        //查询条件
        QueryWrapper<ExamRecord> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .select(ExamRecord::getId, ExamRecord::getTryCount)
                .eq(ExamRecord::getUserId, userId)
                .eq(ExamRecord::getExamId, examId);

        ExamRecord record = this.getOne(wrapper, false);
        if (record != null) {
            return record.getTryCount();
        }

        return 0;
    }

    @Override
    public IPage<ExamRecordDTO> clientPaging(PagingReqDTO<ExamRecordListReqDTO> reqDTO) {
        return baseMapper.clientPaging(reqDTO.toPage(), reqDTO.getParams());
    }

}
