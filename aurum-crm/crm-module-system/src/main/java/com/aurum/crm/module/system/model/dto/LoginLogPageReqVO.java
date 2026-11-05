package com.aurum.crm.module.system.model.dto;

import com.aurum.crm.mybatis.core.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 登录日志分页查询。
 *
 * <p><b>前端契约</b>（{@code views/system/log/login.vue}，已固定，不要改名）：
 * {@code ProList} 会恒定带上 {@code pageNo} / {@code pageSize} / {@code keyword}，
 * 外加一个 {@code scope=all}（16 个列表页共用的组件行为）与请求拦截器附加的 {@code _t} 时间戳。
 *
 * <p>⚠️ 这里**故意不声明** {@code scope} 与 {@code _t}：Spring 的 bean 绑定会静默忽略
 * 未声明的查询参数，正好就是「未知参数不筛选」。声明了反而要维护一个永远不生效的分支
 * （与 {@code UnitPageReqVO} 同款处理）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "登录日志分页查询")
public class LoginLogPageReqVO extends PageParam {

    /**
     * 关键字，同时匹配「登陆人」（{@code sys_user.real_name}）、「工号」（{@code username}）与 IP。
     *
     * <p>与前端搜索框占位符「登陆人/工号/IP」一致。空串等于不筛。
     */
    @Schema(description = "关键字：登陆人 / 工号 / IP（空串等于不筛）", example = "admin")
    private String keyword;
}
