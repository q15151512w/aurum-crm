package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 附件 DO，对应表 {@code sys_attachment}。
 *
 * <p>记录「哪个业务的哪个单据挂了哪个 OSS 对象」：
 * <ul>
 *     <li>{@code file_path} 存的是 **对象 key**（如 {@code crm/product/2026/09/xxx.png}），
 *         它是删除对象、排查问题时要用的稳定标识</li>
 *     <li>{@code file_url} 存的是**公网可访问 URL**（bucket 公共读），前端直接用它</li>
 * </ul>
 * 两者都存是有意的：只存 URL 则改域名/CDN 后老数据全部失效，只存 key 则前端每次都要再拼一次。
 *
 * <p><b>注意</b>：本表<b>没有</b> {@code is_active} 生成列（那是带业务唯一键的表才需要的），
 * 所以不要照抄 {@code crm_product} 的写法去声明它。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_attachment")
public class AttachmentDO extends BaseDO {

    /** 附件ID */
    @TableId
    private Long attachmentId;

    /** 业务类型（product / contract / customer / invoice ...），对应 {@code sys_attachment.biz_type} */
    private String bizType;

    /** 业务ID（对应业务表主键） */
    private Long bizId;

    /** 原始文件名（展示给人看，不参与存储路径） */
    private String fileName;

    /** 存储路径（OSS 对象 key） */
    private String filePath;

    /** 访问 URL */
    private String fileUrl;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型（MIME，如 image/png） */
    private String fileType;

    /** 附件分类：1 合同扫描件 2 发票PDF 3 营业执照 4 其他（字典 crm_attachment_type） */
    private Integer attachmentType;

    /** 上传人ID */
    private Long uploadBy;
}
