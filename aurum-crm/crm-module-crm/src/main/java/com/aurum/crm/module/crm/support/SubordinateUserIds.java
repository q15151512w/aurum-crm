package com.aurum.crm.module.crm.support;

import com.aurum.crm.module.crm.mapper.SysUserMiniMapper;
import com.aurum.crm.module.crm.model.SysUserMiniDO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 「我」的全部下属 —— 沿 {@code sys_user.superior_id} 自顶向下逐层收集（多层，<b>不含自己</b>）。
 *
 * <p><b>为什么抽成共用机制</b>：列表页的「下属负责的 X」筛选胶囊在多个模块都要用同一套口径
 * （《05-接口文档》4.1 写死了语义：{@code owner_user_id IN (沿 sys_user.superior_id 逐级向下的全部下属，不含自己)}）。
 * 每个 Service 各写一份 BFS 的后果是：环守卫、层级上限、空集合守卫这三处只要有一处漏掉，
 * 就是「死循环」或「越权看到别人的数据」——都是很难在联调里发现的失败方式。
 * 线索（{@code LeadServiceImpl}）与客户（{@code CustomerServiceImpl}）里各有一份同构的私有实现，
 * 本类把口径固定在一处，商机与报价单先用它；那两个模块保持原样（在跑，不做无谓改动）。
 *
 * <p><b>三处必须保持的细节</b>（都是从既有实现里带过来的，改动前请先想清楚）：
 * <ol>
 *     <li>{@code visited} 先放入 {@code rootUserId}：一举两得 —— 既是<b>环守卫</b>
 *       （A→B→A 时剪断），又保证「下属<b>不含自己</b>」。</li>
 *     <li>循环条件保证 {@code frontier} 进 {@code IN} 查询前<b>永不为空</b> ——
 *       MyBatis-Plus 3.5.7 的 {@code in(col, empty)} 会生成非法的 {@code IN ()}，
 *       直接抛 SQL 语法错（本仓库其它 Service 在每次 {@code in} 前都做了 isEmpty 判断，同一原因）。</li>
 *     <li>返回<b>空列表</b>表示「没有下属」，调用方必须据此返回空页，
 *       <b>绝不能</b>把它当成「不加条件」或退化成本部门查询 —— 那是越权。</li>
 * </ol>
 *
 * <p>口径与 {@code LeadServiceImpl#listSubordinateUserIds} / {@code CustomerServiceImpl#listSubordinateUserIds}
 * 逐条对齐（含 {@link #MAX_SUBORDINATE_DEPTH} 的取值与超深截断的告警）。
 */
@Slf4j
@Component
public class SubordinateUserIds {

    /**
     * 最大遍历层数。
     *
     * <p>正常组织不会有 20 层；这个上限挡的是<b>脏数据造成的循环</b>
     * （{@code visited} 已经能剪断环，这里是第二道保险，避免任何异常数据把请求拖死）。
     */
    private static final int MAX_SUBORDINATE_DEPTH = 20;

    @Resource
    private SysUserMiniMapper sysUserMiniMapper;

    /**
     * 取 {@code rootUserId} 的全部下属（多层，不含自己）。
     *
     * @param rootUserId 起点用户 ID；为 {@code null} 时返回空列表（未登录 / 未取到 userId 的场景）
     * @return 下属用户 ID 列表（<b>有序</b>：按层级由近及远；已去重）；
     *         <b>空列表 = 没有下属</b>，调用方应据此返回空页
     */
    public List<Long> of(Long rootUserId) {
        if (rootUserId == null) {
            return List.of();
        }

        Set<Long> visited = new HashSet<>();
        visited.add(rootUserId); // 只占位，不进结果 → 下属不含自己；同时充当环守卫
        List<Long> result = new ArrayList<>();
        List<Long> frontier = List.of(rootUserId);

        for (int depth = 0; depth < MAX_SUBORDINATE_DEPTH && !frontier.isEmpty(); depth++) {
            List<SysUserMiniDO> children =
                    sysUserMiniMapper.selectList(SysUserMiniDO::getSuperiorId, frontier);
            List<Long> next = new ArrayList<>();
            for (SysUserMiniDO child : children) {
                if (child.getUserId() != null && visited.add(child.getUserId())) {
                    result.add(child.getUserId());
                    next.add(child.getUserId());
                }
            }
            frontier = next;
        }

        if (!frontier.isEmpty()) {
            // frontier 非空 = 循环是被层数上限打断的，说明有超过 20 层的链路（多半是脏数据）
            log.warn("[下属链] 层级超过 {} 层，已截断。rootUserId={}", MAX_SUBORDINATE_DEPTH, rootUserId);
        }
        return result;
    }
}
