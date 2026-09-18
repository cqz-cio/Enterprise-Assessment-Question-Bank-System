package com.yf.system.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yf.ability.Constant;
import com.yf.ability.auth.AuthRateLimiter;
import com.yf.ability.captcha.service.CaptchaService;
import com.yf.ability.redis.service.RedisService;
import com.yf.ability.shiro.dto.SysUserLoginDTO;
import com.yf.ability.shiro.jwt.JwtUtils;
import com.yf.ability.shiro.service.ShiroUserService;
import com.yf.base.api.api.ApiError;
import com.yf.base.api.api.dto.PagingReqDTO;
import com.yf.base.api.exception.ServiceException;
import com.yf.base.utils.BeanMapper;
import com.yf.base.utils.CacheKey;
import com.yf.base.utils.jackson.JsonHelper;
import com.yf.base.utils.passwd.PassHandler;
import com.yf.base.utils.passwd.PassInfo;
import com.yf.system.modules.config.enums.FuncSwitch;
import com.yf.system.modules.config.service.CfgSwitchService;
import com.yf.system.modules.menu.service.SysMenuService;
import com.yf.system.modules.role.entity.SysRole;
import com.yf.system.modules.user.UserUtils;
import com.yf.system.modules.user.dto.request.*;
import com.yf.system.modules.user.dto.response.UserListRespDTO;
import com.yf.system.modules.user.entity.SysUser;
import com.yf.system.modules.user.enums.SysRoleId;
import com.yf.system.modules.user.enums.SysUserId;
import com.yf.system.modules.user.enums.UserState;
import com.yf.system.modules.user.mapper.SysUserMapper;
import com.yf.system.modules.user.service.SysUserRoleService;
import com.yf.system.modules.user.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 语言设置 服务实现类
 * </p>
 *
 * @author 聪明笨狗
 * @since 2020-04-13 16:57
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService, ShiroUserService {

    private final SysUserRoleService sysUserRoleService;

    private final RedisService redisService;

    private final CaptchaService captchaService;

    private final CfgSwitchService cfgSwitchService;

    private final SysMenuService sysMenuService;

    private final JwtUtils jwtUtils;

    private final AuthRateLimiter authRateLimiter;


    @Override
    public SysUserSaveReqDTO detail(String id) {

        // 基础信息复制
        SysUser user = this.getById(id);
        SysUserSaveReqDTO respDTO = new SysUserSaveReqDTO();
        BeanMapper.copy(user, respDTO);

        // 角色是要
        List<SysRole> roleList = sysUserRoleService.listRoles(user.getId());
        List<String> roles = new ArrayList<>();
        for (SysRole role : roleList) {
            roles.add(role.getId());
        }
        respDTO.setRoles(roles);

        // 清理掉密码
        respDTO.setPassword(null);
        respDTO.setSalt(null);

        return respDTO;
    }

    @Override
    public IPage<UserListRespDTO> paging(PagingReqDTO<SysUserQueryReqDTO> reqDTO) {
        List<String> roles = UserUtils.getRoles();
        if (roles != null && roles.contains(SysRoleId.HR) && !roles.contains(SysRoleId.ADMIN)) {
            SysUserQueryReqDTO params = reqDTO.getParams();
            if (params == null) {
                params = new SysUserQueryReqDTO();
                reqDTO.setParams(params);
            }
            params.setRoleIds(List.of(SysRoleId.EMPLOYEE));
        }
        return baseMapper.paging(reqDTO.toPage(), reqDTO.getParams());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(List<String> ids) {

        // 超级用户可以删除任何用户
        if(!SysUserId.ADMIN.equals(UserUtils.getUserId())){
            int count = sysUserRoleService.countWithLevel(ids, UserUtils.getRoleLevel());
            if (count < ids.size()) {
                throw new ServiceException("删除错误，可能存在越权操作！");
            }
        }

        if (ids.contains(UserUtils.getUserId())) {
            throw new ServiceException("您不可以删除自己的账号！");
        }

        // 移除数据
        this.removeByIds(ids);
    }

    @Override
    public SysUserLoginDTO login(SysUserLoginReqDTO reqDTO) {

        if (!captchaService.checkCaptcha(reqDTO.getCaptchaKey(), reqDTO.getCaptchaValue())) {
            throw new ServiceException("图形验证码不正确或已失效，请刷新重试！");
        }

        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(SysUser::getUserName, reqDTO.getUserName());
        SysUser user = this.getOne(wrapper, false);

        // Use the stored user ID so database-equivalent account spellings share one limit.
        String identity = user == null ? StringUtils.trimToEmpty(reqDTO.getUserName()).toLowerCase(java.util.Locale.ROOT) : user.getId();
        String throttleKey = authRateLimiter.check("login-account", identity, 10, 600);
        SysUserLoginDTO response = this.checkAndLogin(user, reqDTO.getPassword());
        authRateLimiter.clear(throttleKey);
        return response;
    }


    /**
     * 用户登录校验
     *
     * @param user
     */
    private SysUserLoginDTO checkAndLogin(SysUser user, String password) {

        if (user == null || StringUtils.isBlank(password)
                || !PassHandler.checkPass(password, user.getSalt(), user.getPassword())) {
            throw new ServiceException("账号或密码不正确！");
        }
        if (UserState.DISABLED.equals(user.getState())) throw new ServiceException(ApiError.ERROR_90010005);
        if (UserState.AUDIT.equals(user.getState())) throw new ServiceException(ApiError.ERROR_90010006);

        return this.setToken(user);
    }

    @Override
    public List<String> permissions(String userId) {
        return sysUserRoleService.findUserPermission(userId);
    }

    @Override
    public List<String> roles(String userId) {
        return sysUserRoleService.listRoleIds(userId);
    }

    @Override
    public SysUserLoginDTO token(String token) {

        String username;
        try {
            username = jwtUtils.getVerifiedUsername(token);
        } catch (Exception e) {
            throw new ServiceException("会话失效，请重新登录！");
        }

        Map<String, Object> json = redisService.getJson(Constant.USER_NAME_KEY + username);
        if (json == null) {
            throw new ServiceException(ApiError.ERROR_10010002);
        }

        SysUserLoginDTO session = JsonHelper.parseObject(json, SysUserLoginDTO.class);
        if (session == null || !sameToken(token, session.getToken())) {
            throw new ServiceException(ApiError.ERROR_10010002);
        }

        SysUser current = this.getById(session.getId());
        if (current == null || !UserState.NORMAL.equals(current.getState())
                || !username.equals(current.getUserName())) {
            redisService.del(Constant.USER_NAME_KEY + username);
            throw new ServiceException(ApiError.ERROR_10010002);
        }

        return session;
    }

    @CacheEvict(value = CacheKey.TOKEN, key = "#token")
    @Override
    public void logout(String token) {
        try {
            String username = jwtUtils.getVerifiedUsername(token);
            String key = Constant.USER_NAME_KEY + username;
            Map<String, Object> json = redisService.getJson(key);
            SysUserLoginDTO session = json == null ? null : JsonHelper.parseObject(json, SysUserLoginDTO.class);
            if (session != null && sameToken(token, session.getToken())) {
                redisService.del(key);
            }
        } catch (Exception e) {
            log.debug("忽略无效 token 的退出请求");
        }
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public SysUserLoginDTO update(SysUserUpdateReqDTO reqDTO) {


        // 更新用户资料
        SysUser user = this.getById(UserUtils.getUserId());
        BeanMapper.copy(reqDTO, user);

        // 修改标识
        boolean reLogin = false;

        // 修改密码
        String password = reqDTO.getPassword();
        if (!StringUtils.isBlank(password)) {
            PassInfo passInfo = PassHandler.buildPassword(password);
            user.setPassword(passInfo.getPassword());
            user.setSalt(passInfo.getSalt());
            reLogin = true;
        }

        // 重新登录
        if (reLogin) {
            // 退出登录
            String[] keys = new String[]{Constant.USER_NAME_KEY + user.getUserName()};
            redisService.del(keys);
        }

        // 更新信息
        this.updateById(user);

        return this.setToken(user);
    }

    @Override
    public void pass(SysUserPassReqDTO reqDTO) {

        // 旧密码不能与新密码一致
        boolean same = reqDTO.getOldPass().equals(reqDTO.getNewPass());
        if(same){
            throw new ServiceException("新密码不能与旧密码一样！");
        }

        // 获取当前用户
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .select(SysUser::getId, SysUser::getPassword, SysUser::getSalt)
                .eq(SysUser::getId, UserUtils.getUserId());
        SysUser user = this.getOne(wrapper, false);

        // 旧密码不对
        boolean check = PassHandler.checkPass(reqDTO.getOldPass(), user.getSalt(), user.getPassword());
        if (!check) {
            throw new ServiceException(ApiError.ERROR_90010007);
        }

        PassInfo passInfo = PassHandler.buildPassword(reqDTO.getNewPass());
        user.setPassword(passInfo.getPassword());
        user.setSalt(passInfo.getSalt());
        this.updateById(user);

    }

    @CacheEvict(value = CacheKey.MENU, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void save(SysUserSaveReqDTO reqDTO) {

        List<String> roles = reqDTO.getRoles();

        if (CollectionUtils.isEmpty(roles)) {
            throw new ServiceException(ApiError.ERROR_90010003);
        }

        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(SysUser::getUserName, reqDTO.getUserName());
        if (!StringUtils.isBlank(reqDTO.getId())) {
            wrapper.lambda().ne(SysUser::getId, reqDTO.getId());
        }


        long count = this.count(wrapper);
        if (count > 0) {
            throw new ServiceException("用户名不能重复！");
        }


        // 保存基本信息
        SysUser user;

        // 添加模式
        if (StringUtils.isBlank(reqDTO.getId())) {
            user = new SysUser();
            BeanMapper.copy(reqDTO, user);
            user.setId(IdWorker.getIdStr());
        } else {
            user = this.getById(reqDTO.getId());
            BeanMapper.copy(reqDTO, user);
        }

        if (StringUtils.isBlank(user.getAvatar())) {
            user.setAvatar(SysUser.DEFAULT_AVATAR);
        }

        // 级别
        int level = sysUserRoleService.findMaxLevel(reqDTO.getId());
        if (level > UserUtils.getRoleLevel()) {
            throw new ServiceException("越级操作，不能操作等级高的用户！");
        }


        // 修改密码
        if (!StringUtils.isBlank(reqDTO.getPassword())) {
            PassInfo pass = PassHandler.buildPassword(reqDTO.getPassword());
            user.setPassword(pass.getPassword());
            user.setSalt(pass.getSalt());
        }

        // 保存角色信息
        sysUserRoleService.saveRoles(user.getId(), roles, true);

        // 保存绑定关系
        this.saveOrUpdate(user);

        // 用户资料或角色被管理端修改后，旧会话中的授权信息必须失效。
        this.invalidateSessions(List.of(user.getId()));

    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public SysUserLoginDTO reg(UserRegReqDTO reqDTO) {


        boolean check = captchaService.checkCaptcha(reqDTO.getCaptchaKey(), reqDTO.getCaptchaValue());
        if (!check) {
            throw new ServiceException("图形验证码不正确或已失效！");
        }


        // 功能开关
        boolean on = cfgSwitchService.isOn(FuncSwitch.USER_REG);
        if (!on) {
            throw new ServiceException("管理员未开启用户注册！");
        }


        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .select(SysUser::getId)
                .eq(SysUser::getUserName, reqDTO.getUserName());

        // 用户名即为手机号
        boolean exists = this.count(wrapper) > 0;
        if (exists) {
            throw new ServiceException("用户名已存在，换一个吧！！");
        }

        QueryWrapper<SysUser> employeeNoWrapper = new QueryWrapper<>();
        employeeNoWrapper.lambda()
                .select(SysUser::getId)
                .eq(SysUser::getEmployeeNo, reqDTO.getEmployeeNo().trim());
        if (this.count(employeeNoWrapper) > 0) {
            throw new ServiceException("员工工号已存在！");
        }

        return this.saveAndLogin(
                null,
                reqDTO.getUserName(),
                reqDTO.getDeptCode(),
                reqDTO.getRealName(),
                SysRoleId.EMPLOYEE,
                reqDTO.getMobile(),
                "",
                reqDTO.getPassword(),
                reqDTO.getEmployeeNo(),
                reqDTO.getEmail());
    }


    /**
     * 保存用户并自动登录
     *
     * @param userName
     * @param deptCode
     * @param realName
     * @param mobile
     * @param avatar
     * @param password
     * @return
     */
    private SysUserLoginDTO saveAndLogin(String userId, String userName, String deptCode, String realName,
                                         String role, String mobile, String avatar, String password,
                                         String employeeNo, String email) {

        // 保存用户
        SysUser user = new SysUser();

        // 指定用户ID的
        if (!StringUtils.isBlank(userId)) {
            user.setId(userId);
        } else {
            user.setId(IdWorker.getIdStr());
        }

        // 指定部门
        boolean on = cfgSwitchService.isOn(FuncSwitch.USER_DEPT_TYPE);
        if (on) {
            deptCode = cfgSwitchService.val(FuncSwitch.USER_DEPT_CODE);
        }

        // 企业员工自由注册后固定进入待审核状态，不能由通用开关绕过。
        user.setState(UserState.AUDIT);

        user.setUserName(userName);
        user.setRealName(realName);
        user.setDeptCode(deptCode);
        user.setMobile(StringUtils.trimToNull(mobile));
        user.setEmail(StringUtils.trimToNull(email));
        user.setEmployeeNo(employeeNo.trim());
        user.setAvatar(StringUtils.defaultIfBlank(avatar, SysUser.DEFAULT_AVATAR));
        PassInfo passInfo = PassHandler.buildPassword(password);
        user.setPassword(passInfo.getPassword());
        user.setSalt(passInfo.getSalt());

        // 保存角色
        List<String> roleList = new ArrayList<>();
        if (!StringUtils.isBlank(role)) {
            roleList.add(role);
        } else {
            roleList.add(SysRoleId.EMPLOYEE);
        }


        // 保存角色
        sysUserRoleService.saveRoles(user.getId(), roleList, false);

        // 保存用户
        this.save(user);

        return this.setToken(user);
    }


    /**
     * 保存会话信息
     *
     * @param user
     * @return
     */
    private SysUserLoginDTO setToken(SysUser user) {
        return setToken(user, null);
    }

    private SysUserLoginDTO setToken(SysUser user, Date maxExpiresAt) {

        // 获取一个用户登录的信息
        String key = Constant.USER_NAME_KEY + user.getUserName();
        String json = redisService.getString(key);
        if (!StringUtils.isBlank(json)) {
            // 删除旧的会话
            redisService.del(key);
        }

        SysUserLoginDTO respDTO = new SysUserLoginDTO();
        BeanMapper.copy(user, respDTO);

        // 正常状态才登录
        if (UserState.NORMAL.equals(user.getState())) {

            // 根据用户生成Token
            String token = jwtUtils.sign(user.getUserName(), maxExpiresAt);
            respDTO.setToken(token);

            // 添加角色信息
            this.fillRoleData(respDTO);

            // 权限表，用于前端控制按钮
            List<String> permissions = sysMenuService.listPermissionByRoles(respDTO.getRoles());
            respDTO.setPermissions(permissions);


            // 保存如Redis
            long ttlSeconds = jwtUtils.remainingSeconds(token);
            if (ttlSeconds <= 0 || !redisService.set(key, JsonHelper.toJson(respDTO), ttlSeconds)) {
                throw new ServiceException("登录会话保存失败，请稍后重试！");
            }
        }

        return respDTO;

    }

    @Override
    public SysUserLoginDTO loginCandidate(String userId, Date expireAt) {
        SysUser user = this.getById(userId);
        if (user == null || !UserState.NORMAL.equals(user.getState())
                || !sysUserRoleService.listRoleIds(userId).equals(List.of(SysRoleId.CANDIDATE))) {
            throw new ServiceException("考核信息不存在或凭证错误！");
        }
        return setToken(user, expireAt);
    }


    /**
     * 追加用户角色信息
     *
     * @param respDTO
     */
    private void fillRoleData(SysUserLoginDTO respDTO) {

        // 角色是要
        List<SysRole> roleList = sysUserRoleService.listRoles(respDTO.getId());
        // 角色级别
        Integer roleLevel = 0;
        // 数据权限1最小：查看自己的数据
        Integer dataScope = 1;

        List<String> roleIds = new ArrayList<>();
        for (SysRole role : roleList) {
            // 角色ID
            roleIds.add(role.getId());
            // 替换大的权限
            if (dataScope < role.getDataScope()) {
                dataScope = role.getDataScope();
            }
            // 权限级别
            if (roleLevel < role.getRoleLevel()) {
                roleLevel = role.getRoleLevel();
            }
        }
        respDTO.setRoleLevel(roleLevel);
        respDTO.setDataScope(dataScope);
        respDTO.setRoles(roleIds);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void auditRegistration(UserRegistrationAuditReqDTO reqDTO) {
        if (!"APPROVE".equals(reqDTO.getAction()) && !"REJECT".equals(reqDTO.getAction())) {
            throw new ServiceException("审核动作只允许 APPROVE 或 REJECT！");
        }
        SysUser user = this.getById(reqDTO.getUserId());
        if (user == null || !UserState.AUDIT.equals(user.getState())) {
            throw new ServiceException("待审核员工不存在或状态已变化！");
        }

        List<String> roles = sysUserRoleService.listRoleIds(user.getId());
        if (!roles.contains(SysRoleId.EMPLOYEE) || roles.contains(SysRoleId.ADMIN)
                || roles.contains(SysRoleId.HR) || roles.contains(SysRoleId.CANDIDATE)) {
            throw new ServiceException("只能审核员工注册申请！");
        }

        boolean approve = "APPROVE".equals(reqDTO.getAction());
        user.setState(approve ? UserState.NORMAL : UserState.DISABLED);
        user.setAuditBy(UserUtils.getUserId());
        user.setAuditTime(new Date());
        user.setAuditRemark(StringUtils.trimToNull(reqDTO.getRemark()));
        this.updateById(user);

        // 审核通过时重置为唯一员工角色，避免注册链路夹带其他角色。
        if (approve) {
            sysUserRoleService.saveRoles(user.getId(), List.of(SysRoleId.EMPLOYEE), false);
        }
        this.invalidateSessions(List.of(user.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void changeState(com.yf.base.api.api.dto.BaseStateReqDTO reqDTO) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .in(SysUser::getId, reqDTO.getIds())
                .ne(SysUser::getUserName, "admin");

        List<SysUser> affectedUsers = this.list(wrapper);

        SysUser record = new SysUser();
        record.setState(reqDTO.getState());
        this.update(record, wrapper);
        this.invalidateSessions(affectedUsers.stream().map(SysUser::getId).toList());
    }

    @Override
    public void invalidateSessions(List<String> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return;
        }
        List<SysUser> users = this.listByIds(userIds);
        for (SysUser user : users) {
            if (StringUtils.isNotBlank(user.getUserName())) {
                redisService.del(Constant.USER_NAME_KEY + user.getUserName());
            }
        }
    }

    static boolean sameToken(String supplied, String cached) {
        if (supplied == null || cached == null) {
            return false;
        }
        return MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                cached.getBytes(StandardCharsets.UTF_8));
    }
}
