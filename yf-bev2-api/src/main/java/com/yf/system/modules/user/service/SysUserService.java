package com.yf.system.modules.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yf.ability.shiro.dto.SysUserLoginDTO;
import com.yf.base.api.api.dto.PagingReqDTO;
import com.yf.system.modules.user.dto.request.*;
import com.yf.system.modules.user.dto.response.UserListRespDTO;
import com.yf.system.modules.user.entity.SysUser;

import java.util.List;
import java.util.Date;

/**
 * <p>
 * 管理用户业务类
 * </p>
 *
 * @author 聪明笨狗
 * @since 2020-04-13 16:57
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 用户详情
     *
     * @param id
     * @return
     */
    SysUserSaveReqDTO detail(String id);

    /**
     * 分页查询数据
     *
     * @param reqDTO
     * @return
     */
    IPage<UserListRespDTO> paging(PagingReqDTO<SysUserQueryReqDTO> reqDTO);

    /**
     * 删除用户
     *
     * @param ids
     */
    void delete(List<String> ids);

    /**
     * 用户登录请求类
     *
     * @param reqDTO
     * @return
     */
    SysUserLoginDTO login(SysUserLoginReqDTO reqDTO);

    /**
     * 获取管理会话
     *
     * @param token
     * @return
     */
    SysUserLoginDTO token(String token);

    /**
     * 退出登录
     *
     * @param token
     */
    void logout(String token);

    /**
     * 修改用户资料
     *
     * @param reqDTO
     */
    SysUserLoginDTO update(SysUserUpdateReqDTO reqDTO);

    /**
     * 修改密码
     * @param reqDTO
     */
    void pass(SysUserPassReqDTO reqDTO);

    /**
     * 保存添加系统用户
     *
     * @param reqDTO
     */
    void save(SysUserSaveReqDTO reqDTO);

    /**
     * 手机号码注册
     *
     * @param reqDTO
     * @return
     */
    SysUserLoginDTO reg(UserRegReqDTO reqDTO);

    /**
     * 审核员工注册申请。
     */
    void auditRegistration(UserRegistrationAuditReqDTO reqDTO);

    /**
     * 修改账号状态并撤销受影响用户的活动会话。
     */
    void changeState(com.yf.base.api.api.dto.BaseStateReqDTO reqDTO);

    /**
     * 撤销指定用户的活动会话，确保角色或状态变更立即生效。
     */
    void invalidateSessions(List<String> userIds);

    /**
     * 为已验证的候选人签发不超过考核截止时间的普通系统会话。
     */
    SysUserLoginDTO loginCandidate(String userId, Date expireAt);
}
