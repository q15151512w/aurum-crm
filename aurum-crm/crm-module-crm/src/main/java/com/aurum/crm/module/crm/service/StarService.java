package com.aurum.crm.module.crm.service;

import java.util.Collection;
import java.util.Set;

/**
 * 关注 Service：通用「关注 / 取消关注」，不绑定具体业务对象。
 *
 * <p>接口刻意只收 {@code (bizType, bizId)} 两个裸参数，因此客户 / 商机 / 报价单 /
 * 合同 / 回款 / 发票要接入关注时，只需在自己的 Service 里加一行委托
 * （如 {@code starService.star(StarBizType.CUSTOMER, id)}）与两个 Controller 端点，
 * 不需要新表、新 Service、新错误码。
 *
 * <p>写入语义都是<b>幂等</b>的：重复关注不报错，取消一个没关注过的也不报错。
 *
 * <p>实现类：{@link com.aurum.crm.module.crm.service.impl.StarServiceImpl}。
 */
public interface StarService {

    /**
     * 关注（幂等：已关注则直接返回，不重复写库）。
     *
     * @param bizType 业务类型，取值见 {@link com.aurum.crm.module.crm.model.StarBizType}
     * @param bizId   业务ID
     */
    void star(int bizType, Long bizId);

    /**
     * 取消关注（幂等：未关注则影响 0 行，不报错）。
     */
    void unstar(int bizType, Long bizId);

    /**
     * 当前登录人是否已关注该业务对象（详情页单条用）。
     */
    boolean isStarred(int bizType, Long bizId);

    /**
     * 批量取「给定对象里当前登录人已关注的那部分」（列表回填用）。
     *
     * @param bizIds 待判定的业务ID集合；为空返回空集，不查库
     * @return 已关注的业务ID集合
     */
    Set<Long> starredBizIds(int bizType, Collection<Long> bizIds);

    /**
     * 当前登录人关注的该类业务的所有业务ID（{@code scope=starred} 过滤用）。
     *
     * <p>与 {@link #starredBizIds(int, Collection)} 是两个不同的查询：
     * 那个是「这批 id 里哪些被关注了」，这个是「我关注的全部 id」。
     *
     * @return 业务ID集合；没关注过任何一条时返回空集
     */
    Set<Long> starredBizIds(int bizType);
}
