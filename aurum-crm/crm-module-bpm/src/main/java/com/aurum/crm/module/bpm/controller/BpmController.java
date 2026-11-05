package com.aurum.crm.module.bpm.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.bpm.model.SysApprovalFlowDO;
import com.aurum.crm.module.bpm.model.SysApprovalNodeDO;
import com.aurum.crm.module.bpm.model.dto.FlowSaveReqVO;
import com.aurum.crm.module.bpm.model.dto.NodeSaveBodyReqVO;
import com.aurum.crm.module.bpm.model.dto.NodeSaveReqVO;
import com.aurum.crm.module.bpm.model.vo.ApprovalInstanceRespVO;
import com.aurum.crm.module.bpm.model.vo.ApprovalTaskRespVO;
import com.aurum.crm.module.bpm.service.ApprovalEngine;
import com.aurum.crm.module.bpm.service.ApprovalFlowService;
import com.aurum.crm.module.bpm.service.ApprovalResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审批流 Controller，严格对齐接口文档八节 `/bpm` 的 11 个端点：
 *
 * <pre>
 *   GET    /bpm/flow                       模板列表
 *   POST   /bpm/flow                       新增模板
 *   PUT    /bpm/flow/{id}                  修改模板
 *   DELETE /bpm/flow/{id}                  删除模板
 *   GET    /bpm/flow/{id}/nodes            节点列表
 *   PUT    /bpm/flow/{id}/nodes            保存节点配置
 *   GET    /bpm/task/todo                  我的待办
 *   GET    /bpm/task/done                  我的已办
 *   POST   /bpm/task/{id}/approve          审批（通过/驳回）
 *   GET    /bpm/instance                   我发起的审批
 *   GET    /bpm/instance/{bizType}/{bizId} 按单据查审批进度
 * </pre>
 *
 * <p>模板/节点管理端点未加 {@code @SaCheckPermission}（文档八节未给权限码，且暂不新增
 * 两段式权限码——见文档九节「新增权限点一律写成三段式」的约定）；登录即可访问。
 * 业务对象自身的提交/审批入口仍由各模块调用 {@link ApprovalEngine} 完成，本控制器
 * 负责模板配置管理与通用待办/进度查询。
 */
@Tag(name = "审批流")
@RestController
@RequestMapping("/bpm")
@Validated
public class BpmController {

    @Resource
    private ApprovalFlowService approvalFlowService;
    @Resource
    private ApprovalEngine approvalEngine;

    // ---------- 模板 /bpm/flow ----------

    @SaCheckLogin
    @Operation(summary = "审批流模板列表")
    @GetMapping("/flow")
    public CommonResult<PageResult<SysApprovalFlowDO>> flowPage(
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize) {
        return CommonResult.success(approvalFlowService.pageQuery(pageNo, pageSize));
    }

    @SaCheckLogin
    @Operation(summary = "新增审批流模板")
    @PostMapping("/flow")
    public CommonResult<Long> flowCreate(@RequestBody FlowSaveReqVO reqVO) {
        return CommonResult.success(approvalFlowService.create(reqVO));
    }

    @SaCheckLogin
    @Operation(summary = "修改审批流模板")
    @PutMapping("/flow/{id}")
    public CommonResult<Void> flowUpdate(@PathVariable Long id, @RequestBody FlowSaveReqVO reqVO) {
        approvalFlowService.update(id, reqVO);
        return CommonResult.success(null);
    }

    @SaCheckLogin
    @Operation(summary = "删除审批流模板")
    @DeleteMapping("/flow/{id}")
    public CommonResult<Void> flowDelete(@PathVariable Long id) {
        approvalFlowService.delete(id);
        return CommonResult.success(null);
    }

    @SaCheckLogin
    @Operation(summary = "模板节点列表")
    @GetMapping("/flow/{id}/nodes")
    public CommonResult<List<SysApprovalNodeDO>> nodes(@PathVariable Long id) {
        return CommonResult.success(approvalFlowService.nodesOfFlow(id));
    }

    @SaCheckLogin
    @Operation(summary = "保存节点配置")
    @PutMapping("/flow/{id}/nodes")
    public CommonResult<Void> nodesSave(@PathVariable Long id, @RequestBody NodeSaveBodyReqVO body) {
        List<NodeSaveReqVO> nodes = body == null ? null : body.getNodes();
        approvalFlowService.saveNodes(id, nodes);
        return CommonResult.success(null);
    }

    // ---------- 任务 /bpm/task ----------

    @SaCheckLogin
    @Operation(summary = "我的待办")
    @GetMapping("/task/todo")
    public CommonResult<List<ApprovalTaskRespVO>> todo() {
        return CommonResult.success(approvalEngine.toTaskVOs(approvalEngine.myTodoTasks()));
    }

    @SaCheckLogin
    @Operation(summary = "我的已办")
    @GetMapping("/task/done")
    public CommonResult<List<ApprovalTaskRespVO>> done() {
        return CommonResult.success(approvalEngine.toTaskVOs(approvalEngine.myDoneTasks()));
    }

    @SaCheckLogin
    @Operation(summary = "审批（通过/驳回）")
    @PostMapping("/task/{id}/approve")
    public CommonResult<ApprovalResult> approve(@PathVariable Long id, @RequestBody ApproveBody body) {
        return CommonResult.success(approvalEngine.auditTask(id, body.getResult(), body.getComment()));
    }

    // ---------- 实例 /bpm/instance ----------

    @SaCheckLogin
    @Operation(summary = "我发起的审批")
    @GetMapping("/instance")
    public CommonResult<List<ApprovalInstanceRespVO>> myInstances() {
        return CommonResult.success(approvalEngine.toInstanceVOs(approvalEngine.myInstances()));
    }

    @SaCheckLogin
    @Operation(summary = "按业务单据查审批进度")
    @GetMapping("/instance/{bizType}/{bizId}")
    public CommonResult<ApprovalInstanceRespVO> instanceByBiz(@PathVariable String bizType,
                                                              @PathVariable Long bizId) {
        var instance = approvalEngine.latestInstance(bizType, bizId);
        if (instance == null) {
            return CommonResult.success(null);
        }
        return CommonResult.success(approvalEngine.toInstanceVOs(List.of(instance)).get(0));
    }

    /** 审批请求体。 */
    public static class ApproveBody {
        private Integer result;
        private String comment;

        public Integer getResult() {
            return result;
        }

        public void setResult(Integer result) {
            this.result = result;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }
    }
}
