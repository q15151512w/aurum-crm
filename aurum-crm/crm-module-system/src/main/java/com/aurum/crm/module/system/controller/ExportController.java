package com.aurum.crm.module.system.controller;

import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.system.service.OssStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 导出下载 Controller：把导出到 OSS 的对象经后端流式转发送回浏览器。
 *
 * <p>为什么各导出接口不能直接让前端 302 / fetch 到 OSS URL 下载：
 * <ol>
 *     <li><b>跨域文件名失控</b>：OSS 是公网跨域 URL，浏览器的 {@code <a download>}
 *         对跨域地址不生效，会退回用 URL 末尾的 uuid 当文件名（下载出来是
 *         {@code 762e6f31....xlsx}），体验差；</li>
 *     <li><b>OSS 侧 CORS 限制</b>：前端 fetch 跨域 OSS URL 依赖 bucket 开 CORS，
 *         本项目未配置，直接拉会失败。</li>
 * </ol>
 * 所以走「后端流式转发 + {@code Content-Disposition} 中文文件名」这条和
 * {@code /system/file/{id}} 附件下载一致的路：同源、文件名可控。
 *
 * <p>防 SSRF：只认本 bucket 前缀的 URL（{@code objectKeyOf} 已做校验），
 * 不把后端变成任意 URL 的代理。
 */
@Tag(name = "导出下载")
@RestController
@RequestMapping("/system/export")
@Validated
public class ExportController {

    @Resource
    private OssStorageService ossStorageService;

    @Operation(summary = "流式下载导出文件（后端转 OSS 对象，支持中文文件名）")
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(
            @RequestParam("url") String url,
            @RequestParam(value = "filename", required = false) String filename) {
        String objectKey = ossStorageService.objectKeyOf(url);
        if (objectKey == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "非法的下载地址");
        }

        InputStreamResource resource = new InputStreamResource(ossStorageService.openStream(objectKey));
        String name = StringUtils.hasText(filename) ? filename : "导出";
        String contentType = objectKey.endsWith(".xml")
                ? "application/vnd.ms-excel"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name + ".xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
