package com.yf.modules.exam.paper.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yf.base.api.exception.ServiceException;
import com.yf.base.utils.LetterUtils;
import com.yf.base.utils.BeanMapper;
import com.yf.base.utils.DecimalUtils;
import com.yf.modules.exam.paper.dto.request.PaperQuFillReqDTO;
import com.yf.modules.exam.paper.dto.response.PaperQuCardItemRespDTO;
import com.yf.modules.exam.paper.dto.response.PaperQuCardRespDTO;
import com.yf.modules.exam.paper.dto.response.PaperQuDetailDTO;
import com.yf.modules.exam.paper.dto.response.PaperQuFillRespDTO;
import com.yf.modules.exam.paper.entity.PaperQu;
import com.yf.modules.exam.paper.entity.PaperQuAnswer;
import com.yf.modules.exam.paper.mapper.PaperQuMapper;
import com.yf.modules.exam.paper.service.PaperQuAnswerService;
import com.yf.modules.exam.paper.service.PaperAccessService;
import com.yf.modules.exam.paper.service.PaperQuService;
import com.yf.modules.exam.repo.dto.RepoQuAnswerDTO;
import com.yf.modules.exam.repo.dto.request.RepoQuDetailDTO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * <p>
 * 试卷考题业务实现类
 * </p>
 *
 * @author 聪明笨狗
 * @since 2025-04-14 17:40
 */
@RequiredArgsConstructor
@Service
public class PaperQuServiceImpl extends ServiceImpl<PaperQuMapper, PaperQu> implements PaperQuService {

    private final PaperQuAnswerService paperQuAnswerService;
    private final PaperAccessService paperAccessService;


    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveToPaper(String paperId, BigDecimal perScore, List<RepoQuDetailDTO> quList, int startSort, boolean shuffleOptions) {

        List<PaperQu> paperQuList = new ArrayList<>();
        List<PaperQuAnswer> answerList = new ArrayList<>();

        int sort = startSort;

        for (RepoQuDetailDTO dto : quList) {
            com.yf.modules.exam.paper.service.ObjectivePaperPolicy.requireSupported(dto.getQuType());
            boolean objective = com.yf.modules.exam.paper.service.ObjectivePaperPolicy.isObjective(dto.getQuType());
            if (dto.getContent() == null || (objective && (dto.getAnswerList() == null || dto.getAnswerList().isEmpty()))) {
                throw new ServiceException("题目或选项不完整，无法生成试卷！");
            }
            if (objective) {
            long rightCount = dto.getAnswerList().stream().filter(a -> Boolean.TRUE.equals(a.getIsRight())).count();
            if (rightCount == 0 || (!"multi".equals(dto.getQuType()) && rightCount != 1)
                    || dto.getAnswerList().stream().anyMatch(a -> a.getContent() == null || a.getId() == null)) {
                throw new ServiceException("题目正确答案或选项配置无效！");
            }
            }
            PaperQu entity = new PaperQu();
            entity.setGradingState(objective ? "NOT_REQUIRED" : "PENDING");
            entity.setContentSnapshot(dto.getContent());
            entity.setAnalysisSnapshot(dto.getAnalysis());
            entity.setReferenceAnswerSnapshot(dto.getReferenceAnswer());
            entity.setGradingCriteriaSnapshot(dto.getGradingCriteria());
            entity.setPaperId(paperId);
            entity.setQuType(dto.getQuType());
            entity.setActualScore(DecimalUtils.zero());
            entity.setScore(perScore);
            entity.setQuId(dto.getId());
            entity.setAnswered(false);
            entity.setSort(sort);
            paperQuList.add(entity);

            List<RepoQuAnswerDTO> answers = new ArrayList<>(objective ? dto.getAnswerList() : List.of());
            if (shuffleOptions && !"judge".equals(dto.getQuType())) Collections.shuffle(answers);

            int i = 0;
            for (RepoQuAnswerDTO answer : answers) {
                PaperQuAnswer ae = new PaperQuAnswer();
                ae.setPaperId(paperId);
                ae.setQuId(dto.getId());
                ae.setAnswerId(answer.getId());
                ae.setContentSnapshot(answer.getContent());
                ae.setChecked(false);
                ae.setIsRight(answer.getIsRight());
                ae.setAbc(LetterUtils.getLetter(i));
                ae.setSort(i);
                answerList.add(ae);
                i++;
            }

            sort++;
        }

        // 保存题目
        saveBatch(paperQuList);

        // 保存选项
        if (!answerList.isEmpty()) paperQuAnswerService.saveBatch(answerList);

    }

    @Override
    public List<PaperQuCardRespDTO> listQuCard(String paperId, String userId) {

        paperAccessService.requireOwner(paperId, userId);


        //查找全部题目
        QueryWrapper<PaperQu> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .select(PaperQu::getQuId, PaperQu::getQuType, PaperQu::getAnswered, PaperQu::getMark)
                .eq(PaperQu::getPaperId, paperId)
                .orderByAsc(PaperQu::getSort, PaperQu::getId);
        List<PaperQu> paperQuList = this.list(wrapper);

        // 使用程序转换成题型分组Map
        Map<String, List<PaperQuCardItemRespDTO>> map = new LinkedHashMap<>();
        for (PaperQu paperQu : paperQuList) {
            String key = paperQu.getQuType();

            // 复制属性
            PaperQuCardItemRespDTO item = new PaperQuCardItemRespDTO();
            BeanMapper.copy(paperQu, item);

            if (map.containsKey(key)) {
                map.get(key).add(item);
            } else {
                List<PaperQuCardItemRespDTO> list = new ArrayList<>();
                list.add(item);
                map.put(key, list);
            }
        }

        // 转换为列表并返回
        List<PaperQuCardRespDTO> dtoList = new ArrayList<>();
        for (Map.Entry<String, List<PaperQuCardItemRespDTO>> entry : map.entrySet()) {
            PaperQuCardRespDTO dto = new PaperQuCardRespDTO();
            dto.setQuType(entry.getKey());
            dto.setItemList(entry.getValue());
            dtoList.add(dto);
        }
        return dtoList;
    }

    @Override
    public PaperQuDetailDTO detailForAnswer(String paperId, String quId, String userId) {
        paperAccessService.requireOwner(paperId, userId);
        return baseMapper.detailForAnswer(paperId, quId);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 20)
    @Override
    public PaperQuFillRespDTO fillAnswer(PaperQuFillReqDTO reqDTO, String userId) {

        // 参数
        String paperId = reqDTO.getPaperId();
        String quId = reqDTO.getQuId();
        List<String> checkedItems = reqDTO.getCheckedItems();
        if (checkedItems == null || checkedItems.stream().anyMatch(Objects::isNull)
                || new HashSet<>(checkedItems).size() != checkedItems.size()) {
            throw new ServiceException("作答选项格式无效！");
        }

        paperAccessService.requireLockedWritableOwner(paperId, userId);

        //查找全部题目
        QueryWrapper<PaperQu> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .select(PaperQu::getId, PaperQu::getQuId, PaperQu::getQuType, PaperQu::getScore)
                .eq(PaperQu::getPaperId, paperId)
                .eq(PaperQu::getQuId, quId);

        PaperQu paperQu = this.getOne(wrapper, false);
        if (paperQu == null) {
            throw new ServiceException("答题错误，题目不存在！");
        }

        com.yf.modules.exam.paper.service.ObjectivePaperPolicy.requireObjective(paperQu.getQuType());
        if (!"multi".equals(paperQu.getQuType()) && checkedItems.size() > 1) {
            throw new ServiceException("单选题和判断题只能选择一个选项！");
        }
        // 标准答案列表
        List<PaperQuAnswer> answerList = paperQuAnswerService.listForAnswer(paperId, quId);
        List<String> rightList = new ArrayList<>();

        Set<String> validIds = new HashSet<>();
        for (PaperQuAnswer answer : answerList) validIds.add(answer.getAnswerId());
        if (!validIds.containsAll(checkedItems)) throw new ServiceException("选项不属于当前试题！");
        // 正确列表
        for (PaperQuAnswer answer : answerList) {
            if (answer.getIsRight() != null && answer.getIsRight()) {
                rightList.add(answer.getAnswerId());
            }

            // 是否勾选
            answer.setChecked(checkedItems.contains(answer.getAnswerId()));
        }

        if (rightList.isEmpty()) throw new ServiceException("试题评分依据缺失，请联系管理员！");
        // 判断是否正确
        boolean isRight = areListsEqualIgnoreOrder(rightList, checkedItems);

        // 进行保存操作
        paperQuAnswerService.updateBatchById(answerList);

        // 更新结果
        paperQu.setIsRight(isRight);
        paperQu.setActualScore(isRight ? paperQu.getScore() : BigDecimal.ZERO);
        paperQu.setAnswered(CollectionUtils.isNotEmpty(checkedItems));
        this.updateById(paperQu);


        PaperQuFillRespDTO respDTO = new PaperQuFillRespDTO();
        respDTO.setFilled(paperQu.getAnswered());
        return respDTO;
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, timeout = 20)
    @Override
    public PaperQuFillRespDTO fillTextAnswer(com.yf.modules.exam.paper.dto.request.PaperTextAnswerDTO request, String userId) {
        if (request.getAnswerText() == null || request.getAnswerText().length() > 5000) throw new ServiceException("简答题答案不能超过 5000 字！");
        paperAccessService.requireLockedWritableOwner(request.getPaperId(), userId);
        PaperQu question = getOne(new QueryWrapper<PaperQu>().eq("paper_id", request.getPaperId()).eq("qu_id", request.getQuId()), false);
        if (question == null || !"short".equals(question.getQuType())) throw new ServiceException("当前题目不是简答题！");
        question.setTextAnswer(request.getAnswerText());
        question.setAnswered(!request.getAnswerText().isBlank());
        updateById(question);
        PaperQuFillRespDTO result = new PaperQuFillRespDTO();
        result.setFilled(question.getAnswered()); result.setSavedAt(new Date()); return result;
    }

    @Override
    public BigDecimal sumTotalScore(String paperId) {
        return baseMapper.sumTotalScore(paperId);
    }

    /**
     * 两个List比较
     *
     * @param list1
     * @param list2
     * @return
     */
    private static boolean areListsEqualIgnoreOrder(List<String> list1, List<String> list2) {

        // 快速校验
        if (Objects.equals(list1, list2)) {
            return true;
        }  // 包括都为null的情况
        if (list1 == null || list2 == null) {
            return false;
        }
        if (list1.size() != list2.size()) {
            return false;
        }

        // 直接转换为 HashSet 比较
        return new HashSet<>(list1).equals(new HashSet<>(list2));
    }
}
