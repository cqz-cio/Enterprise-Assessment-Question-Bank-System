package com.yf.modules.exam.repo.service.impl;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yf.base.api.api.dto.PagingReqDTO;
import com.yf.base.utils.BeanMapper;
import com.yf.modules.exam.repo.dto.RepoDTO;
import com.yf.modules.exam.repo.dto.response.RepoListRespDTO;
import com.yf.modules.exam.repo.dto.response.RepoStatRespDTO;
import com.yf.modules.exam.repo.entity.Repo;
import com.yf.modules.exam.repo.mapper.RepoMapper;
import com.yf.modules.exam.repo.service.RepoService;
import com.yf.modules.exam.common.AssessmentScene;
import com.yf.modules.exam.position.service.PositionService;
import com.yf.base.api.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 题库业务实现类
 * </p>
 *
 * @author 聪明笨狗
 * @since 2025-04-11 09:42
 */
@Service
@RequiredArgsConstructor
public class RepoServiceImpl extends ServiceImpl<RepoMapper, Repo> implements RepoService {

    private final PositionService positionService;

    @Override
    public IPage<RepoListRespDTO> paging(PagingReqDTO<RepoDTO> reqDTO) {
        return baseMapper.paging(reqDTO.toPage(), reqDTO.getParams());
    }


    @Override
    public void save(RepoDTO reqDTO) {
        if (StringUtils.isBlank(reqDTO.getDepartId()) || StringUtils.isBlank(reqDTO.getPositionId())) {
            throw new ServiceException("题库必须选择部门和岗位！");
        }
        if (!AssessmentScene.isValid(reqDTO.getSceneType())) {
            throw new ServiceException("考核场景无效！");
        }
        positionService.requireDepartmentPosition(reqDTO.getDepartId(), reqDTO.getPositionId());
        if (AssessmentScene.PROMOTION.equals(reqDTO.getSceneType())) {
            positionService.requireGradeForPosition(reqDTO.getTargetGradeId(), reqDTO.getPositionId());
        } else {
            reqDTO.setTargetGradeId(null);
        }
        if (reqDTO.getStatus() == null) {
            reqDTO.setStatus(1);
        }
        //复制参数
        Repo entity = new Repo();
        BeanMapper.copy(reqDTO, entity);
        this.saveOrUpdate(entity);
    }

    @Override
    public void delete(List<String> ids) {
        //批量删除
        this.removeByIds(ids);
    }

    @Override
    public RepoDTO detail(String id) {
        Repo entity = this.getById(id);
        RepoDTO dto = new RepoDTO();
        BeanMapper.copy(entity, dto);
        return dto;
    }

    @Override
    public List<RepoStatRespDTO> listStat(String repoId) {
        return baseMapper.listStat(repoId);
    }

}
