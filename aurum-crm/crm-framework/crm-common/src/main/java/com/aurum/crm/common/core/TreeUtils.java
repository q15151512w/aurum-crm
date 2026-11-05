package com.aurum.crm.common.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 扁平列表 → 树。菜单树、部门树共用。
 *
 * <p>放在 crm-common 而不是某个业务模块：菜单树（system 模块）和部门树（同样是 system 模块）
 * 都要用，而将来线索/客户的组织树也会用到 —— 这属于「与业务无关的技术工具」。
 *
 * <h3>三条必须守住的规则</h3>
 *
 * <ol>
 *     <li><b>父节点不在集合里的，提升为根，绝不丢弃。</b>
 *         典型场景是「角色只被授权了子菜单、没授权父目录」——
 *         丢掉的话那条菜单就从界面上凭空消失了，而且没人会报错</li>
 *     <li><b>先全量排序再顺序挂接</b>，等价于每一层都排好序；
 *         不用在递归里再排一遍</li>
 *     <li><b>用 Map 定位父节点，单遍完成</b>（O(n log n)，排序主导）——
 *         对每个节点去 List 里找父亲会退化成 O(n²)</li>
 * </ol>
 *
 * @param <T>  节点类型（DO 或 VO）
 * @param <ID> 主键类型
 */
public final class TreeUtils {

    /** 树的最大深度，防脏数据成环（如 A→B→A）导致死循环 */
    private static final int MAX_DEPTH_GUARD = 64;

    /**
     * 把扁平列表组装成树。
     *
     * @param flat       扁平节点列表（顺序无所谓，内部会排序）
     * @param idOf       取节点主键
     * @param parentIdOf 取父节点主键
     * @param childrenOf 取子节点列表（用于挂接）—— 该列表必须已被初始化为**可变**集合
     * @param setChildren 设置子节点列表（挂接后回写）
     * @param comparator 同级排序规则；传 {@code null} 表示保持输入顺序
     * @param rootParentId 视为「根」的父 ID（通常是 0）
     * @return 根节点列表
     */
    public static <T, ID> List<T> build(
            List<T> flat,
            Function<T, ID> idOf,
            Function<T, ID> parentIdOf,
            Function<T, List<T>> childrenOf,
            BiConsumer<T, List<T>> setChildren,
            Comparator<T> comparator,
            ID rootParentId) {

        List<T> result = new ArrayList<>();
        if (flat == null || flat.isEmpty()) {
            return result;
        }

        List<T> sorted = new ArrayList<>(flat);
        if (comparator != null) {
            sorted.sort(comparator);
        }

        // 单遍：先建索引，再挂接
        Map<ID, T> byId = new LinkedHashMap<>(sorted.size() * 2);
        for (T node : sorted) {
            byId.put(idOf.apply(node), node);
        }

        // 防御：深度超过阈值说明数据成环了，直接当扁平列表返回，至少不丢数据
        if (exceedsMaxDepth(byId, idOf, parentIdOf, rootParentId)) {
            return sorted;
        }

        for (T node : sorted) {
            ID parentId = parentIdOf.apply(node);
            boolean isRoot = parentId == null || parentId.equals(rootParentId);
            T parent = isRoot ? null : byId.get(parentId);

            if (parent == null) {
                // 根节点，或者父节点不在集合里（孤儿）—— 提升为根，不丢
                result.add(node);
            } else {
                List<T> children = childrenOf.apply(parent);
                if (children == null) {
                    children = new ArrayList<>();
                    setChildren.accept(parent, children);
                }
                children.add(node);
            }
        }
        return result;
    }

    /**
     * 从任一节点向上走，看是否会超过 {@link #MAX_DEPTH_GUARD} 层。
     *
     * <p>只沿一条链走就够了：若存在环，任何落在环上的节点都会走不到头。
     * 不必对每个节点都做一遍。
     */
    private static <T, ID> boolean exceedsMaxDepth(
            Map<ID, T> byId, Function<T, ID> idOf, Function<T, ID> parentIdOf, ID rootParentId) {
        for (T start : byId.values()) {
            int depth = 0;
            T cur = start;
            while (cur != null && depth++ < MAX_DEPTH_GUARD) {
                ID pid = parentIdOf.apply(cur);
                if (pid == null || pid.equals(rootParentId)) {
                    break;
                }
                cur = byId.get(pid);
            }
            if (depth >= MAX_DEPTH_GUARD) {
                return true;
            }
        }
        return false;
    }

    private TreeUtils() {
    }
}
