package com.aurum.crm.module.crm.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 线索列表 / 详情响应，列与原型表格对齐：
 * 线索名称 | 联系人 | 联系电话 | 负责人 | 行业 | 来源 | 创建时间 | 操作。
 */
@Data
@Schema(description = "线索响应")
public class LeadRespVO {

    /**
     * 前端表格主键别名（值与 {@code leadId} 相同）。
     *
     * <p>前端通用列表组件用 {@code row.id} 做行键：详情跳转
     * （{@code ProList.openDetail}）、勾选后的批量操作载荷（{@code useTable.selectedIds}）
     * 都读它。缺了它详情会跳到 {@code /crm/lead/undefined}、批量操作会发出 {@code undefined}。
     */
    @Schema(description = "线索ID（前端列表主键，与 leadId 相同）")
    private Long id;

    @Schema(description = "线索ID")
    private Long leadId;

    @Schema(description = "线索编号")
    private String leadNo;

    /** 前端表格列别名（值与 {@code leadName} 相同）：列表第一列的 prop 是 {@code name}。 */
    @Schema(description = "线索名称（公司/客户名，前端列表列 prop，与 leadName 相同）")
    private String name;

    @Schema(description = "线索名称（公司/客户名）")
    private String leadName;

    @Schema(description = "联系人姓名")
    private String contactName;

    @Schema(description = "职位")
    private String position;

    @Schema(description = "联系电话（非授权角色脱敏）")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "微信")
    private String wechat;

    @Schema(description = "所属行业（字典 crm_industry）")
    private Integer industry;

    @Schema(description = "线索来源（字典 crm_source）")
    private Integer source;

    @Schema(description = "客户级别（字典 crm_customer_level，与客户模块同一套字典）")
    private Integer level;

    @Schema(description = "省")
    private String province;

    @Schema(description = "市")
    private String city;

    @Schema(description = "区县")
    private String district;

    /**
     * 省市区拼接串（派生字段，前端列表「地区」列 + 编辑弹窗回填级联选择器）。
     *
     * <p>库里没有这一列，由 {@code LeadConvert} 的 {@code @AfterMapping} 从上面三段拼出来。
     */
    @Schema(description = "地区拼接串，如 广东省广州市天河区")
    private String region;

    @Schema(description = "详细地址")
    private String address;

    @Schema(description = "负责人ID（NULL=在线索池）")
    private Long ownerUserId;

    @Schema(description = "负责人姓名")
    private String ownerName;

    @Schema(description = "当前登录人是否已关注该线索")
    private Boolean starred;

    @Schema(description = "归属部门ID")
    private Long deptId;

    @Schema(description = "进入线索池时间")
    private LocalDateTime enterPoolTime;

    @Schema(description = "进入线索池原因编码（字典 crm_pool_reason）")
    private Integer enterPoolReasonCode;

    @Schema(description = "进入线索池原因备注")
    private String enterPoolRemark;

    @Schema(description = "前归属人ID")
    private Long prevOwnerId;

    /**
     * 前归属人姓名（线索池列表的「放入人」列）。
     *
     * <p>DO 里只有 {@code prev_owner_id}，姓名要跨表补 —— 与 {@code ownerName} 一样由
     * Service 批量填充。导出行的 {@code LeadPoolExportRowVO.prevOwnerName} 早就这么做了，
     * 列表侧漏了，于是「放入人」列一直是空的。
     */
    @Schema(description = "前归属人姓名（放入人）")
    private String prevOwnerName;

    @Schema(description = "下次联系时间（前端列表列 prop）")
    private LocalDate nextContactTime;

    @Schema(description = "最后跟进时间")
    private LocalDateTime lastFollowTime;

    @Schema(description = "转化时间")
    private LocalDateTime convertTime;

    @Schema(description = "转化生成的客户ID")
    private Long convertedCustomerId;

    @Schema(description = "备注")
    private String remark;

    /**
     * 创建人姓名（前端列表「创建人」列）。
     *
     * <p>DO 里只有审计字段 {@code create_by}（用户ID），姓名要跨表补 ——
     * 与 {@code ownerName} 同样由 Service 批量填充，不要在 Convert 里做（会 N+1）。
     */
    @Schema(description = "创建人姓名")
    private String creator;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
