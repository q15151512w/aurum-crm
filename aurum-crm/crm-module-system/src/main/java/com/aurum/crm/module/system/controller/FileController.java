package com.aurum.crm.module.system.controller;

import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.module.system.model.dto.FileUploadReqVO;
import com.aurum.crm.module.system.model.vo.AttachmentDownloadRespVO;
import com.aurum.crm.module.system.model.vo.FileUploadRespVO;
import com.aurum.crm.module.system.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 文件上传 Controller，对应《05-接口文档》3.7 附件。
 *
 * <p>产品图片的用法：前端先调本接口拿到 {@code url}，再把它作为普通字符串
 * 存进 {@code crm_product.image}（产品模块因此完全不需要感知 OSS）。
 *
 * <p><b>两个实现细节</b>：
 * <ol>
 *     <li>文件参数用 {@code @RequestParam(required = false)} 而不是 {@code @RequestPart}：
 *         前者在漏传字段时得到 {@code null}，由业务校验返回**明确的 400**；
 *         后者会抛 {@code MissingServletRequestPartException}，而全局异常处理器
 *         没有对应分支，会退化成 500「服务器内部错误」</li>
 *     <li>不加 {@code @SaCheckPermission}：{@code sys_menu} 里没有文件上传的权限点，
 *         且鉴权体系尚未落地，加了会导致所有请求 403。建议后续补
 *         {@code system:file:upload} 权限点后再加注解</li>
 * </ol>
 */
@Tag(name = "文件上传")
@RestController
@RequestMapping("/system/file")
@Validated
public class FileController {

    @Resource
    private FileService fileService;

    @Operation(summary = "上传文件到 OSS")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<FileUploadRespVO> upload(
            @Parameter(description = "文件本体（form-data 字段名固定为 file）")
            @RequestParam(value = "file", required = false) MultipartFile file,
            @Valid @ModelAttribute FileUploadReqVO reqVO) {
        return CommonResult.success(fileService.upload(file, reqVO));
    }

    /**
     * 下载附件。
     *
     * <p>刻意返回 {@code ResponseEntity<Resource>}（二进制流）而不是 {@code CommonResult}：
     * 前端 `downloadFile()` 用 {@code responseType: 'blob'} 接收。
     * 附件不存在时抛出的业务异常仍由全局处理器转成 {@code CommonResult} JSON。
     *
     * <p>⚠️ 返回类型这里写**全限定名**是必须的：本类已经导入了
     * {@code jakarta.annotation.Resource}（字段注入用，全项目统一风格），
     * 再导入 {@code org.springframework.core.io.Resource} 会导致同名类型引用不明确、直接编译失败。
     */
    @Operation(summary = "下载附件（后端流式转发，不走 302 到 OSS）")
    @GetMapping("/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable("id") Long id) {
        AttachmentDownloadRespVO respVO = fileService.download(id);

        String contentType = StringUtils.hasText(respVO.getContentType())
                ? respVO.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        // 中文文件名必须用 filename*(UTF-8) 形式编码，否则浏览器会拿到乱码文件名
        String fileName = StringUtils.hasText(respVO.getFileName()) ? respVO.getFileName() : "download";

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString());
        if (StringUtils.hasText(respVO.getContentType())) {
            builder.contentType(MediaType.parseMediaType(contentType));
        }
        if (respVO.getFileSize() != null) {
            builder.contentLength(respVO.getFileSize());
        }
        return builder.body(respVO.getResource());
    }

    @Operation(summary = "删除附件（逻辑删除 + 尽力删除 OSS 对象）")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        fileService.delete(id);
        return CommonResult.success(true);
    }
}
