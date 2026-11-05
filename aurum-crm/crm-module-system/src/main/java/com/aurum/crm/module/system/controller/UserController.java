package com.aurum.crm.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.UserPageReqVO;
import com.aurum.crm.module.system.model.dto.UserSaveReqVO;
import com.aurum.crm.module.system.model.dto.UserStatusReqVO;
import com.aurum.crm.module.system.model.dto.UserSuperiorReqVO;
import com.aurum.crm.module.system.model.vo.UserResetPasswordRespVO;
import com.aurum.crm.module.system.model.vo.UserRespVO;
import com.aurum.crm.module.system.model.vo.UserSimpleRespVO;
import com.aurum.crm.module.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 人员管理（接口文档 3.1）。
 *
 * <p>⚠️ <b>这个模块的 URL 不在任何业务模块前缀下，也没有数据权限兜底</b>：
 * 数据权限拦截器的白名单（{@code crm_*} / {@code fin_*}）<b>不含 sys_user</b>，
 * 所以这里每个方法上的 {@code @SaCheckPermission} 是<b>唯一</b>的一道防线。
 * 漏标一个，任何登录用户都能用 curl 调它 —— 而人员管理能建账号、改角色、激活账号，
 * 漏一个就是提权漏洞。
 *
 * <p>权限标识全部取自 {@code sys_menu} 种子，不新增。
 */
@Tag(name = "人员管理")
@RestController
@RequestMapping("/system/user")
@Validated
public class UserController {

    @Resource
    private UserService userService;

    @SaCheckPermission("system:user:list")
    @Operation(summary = "人员分页列表（支持部门/状态/关键字/未分配角色过滤）")
    @GetMapping
    public CommonResult<PageResult<UserRespVO>> page(UserPageReqVO reqVO) {
        return CommonResult.success(userService.pageQuery(reqVO));
    }

    @SaCheckPermission("system:user:list")
    @Operation(summary = "人员详情")
    @GetMapping("/{id}")
    public CommonResult<UserRespVO> get(@PathVariable("id") Long id) {
        return CommonResult.success(userService.get(id));
    }

    /**
     * 人员精简列表（<b>分页</b>，需要 {@code system:user:list}）。
     *
     * <p>⚠️⚠️ <b>做「负责人 / 签约人」这类下拉时不要用这个端点，用下面的
     * {@link #options}。</b> 两个原因，都会让下拉静默出错：
     *
     * <ol>
     *     <li><b>它走分页。</b> {@code PageParam} 默认 {@code pageSize=10}，而下拉请求
     *         通常不带分页参数 —— 结果是<b>只返回前 10 个人，第 11 个往后的凭空消失</b>，
     *         且没有任何报错。这正是 BUG-026 的现象（「负责人选不了销售自己」）。
     *         2026-09-15 实测：库里 17 个人，本端点返回 10 条，{@code /options} 返回 17 条。</li>
     *     <li><b>权限边界。</b> 本端点要求 {@code system:user:list}，而该权限在种子里
     *         只授予了超管 —— 拿它当销售表单的下拉数据源，销售一打开表单就 403。
     *         这是 BUG-027。（2026-09-14 提交 {@code d3bcd8e} 曾把这行注解<b>删掉</b>，
     *         2026-09-15 发现后按 {@link #options} 的既定设计<b>恢复</b> ——
     *         见 BUG-031。）</li>
     * </ol>
     *
     * <p>本方法曾经是那三处下拉（选直属上级 / 选部门负责人 / 选角色人员）的数据源，
     * 现已全部迁到 {@code /options}。前端 {@code api/system.js#getUserSimpleList}
     * 目前<b>没有任何调用方</b>，也就是说这个端点已经闲置。
     *
     * <p>它没有被删掉，是因为将来可能有「带分页 + 带关键字搜索的人员挑选」场景
     * 确实需要它 —— 那种场景分页是对的。但<b>只要拿来当下拉数据源就一定会出事</b>，
     * 所以留在这里的是一句警告，而不是一个真空。
     */
    @SaCheckPermission("system:user:list")
    @Operation(summary = "人员精简列表（分页；下拉场景请用 /options）")
    @GetMapping("/simple-list")
    public CommonResult<List<UserSimpleRespVO>> simpleList(UserPageReqVO reqVO) {
        return CommonResult.success(userService.simpleList(reqVO));
    }

    /**
     * 员工选择器（<b>仅需登录，不校验权限点</b>）。
     *
     * <p><b>为什么必须单开一个端点</b>：上面的 {@code /simple-list} 要求
     * {@code system:user:list}，而该权限在种子里<b>只授予了超级管理员</b>。
     * 但「选负责人 / 选签约人」这类下拉出现在<b>线索、商机、报价单、合同</b>等表单里，
     * 这些表单是销售角色天天在用的 —— 于是他们一打开表单，下拉就因为 403 加载不出来，
     * 表现为「负责人选不了、也回显不出来」。
     *
     * <p>📌 <b>2026-09-15 插曲（已了结）</b>：提交 {@code d3bcd8e}（提交信息是
     * {@code docs:修复部分合同 产品bug}）曾把 {@code /simple-list} 上那行
     * {@code @SaCheckPermission} 删掉，使本段「保留原端点的严格性」一度与代码不符。
     * 实测确认后已按本段的设计<b>恢复该注解</b>，见 BUG-031。</p>
     *
     * <p>根因是权限点的<b>归属</b>错了：{@code /simple-list} 挂在系统模块的
     * 「人员管理」权限下，但它的用途是<b>全项目的通用人员选择器</b>
     * （见 {@code selectorRegistry.js} 的 {@code userSelector}，6 处复用）。
     * 拿它当选择器数据源，等于要求每个能用表单的人都先拥有「人员管理」权限。
     *
     * <p>为什么不直接放宽 {@code /simple-list}：那会让「人员管理」这个模块边界失效，
     * 而且列表页 {@code GET /system/user} 与它共用同一个权限点，改动影响面更大。
     * 这里保留原端点的严格性，另开一个语义明确的「选择器端点」——
     * 与 {@code /crm/customer/owner-options} 是同一个思路。
     *
     * <p><b>安全性</b>：返回的是精简字段（{@code id/name/username/phone/deptName}），
     * 不含密码、状态、角色等管理信息；且「登录」这一关仍由
     * {@code SaTokenConfig} 的全局拦截器把守（本类不写注解 ≠ 不需要登录）。
     * 对内部 CRM 来说，让任何在职员工查到同事的姓名/电话/部门是可接受的。
     */
    @Operation(summary = "员工选择器（仅需登录，供各业务表单的负责人/签约人下拉使用）")
    @GetMapping("/options")
    public CommonResult<List<UserSimpleRespVO>> options(UserPageReqVO reqVO) {
        // ⚠️ 必须走【不分页】的 options()。曾经错调 simpleList()，
        //    而 PageParam 默认 pageSize=10、下拉请求又不带分页参数 ——
        //    结果是下拉里只有前 10 个用户，第 11 个往后的人「凭空消失」，
        //    表现为「负责人选不了自己 / 选不了某个人」，且毫无报错。
        //    详见 UserService#options 的注释。
        return CommonResult.success(userService.options(reqVO));
    }

    @SaCheckPermission("system:user:list")
    @Operation(summary = "导出人员 Excel（上传 OSS，返回下载 URL）")
    @GetMapping("/export")
    public CommonResult<String> export(UserPageReqVO reqVO) {
        return CommonResult.success(userService.export(reqVO));
    }

    @SaCheckPermission("system:user:list")
    @Operation(summary = "导出人员 XML/SpreadsheetML（上传 OSS，返回下载 URL）")
    @GetMapping("/export-xml")
    public CommonResult<String> exportXml(UserPageReqVO reqVO) {
        return CommonResult.success(userService.exportXml(reqVO));
    }

    @SaCheckPermission("system:user:create")
    @Operation(summary = "新增人员")
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody UserSaveReqVO reqVO) {
        return CommonResult.success(userService.create(reqVO));
    }

    @SaCheckPermission("system:user:update")
    @Operation(summary = "修改人员")
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable("id") Long id,
                                        @Valid @RequestBody UserSaveReqVO reqVO) {
        userService.update(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("system:user:delete")
    @Operation(summary = "删除人员")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        userService.delete(id);
        return CommonResult.success(true);
    }

    /**
     * 激活 / 禁用 / 操作离职。
     *
     * <p>⚠️ 激活时后端会校验「该用户必须已有角色」，没有则返回 1106 ——
     * 这是「有账号必有角色」的兜底（接口文档 2.4 / 3.1）。
     */
    @SaCheckPermission("system:user:changeStatus")
    @Operation(summary = "激活 / 禁用 / 操作离职（激活前必须有角色）")
    @PutMapping("/{id}/status")
    public CommonResult<Boolean> changeStatus(@PathVariable("id") Long id,
                                              @Valid @RequestBody UserStatusReqVO reqVO) {
        userService.changeStatus(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("system:user:update")
    @Operation(summary = "设置直属上级")
    @PutMapping("/{id}/superior")
    public CommonResult<Boolean> setSuperior(@PathVariable("id") Long id,
                                             @RequestBody UserSuperiorReqVO reqVO) {
        userService.setSuperior(id, reqVO);
        return CommonResult.success(true);
    }

    /** ⚠️ 新密码只在这一次响应里返回，前端必须当场弹给管理员看 */
    @SaCheckPermission("system:user:resetPwd")
    @Operation(summary = "重置密码（返回后端生成的新密码）")
    @PostMapping("/{id}/reset-password")
    public CommonResult<UserResetPasswordRespVO> resetPassword(@PathVariable("id") Long id) {
        return CommonResult.success(userService.resetPassword(id));
    }
}
