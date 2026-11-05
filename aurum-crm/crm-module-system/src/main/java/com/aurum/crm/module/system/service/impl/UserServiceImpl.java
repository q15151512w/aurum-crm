package com.aurum.crm.module.system.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.excel.ExcelKit;
import com.aurum.crm.common.excel.SpreadsheetXml;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.system.core.AuthConstants;
import com.aurum.crm.module.system.core.DisplayEnumTranslator;
import com.aurum.crm.module.system.dal.dataobject.SysDeptDO;
import com.aurum.crm.module.system.dal.dataobject.SysRoleDO;
import com.aurum.crm.module.system.dal.dataobject.SysUserDO;
import com.aurum.crm.module.system.dal.dataobject.SysUserRoleDO;
import com.aurum.crm.module.system.dal.mysql.SysDeptMapper;
import com.aurum.crm.module.system.dal.mysql.SysRoleMapper;
import com.aurum.crm.module.system.dal.mysql.SysUserMapper;
import com.aurum.crm.module.system.dal.mysql.SysUserRoleMapper;
import com.aurum.crm.module.system.model.dto.UserPageReqVO;
import com.aurum.crm.module.system.model.dto.UserSaveReqVO;
import com.aurum.crm.module.system.model.dto.UserStatusReqVO;
import com.aurum.crm.module.system.model.dto.UserSuperiorReqVO;
import com.aurum.crm.module.system.model.vo.UserExportRowVO;
import com.aurum.crm.module.system.model.vo.UserResetPasswordRespVO;
import com.aurum.crm.module.system.model.vo.UserRespVO;
import com.aurum.crm.module.system.model.vo.UserSimpleRespVO;
import com.aurum.crm.module.system.service.OssStorageService;
import com.aurum.crm.module.system.service.UserService;
import com.aurum.crm.security.core.PasswordEncoder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 人员管理实现（接口文档 3.1）。
 *
 * <p><b>三层口径要分清</b>，这个类里三种同时存在，混了就出 bug：
 * <ol>
 *     <li><b>展示口径</b>（前端发的中文）：{@code gender} / {@code workStatus} / {@code accountStatus}
 *         —— 靠 {@link DisplayEnumTranslator} 转成码值</li>
 *     <li><b>查询口径</b>（数字码值）：分页的 {@code status}（1/2/3）</li>
 *     <li><b>存储口径</b>（DDL 列）：{@code sys_user.status} 一个列承载三态</li>
 * </ol>
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysRoleMapper sysRoleMapper;
    @Resource
    private SysDeptMapper sysDeptMapper;
    @Resource
    private SysUserRoleMapper sysUserRoleMapper;
    /** 展示口径 VO 的装配（中文转换 + 跨表补名）统一由它做，本类不重复实现 */
    @Resource
    private UserVoAssembler userVoAssembler;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private OssStorageService ossStorageService;

    /** 导出对象的 OSS 业务目录（与 crm 模块导出统一落在 export/ 下） */
    private static final String EXPORT_BIZ_TYPE = "export";

    /** 导出 Excel 的 Content-Type */
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 导出 SpreadsheetML（.xml）的 Content-Type */
    private static final String XML_CONTENT_TYPE = "application/vnd.ms-excel";

    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ========================================================================
    //  分页 / 详情
    // ========================================================================

    @Override
    public PageResult<UserRespVO> pageQuery(UserPageReqVO reqVO) {
        LambdaQueryWrapper<SysUserDO> wrapper = Wrappers.lambdaQuery();

        // 关键字：姓名 或 账号（接口文档 3.1）
        if (StringUtils.hasText(reqVO.getKeyword())) {
            String kw = reqVO.getKeyword().trim();
            wrapper.and(w -> w.like(SysUserDO::getRealName, kw).or().like(SysUserDO::getUsername, kw));
        }

        if (reqVO.getStatus() != null) {
            wrapper.eq(SysUserDO::getStatus, reqVO.getStatus());
        }

        // 部门过滤：文档要求「含子部门」，用 ancestors 前缀匹配出子部门集合再 in
        if (reqVO.getDeptId() != null) {
            Set<Long> deptIds = deptIdAndChildren(reqVO.getDeptId());
            wrapper.in(SysUserDO::getDeptId, deptIds);
        }

        // 只看「还没有任何角色」的账号 —— 注册后的待处理申请就落在这里
        if (Boolean.TRUE.equals(reqVO.getNoRole())) {
            Set<Long> assignedUserIds = sysUserRoleMapper
                    .selectList(Wrappers.<SysUserRoleDO>lambdaQuery())
                    .stream().map(SysUserRoleDO::getUserId).collect(Collectors.toSet());
            if (assignedUserIds.isEmpty()) {
                // 一个人都没分配过角色 —— 全部都是「无角色」，不必加 not in 条件
                log.debug("[人员分页] noRole=true 且尚无任何角色关联，返回全部用户");
            } else {
                wrapper.notIn(SysUserDO::getUserId, assignedUserIds);
            }
        }

        wrapper.orderByDesc(SysUserDO::getCreateTime);

        Page<SysUserDO> page = sysUserMapper.selectPage(reqVO, wrapper);
        List<UserRespVO> list = userVoAssembler.toRespList(page.getRecords());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public UserRespVO get(Long id) {
        return userVoAssembler.toResp(getUserDO(id));
    }

    @Override
    public List<UserSimpleRespVO> simpleList(UserPageReqVO reqVO) {
        Page<SysUserDO> page = sysUserMapper.selectPage(reqVO, buildSelectorWrapper(reqVO));
        return userVoAssembler.toSimpleList(page.getRecords());
    }

    @Override
    public List<UserSimpleRespVO> options(UserPageReqVO reqVO) {
        // ⚠️ 这里【不能】用 selectPage —— 见 UserService#options 的注释：
        //    PageParam 默认 pageSize=10，而下拉请求不带分页参数，
        //    结果就是下拉里只有前 10 个人，排在后面的人「凭空消失」且无任何报错。
        return userVoAssembler.toSimpleList(sysUserMapper.selectList(buildSelectorWrapper(reqVO)));
    }

    /**
     * {@link #simpleList} 与 {@link #options} 共用的过滤条件。
     *
     * <p>抽出来是为了让「分页版」和「不分页版」的口径不可能漂移 ——
     * 两边各写一份的话，将来给下拉加个过滤条件时很容易只改一处。
     */
    private LambdaQueryWrapper<SysUserDO> buildSelectorWrapper(UserPageReqVO reqVO) {
        LambdaQueryWrapper<SysUserDO> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(reqVO.getKeyword())) {
            String kw = reqVO.getKeyword().trim();
            wrapper.and(w -> w.like(SysUserDO::getRealName, kw).or().like(SysUserDO::getUsername, kw));
        }
        return wrapper;
    }

    @Override
    public String export(UserPageReqVO reqVO) {
        List<UserExportRowVO> rows = buildExportRows(reqVO);
        byte[] bytes = ExcelKit.write(rows, UserExportRowVO.class, "人员");
        String objectKey = ossStorageService.upload(bytes, EXPORT_BIZ_TYPE, XLSX_CONTENT_TYPE, "xlsx");
        return ossStorageService.buildUrl(objectKey);

    }

    @Override
    public String exportXml(UserPageReqVO reqVO) {
        List<UserExportRowVO> rows = buildExportRows(reqVO);
        byte[] bytes = SpreadsheetXml.write(rows, UserExportRowVO.class, "人员");
        String objectKey = ossStorageService.upload(bytes, EXPORT_BIZ_TYPE, XML_CONTENT_TYPE, "xml");
        return ossStorageService.buildUrl(objectKey);
    }

    /** 导出用：复用 pageQuery 过滤条件查数并为导出行补名 + 中文化 */
    private List<UserExportRowVO> buildExportRows(UserPageReqVO reqVO) {
        // 复用与 pageQuery 完全一致的过滤条件，但不翻页：导出取全部结果
        LambdaQueryWrapper<SysUserDO> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(reqVO.getKeyword())) {
            String kw = reqVO.getKeyword().trim();
            wrapper.and(w -> w.like(SysUserDO::getRealName, kw).or().like(SysUserDO::getUsername, kw));
        }
        if (reqVO.getStatus() != null) {
            wrapper.eq(SysUserDO::getStatus, reqVO.getStatus());
        }
        if (reqVO.getDeptId() != null) {
            Set<Long> deptIds = deptIdAndChildren(reqVO.getDeptId());
            wrapper.in(SysUserDO::getDeptId, deptIds);
        }
        if (Boolean.TRUE.equals(reqVO.getNoRole())) {
            Set<Long> assignedUserIds = sysUserRoleMapper
                    .selectList(Wrappers.<SysUserRoleDO>lambdaQuery())
                    .stream().map(SysUserRoleDO::getUserId).collect(Collectors.toSet());
            if (!assignedUserIds.isEmpty()) {
                wrapper.notIn(SysUserDO::getUserId, assignedUserIds);
            }
        }
        wrapper.orderByDesc(SysUserDO::getCreateTime);

        List<SysUserDO> users = sysUserMapper.selectList(wrapper);
        // 跨表补名 + 中文化统一走 UserVoAssembler（含批量查，避免 N+1）
        List<UserRespVO> respList = userVoAssembler.toRespList(users);

        return respList.stream().map(vo -> {
            UserExportRowVO row = new UserExportRowVO();
            row.setUsername(vo.getUsername());
            row.setName(vo.getName());
            row.setDeptName(vo.getDeptName());
            row.setRoleName(vo.getRoleName());
            row.setPhone(vo.getPhone());
            row.setEmail(vo.getEmail());
            row.setGender(vo.getGender());
            row.setWorkStatus(vo.getWorkStatus());
            row.setAccountStatus(vo.getAccountStatus());
            row.setCreateTime(vo.getCreateTime() == null ? "" : vo.getCreateTime().format(DATE_TIME_FMT));
            return row;
        }).collect(Collectors.toList());
    }

    // ========================================================================
    //  新增 / 修改 / 删除
    // ========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserSaveReqVO reqVO) {
        // 账号查重：走 ORM，@TableLogic 自动带 del_flag=0，
        // 与唯一键 uk_username(username, is_active)「只约束存活行」的语义一致
        if (sysUserMapper.exists(SysUserDO::getUsername, reqVO.getUsername())) {
            throw new ServiceException(ErrorCode.USER_USERNAME_EXISTS);
        }
        if (!StringUtils.hasText(reqVO.getPassword())) {
            // 新增必须给密码（前端表单里 password 的 required 是「新增必填、编辑可空」）
            throw new ServiceException(400, "新增人员必须设置登录密码");
        }

        SysUserDO user = new SysUserDO();
        user.setUsername(reqVO.getUsername().trim());
        user.setPassword(passwordEncoder.encode(reqVO.getPassword()));
        user.setRealName(reqVO.getName());
        user.setNickname(reqVO.getName());
        user.setDeptId(reqVO.getDeptId());
        user.setPhone(reqVO.getPhone());
        user.setEmail(reqVO.getEmail());
        user.setRemark(reqVO.getRemark());
        user.setGender(DisplayEnumTranslator.genderToCode(reqVO.getGender()));
        user.setStatus(DisplayEnumTranslator.inferStatus(reqVO.getWorkStatus(), reqVO.getAccountStatus()));

        sysUserMapper.insert(user);

        assignRoles(user.getUserId(), resolveRoleIds(reqVO));
        return user.getUserId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserSaveReqVO reqVO) {
        SysUserDO exists = getUserDO(id);

        // 改账号名要查重（前端在编辑态把 username 禁用了，但接口层不能依赖前端）
        if (StringUtils.hasText(reqVO.getUsername())
                && !Objects.equals(exists.getUsername(), reqVO.getUsername())
                && sysUserMapper.exists(SysUserDO::getUsername, reqVO.getUsername())) {
            throw new ServiceException(ErrorCode.USER_USERNAME_EXISTS);
        }

        SysUserDO update = new SysUserDO();
        update.setUserId(id);
        // ⚠️ username 刻意不更新：接口文档把账号视为登录凭据，建成后不改。
        //    若将来要放开，记得同步处理唯一键冲突
        update.setRealName(reqVO.getName());
        update.setNickname(reqVO.getName());
        update.setDeptId(reqVO.getDeptId());
        update.setPhone(reqVO.getPhone());
        update.setEmail(reqVO.getEmail());
        update.setRemark(reqVO.getRemark());
        update.setGender(DisplayEnumTranslator.genderToCode(reqVO.getGender()));

        // 表单里两个中文状态字段都为空时 inferStatus 会返回「正常」，
        // 但编辑时「没动状态」不该把禁用/离职的人改回正常 —— 所以只在表单确实传了状态时才覆盖
        if (StringUtils.hasText(reqVO.getWorkStatus()) || StringUtils.hasText(reqVO.getAccountStatus())) {
            update.setStatus(DisplayEnumTranslator.inferStatus(reqVO.getWorkStatus(), reqVO.getAccountStatus()));
        }

        // 密码留空 = 不改（前端表单的约定）
        if (StringUtils.hasText(reqVO.getPassword())) {
            update.setPassword(passwordEncoder.encode(reqVO.getPassword()));
        }

        sysUserMapper.updateById(update);

        // 传了角色才动角色关联，没传就保持原样（避免「编辑资料时把角色清空」）
        Set<Long> roleIds = resolveRoleIds(reqVO);
        if (!roleIds.isEmpty()) {
            assignRoles(id, roleIds);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getUserDO(id);
        // 先清角色关联再删人，否则 sys_user_role 里留下孤儿行 ——
        // 将来这个 user_id 被复用（或统计角色人数）时会算错
        sysUserRoleMapper.delete(Wrappers.<SysUserRoleDO>lambdaQuery().eq(SysUserRoleDO::getUserId, id));
        sysUserMapper.deleteById(id);
    }

    // ========================================================================
    //  三态 / 上级 / 重置密码
    // ========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, UserStatusReqVO reqVO) {
        getUserDO(id);
        Integer target = reqVO.getStatus();

        if (AuthConstants.USER_STATUS_NORMAL.equals(target) && !hasAnyRole(id)) {
            // 「有账号必有角色」的硬约束。少了它，注册出来的账号能被直接激活，
            // 变成一个登录进去什么都看不到的「幽灵账号」（接口文档 2.4 / 3.1）
            throw new ServiceException(ErrorCode.USER_NOT_ASSIGNED_ROLE);
        }

        SysUserDO update = new SysUserDO();
        update.setUserId(id);
        update.setStatus(target);
        sysUserMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setSuperior(Long id, UserSuperiorReqVO reqVO) {
        getUserDO(id);
        Long superiorId = reqVO.getSuperiorId();

        if (superiorId != null) {
            if (superiorId.equals(id)) {
                throw new ServiceException(ErrorCode.USER_CANNOT_BE_OWN_SUPERIOR);
            }
            // 上级必须存在（否则会写进去一个查不到的 userId，页面上显示成空）
            getUserDO(superiorId);
        }

        SysUserDO update = new SysUserDO();
        update.setUserId(id);
        update.setSuperiorId(superiorId);
        sysUserMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResetPasswordRespVO resetPassword(Long id) {
        getUserDO(id);

        // 8 位随机；用字母+数字混合，避免纯数字被轻易猜
        String raw = RandomUtil.randomString("abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789", 8);

        SysUserDO update = new SysUserDO();
        update.setUserId(id);
        update.setPassword(passwordEncoder.encode(raw));
        sysUserMapper.updateById(update);

        // 密码已改，把该用户的会话全部踢掉（沿用忘记密码那套做法）
        cn.dev33.satoken.stp.StpUtil.kickout(id);
        log.info("[重置密码] userId={} 已重置并踢下线全部会话", id);

        // ⚠️ 明文只在这一次响应里返回，之后库里只有 BCrypt 哈希，谁也再查不出来。
        //    务必提示管理员当场记下（见 UserResetPasswordRespVO 的说明）
        return new UserResetPasswordRespVO(raw);
    }

    // ========================================================================
    //  内部方法
    // ========================================================================

    private SysUserDO getUserDO(Long id) {
        SysUserDO user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new ServiceException(ErrorCode.USER_NOT_EXISTS);
        }
        return user;
    }

    /** 该用户有没有分配过角色（判「激活」能不能放行） */
    private boolean hasAnyRole(Long userId) {
        return sysUserRoleMapper.exists(SysUserRoleDO::getUserId, userId);
    }

    /**
     * 取出请求里的角色 ID 集合。
     *
     * <p>⚠️ 两个字段都要认：接口文档 3.1 写的是 {@code roleIds}（多选），
     * 而前端表单实际发的是 {@code roleId}（单选，选择器控件写回的 extraProp）。
     * 优先用 {@code roleIds}，没有再退回 {@code roleId}。
     */
    private Set<Long> resolveRoleIds(UserSaveReqVO reqVO) {
        Set<Long> ids = new LinkedHashSet<>();
        if (reqVO.getRoleIds() != null) {
            reqVO.getRoleIds().stream().filter(Objects::nonNull).forEach(ids::add);
        }
        if (ids.isEmpty() && reqVO.getRoleId() != null) {
            ids.add(reqVO.getRoleId());
        }
        return ids;
    }

    /**
     * 覆盖式设置用户的角色。
     *
     * <p>先删后插（而不是增量对比）—— 角色数量很少，全量重写更简单也不易出错。
     * 前提是调用方只在「确实要改角色」时才调它（见 {@link #update} 的判断）。
     */
    private void assignRoles(Long userId, Set<Long> roleIds) {
        sysUserRoleMapper.delete(Wrappers.<SysUserRoleDO>lambdaQuery().eq(SysUserRoleDO::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        // 校验角色存在，避免写进去一个查不到的 roleId
        long exists = sysRoleMapper.selectCount(Wrappers.<SysRoleDO>lambdaQuery().in(SysRoleDO::getRoleId, roleIds));
        if (exists != roleIds.size()) {
            throw new ServiceException(ErrorCode.ROLE_NOT_EXISTS);
        }
        for (Long roleId : roleIds) {
            SysUserRoleDO relation = new SysUserRoleDO();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            sysUserRoleMapper.insert(relation);
        }
    }

    /** 该部门 + 其全部子部门的 ID 集合 */
    private Set<Long> deptIdAndChildren(Long deptId) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(deptId);
        SysDeptDO dept = sysDeptMapper.selectById(deptId);
        if (dept == null) {
            return ids;
        }
        // ancestors 形如 "0,1"，本部门 2 的前缀就是 "0,1,2,"
        String ancestors = StringUtils.hasText(dept.getAncestors()) ? dept.getAncestors() : "0";
        String prefix = ancestors + "," + dept.getDeptId() + ",";
        sysDeptMapper.selectList(Wrappers.<SysDeptDO>lambdaQuery()
                        .likeRight(SysDeptDO::getAncestors, prefix))
                .forEach(d -> ids.add(d.getDeptId()));
        return ids;
    }

}
