package com.yf.modules.exam.position.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yf.base.api.api.dto.PagingReqDTO;
import com.yf.base.api.exception.ServiceException;
import com.yf.base.utils.BeanMapper;
import com.yf.modules.exam.position.dto.PositionDTO;
import com.yf.modules.exam.position.dto.PositionGradeDTO;
import com.yf.modules.exam.position.dto.PositionQueryDTO;
import com.yf.modules.exam.position.dto.PositionStatusReqDTO;
import com.yf.modules.exam.position.entity.Position;
import com.yf.modules.exam.position.mapper.PositionMapper;
import com.yf.modules.exam.position.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PositionServiceImpl extends ServiceImpl<PositionMapper, Position> implements PositionService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public IPage<PositionDTO> paging(PagingReqDTO<PositionQueryDTO> reqDTO) {
        PositionQueryDTO query = reqDTO.getParams();
        LambdaQueryWrapper<Position> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.like(StringUtils.isNotBlank(query.getCode()), Position::getCode, query.getCode())
                    .like(StringUtils.isNotBlank(query.getName()), Position::getName, query.getName())
                    .eq(query.getStatus() != null, Position::getStatus, query.getStatus());
            if (StringUtils.isNotBlank(query.getDepartmentId())) {
                List<String> ids = jdbcTemplate.queryForList(
                        "SELECT dp.position_id FROM el_depart_position dp JOIN el_sys_depart d ON d.id = dp.depart_id "
                                + "WHERE dp.depart_id = ? AND d.status = 1 AND d.dept_type = 2 AND d.parent_id <> '0'",
                        String.class, query.getDepartmentId());
                if (ids.isEmpty()) {
                    wrapper.eq(Position::getId, "__NONE__");
                } else {
                    wrapper.in(Position::getId, ids);
                }
            }
        }
        wrapper.orderByAsc(Position::getSort).orderByDesc(Position::getCreateTime);
        return page(reqDTO.toPage(), wrapper).convert(this::toDto);
    }

    @Override
    public List<Position> listEnabled() {
        return list(new LambdaQueryWrapper<Position>()
                .eq(Position::getStatus, 1)
                .orderByAsc(Position::getSort)
                .orderByAsc(Position::getName));
    }

    @Override
    public List<PositionDTO> listEnabledByDepartment(String departmentId) {
        if (StringUtils.isBlank(departmentId)) {
            return List.of();
        }
        List<String> positionIds = jdbcTemplate.queryForList(
                "SELECT dp.position_id FROM el_depart_position dp JOIN el_sys_depart d ON d.id = dp.depart_id "
                        + "WHERE dp.depart_id = ? AND d.status = 1 AND d.dept_type = 2 AND d.parent_id <> '0'",
                String.class, departmentId);
        if (positionIds.isEmpty()) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<Position>()
                .in(Position::getId, positionIds)
                .eq(Position::getStatus, 1)
                .orderByAsc(Position::getSort)
                .orderByAsc(Position::getName)).stream().map(this::toDto).toList();
    }

    @Override
    public PositionDTO detail(String id) {
        Position position = requireExists(id);
        return toDto(position);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePosition(PositionDTO reqDTO) {
        String code = StringUtils.upperCase(StringUtils.trim(reqDTO.getCode()));
        LambdaQueryWrapper<Position> duplicate = new LambdaQueryWrapper<Position>()
                .eq(Position::getCode, code)
                .ne(StringUtils.isNotBlank(reqDTO.getId()), Position::getId, reqDTO.getId());
        if (count(duplicate) > 0) {
            throw new ServiceException("岗位编码不能重复！");
        }
        validateDepartments(reqDTO.getDepartmentIds());
        validateGrades(reqDTO.getGrades());

        Position entity = StringUtils.isBlank(reqDTO.getId()) ? new Position() : requireExists(reqDTO.getId());
        BeanMapper.copy(reqDTO, entity);
        entity.setCode(code);
        entity.setName(StringUtils.trim(reqDTO.getName()));
        saveOrUpdate(entity);
        replaceDepartments(entity.getId(), reqDTO.getDepartmentIds());
        replaceGrades(entity.getId(), reqDTO.getGrades());
    }

    @Override
    public void changeStatus(PositionStatusReqDTO reqDTO) {
        Position position = requireExists(reqDTO.getId());
        position.setStatus(reqDTO.getStatus());
        updateById(position);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        for (String id : ids) {
            Integer references = jdbcTemplate.queryForObject(
                    "SELECT (SELECT COUNT(*) FROM el_repo WHERE position_id = ?) + "
                            + "(SELECT COUNT(*) FROM el_exam WHERE position_id = ?) + "
                            + "(SELECT COUNT(*) FROM el_exam_assignment WHERE position_id = ?)",
                    Integer.class, id, id, id);
            if (references != null && references > 0) {
                throw new ServiceException("岗位已被题库、考核模板或考核分配使用，不能删除；可改为停用！");
            }
            jdbcTemplate.update("DELETE FROM el_depart_position WHERE position_id = ?", id);
            jdbcTemplate.update("DELETE FROM el_position_grade WHERE position_id = ?", id);
        }
        removeByIds(ids);
    }

    @Override
    public Position requireEnabled(String id) {
        Position position = getOne(new LambdaQueryWrapper<Position>()
                .eq(Position::getId, id)
                .eq(Position::getStatus, 1), false);
        if (position == null) {
            throw new ServiceException("岗位不存在或已停用！");
        }
        return position;
    }

    @Override
    public void requireDepartmentPosition(String departmentId, String positionId) {
        if (StringUtils.isBlank(departmentId) || StringUtils.isBlank(positionId)) {
            throw new ServiceException("部门和岗位不能为空！");
        }
        requireEnabled(positionId);
        Integer departmentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM el_sys_depart WHERE id = ? AND status = 1 "
                        + "AND dept_type = 2 AND parent_id <> '0'",
                Integer.class, departmentId);
        if (departmentCount == null || departmentCount == 0) {
            throw new ServiceException("只能选择启用的下级业务部门，不能选择公司根节点！");
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM el_depart_position dp JOIN el_sys_depart d ON d.id = dp.depart_id "
                        + "WHERE dp.depart_id = ? AND dp.position_id = ? AND d.status = 1 "
                        + "AND d.dept_type = 2 AND d.parent_id <> '0'",
                Integer.class, departmentId, positionId);
        if (count == null || count == 0) {
            throw new ServiceException("所选岗位不属于该部门或关联已停用！");
        }
    }

    @Override
    public void requireGradeForPosition(String gradeId, String positionId) {
        if (StringUtils.isBlank(gradeId)) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM el_position_grade WHERE id = ? AND position_id = ? AND status = 1",
                Integer.class, gradeId, positionId);
        if (count == null || count == 0) {
            throw new ServiceException("目标职级不存在、已停用或不属于所选岗位！");
        }
    }

    private Position requireExists(String id) {
        Position position = getById(id);
        if (position == null) {
            throw new ServiceException("岗位不存在！");
        }
        return position;
    }

    private PositionDTO toDto(Position position) {
        PositionDTO dto = new PositionDTO();
        BeanMapper.copy(position, dto);
        List<String> departmentIds = new ArrayList<>();
        List<String> departmentNames = new ArrayList<>();
        jdbcTemplate.query("SELECT d.id, d.dept_name FROM el_depart_position dp "
                        + "JOIN el_sys_depart d ON d.id = dp.depart_id WHERE dp.position_id = ? "
                        + "ORDER BY d.dept_level, d.sort, d.dept_name",
                rs -> {
                    departmentIds.add(rs.getString("id"));
                    departmentNames.add(rs.getString("dept_name"));
                }, position.getId());
        dto.setDepartmentIds(departmentIds);
        dto.setDepartmentNames(departmentNames);
        dto.setGrades(jdbcTemplate.query("SELECT id, code, name, level_no, sort, status "
                        + "FROM el_position_grade WHERE position_id = ? ORDER BY sort, level_no, name",
                (rs, rowNum) -> {
                    PositionGradeDTO grade = new PositionGradeDTO();
                    grade.setId(rs.getString("id"));
                    grade.setCode(rs.getString("code"));
                    grade.setName(rs.getString("name"));
                    grade.setLevelNo((Integer) rs.getObject("level_no"));
                    grade.setSort(rs.getInt("sort"));
                    grade.setStatus(rs.getInt("status"));
                    return grade;
                }, position.getId()));
        return dto;
    }

    private void validateDepartments(List<String> departmentIds) {
        if (CollectionUtils.isEmpty(departmentIds)) {
            throw new ServiceException("岗位至少关联一个部门！");
        }
        Set<String> unique = new HashSet<>(departmentIds);
        String placeholders = String.join(",", java.util.Collections.nCopies(unique.size(), "?"));
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM el_sys_depart WHERE status = 1 AND dept_type = 2 "
                        + "AND parent_id <> '0' AND id IN (" + placeholders + ")",
                Integer.class, unique.toArray());
        if (count == null || count != unique.size()) {
            throw new ServiceException("只能选择启用的下级业务部门，不能选择公司根节点！");
        }
    }

    private void validateGrades(List<PositionGradeDTO> grades) {
        if (CollectionUtils.isEmpty(grades)) {
            return;
        }
        Set<String> codes = new HashSet<>();
        for (PositionGradeDTO grade : grades) {
            String code = StringUtils.upperCase(StringUtils.trim(grade.getCode()));
            if (!codes.add(code)) {
                throw new ServiceException("同一岗位的职级编码不能重复！");
            }
            grade.setCode(code);
            grade.setName(StringUtils.trim(grade.getName()));
        }
    }

    private void replaceDepartments(String positionId, List<String> departmentIds) {
        jdbcTemplate.update("DELETE FROM el_depart_position WHERE position_id = ?", positionId);
        for (String departmentId : new HashSet<>(departmentIds)) {
            jdbcTemplate.update("INSERT INTO el_depart_position(id, depart_id, position_id) VALUES (?, ?, ?)",
                    IdWorker.getIdStr(), departmentId, positionId);
        }
    }

    private void replaceGrades(String positionId, List<PositionGradeDTO> grades) {
        List<String> retained = CollectionUtils.isEmpty(grades) ? List.of()
                : grades.stream().map(PositionGradeDTO::getId).filter(StringUtils::isNotBlank).toList();
        if (retained.isEmpty()) {
            Integer refs = jdbcTemplate.queryForObject(
                    "SELECT (SELECT COUNT(*) FROM el_repo WHERE target_grade_id IN (SELECT id FROM el_position_grade WHERE position_id = ?)) + "
                            + "(SELECT COUNT(*) FROM el_exam WHERE target_grade_id IN (SELECT id FROM el_position_grade WHERE position_id = ?)) + "
                            + "(SELECT COUNT(*) FROM el_exam_assignment WHERE target_grade_id IN (SELECT id FROM el_position_grade WHERE position_id = ?))",
                    Integer.class, positionId, positionId, positionId);
            if (refs != null && refs > 0) {
                throw new ServiceException("已有题库、模板或考核使用该岗位职级，不能全部删除；可改为停用！");
            }
            jdbcTemplate.update("DELETE FROM el_position_grade WHERE position_id = ?", positionId);
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(retained.size(), "?"));
        List<Object> ownershipArgs = new ArrayList<>();
        ownershipArgs.add(positionId);
        ownershipArgs.addAll(retained);
        Integer ownedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM el_position_grade WHERE position_id = ? AND id IN (" + placeholders + ")",
                Integer.class, ownershipArgs.toArray());
        if (ownedCount == null || ownedCount != retained.size()) {
            throw new ServiceException("职级数据已变化，请刷新岗位后重试！");
        }
        List<Object> args = new ArrayList<>();
        args.add(positionId);
        args.addAll(retained);
        Integer removedRefs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM el_position_grade g WHERE g.position_id = ? AND g.id NOT IN (" + placeholders + ") "
                        + "AND ((SELECT COUNT(*) FROM el_repo r WHERE r.target_grade_id = g.id) + "
                        + "(SELECT COUNT(*) FROM el_exam e WHERE e.target_grade_id = g.id) + "
                        + "(SELECT COUNT(*) FROM el_exam_assignment a WHERE a.target_grade_id = g.id)) > 0",
                Integer.class, args.toArray());
        if (removedRefs != null && removedRefs > 0) {
            throw new ServiceException("被题库、模板或考核使用的职级不能删除；可改为停用！");
        }
        jdbcTemplate.update("DELETE FROM el_position_grade WHERE position_id = ?", positionId);
        int index = 0;
        for (PositionGradeDTO grade : grades) {
            jdbcTemplate.update("INSERT INTO el_position_grade(id, position_id, code, name, level_no, sort, status) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    StringUtils.defaultIfBlank(grade.getId(), IdWorker.getIdStr()), positionId,
                    grade.getCode(), grade.getName(), grade.getLevelNo(),
                    grade.getSort() == null ? index : grade.getSort(), grade.getStatus() == null ? 1 : grade.getStatus());
            index++;
        }
    }
}
