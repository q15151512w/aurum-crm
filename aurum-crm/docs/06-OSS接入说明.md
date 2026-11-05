# OSS 接入说明

> | 项 | 内容 |
> | :--- | :--- |
> | 用途 | 说明各业务模块如何调用阿里云 OSS 上传文件 |
> | 能力位置 | `crm-module-system`（配置 + 封装 + 通用上传接口） |
> | 现有示例 | 产品图片 `POST /crm/product/image`（`crm-module-crm`） |
> | 编写日期 | 2026-09-13 |

---

## 一、配置（已就绪，不用改）

`crm-server/src/main/resources/application-dev.yml`：

```yaml
oss:
  endpoint: oss-cn-chengdu.aliyuncs.com
  accessKeyId: ${OSS_ACCESS_KEY_ID}
  accessKeySecret: ${OSS_ACCESS_KEY_SECRET}
  bucketName: qk-files
```

这 4 项是**必填**。以下 3 项有代码默认值，**不配也能跑**，需要时再加：

| 配置项 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `oss.dir` | `crm/` | 对象目录前缀；显式配成空表示不要前缀 |
| `oss.urlPrefix` | 自动拼 `https://{bucket}.{endpoint}` | bucket 绑了自定义域名/CDN 时才需要 |
| `oss.maxSizeMb` | `10` | 单文件大小上限；刻意小于 Spring 的 50MB，让超限返回 400 而不是 500 |

> **文件类型（扩展名）校验不在这里**，属于业务侧职责：哪个模块用哪种文件，就由那个模块自己白名单校验
> （例如产品图片只允许 jpg/jpeg/png/webp/gif，写在 `ProductServiceImpl` 里）。
> 放一个全局白名单只会让不同业务互相牵制，所以这一项已被移除。

配置类：`com.aurum.crm.module.system.config.OssProperties`；
客户端装配：`com.aurum.crm.module.system.config.OssConfig`（配置缺失时**启动即失败**，错误信息会指出缺哪一项）。

> ⚠️ **不要**在别的模块再写一个 `@ConfigurationProperties("oss")` —— 那会创建第二个 OSS 客户端，配置变成两处维护。

---

## 二、两种用法，按场景选

### 场景 A：只要一个文件 URL（单据上的单个图片/附件字段）

典型场景：产品图片、营业执照、发票 PDF 快照。

```java
@Resource
private OssStorageService ossStorageService;   // com.aurum.crm.module.system.service

public String uploadSomething(MultipartFile file) {
    // 第二个参数是业务类型，决定对象落在哪个目录：crm/{bizType}/{yyyy}/{MM}/{uuid}.{ext}
    String objectKey = ossStorageService.upload(file, "contract");
    return ossStorageService.buildUrl(objectKey);   // 这个 URL 直接存进单据字段
}
```

特点：
- **不写 `sys_attachment`**，没有数据库写入，因此也不需要失败补偿；
- 业务侧自己做校验（扩展名、大小），例如产品图片只允许 jpg/jpeg/png/webp/gif 且 ≤5MB；
- OSS 侧失败已经被封装转成 `500 文件上传失败，请稍后重试`，不用再包一层 try/catch。

### 场景 B：需要"附件列表"（详情页的「附件」Tab）

典型场景：客户/合同/回款的附件 Tab，要按单据查出一堆附件。

```java
@Resource
private FileService fileService;   // com.aurum.crm.module.system.service

FileUploadReqVO req = new FileUploadReqVO();
req.setBizType("contract");       // 业务类型
req.setBizId(contractId);         // 必须与 bizType 同时给，否则 400
req.setAttachmentType(1);         // 1合同扫描件 2发票PDF 3营业执照 4其他（缺省 4）
FileUploadRespVO resp = fileService.upload(file, req);   // 会自动写 sys_attachment
```

特点：
- **`bizType` 与 `bizId` 必须同时提供或同时不提供**（只给一个会 400，避免"以为落了库其实没落"）；
- 内置了「落库失败 → 删除已上传对象」的补偿，业务侧不用重复实现；
- 单据**还没保存、拿不到 ID** 时：要么先保存单据再上传（推荐），要么先用场景 A 传完拿 URL、保存后再单独写附件记录。

### 也可以直接调 HTTP 接口

前端（或不想引入依赖的调用方）可以直接用 system 模块的通用上传接口：

```http
POST /system/file/upload        # multipart/form-data，字段名 file
                                # 可选：bizType / bizId / attachmentType
```

---

## 三、业务模块要用 OSS，需要做什么

**第 1 步**：在该模块的 pom 里加依赖（哪个模块用就加在哪个模块）

```xml
<dependency>
    <groupId>com.aurum.crm</groupId>
    <artifactId>crm-module-system</artifactId>
    <version>${revision}</version>
</dependency>
```

**第 2 步**：注入 `OssStorageService`（只要 URL）或 `FileService`（要附件记录），按上面两种场景写。

> ⚠️ 这是**业务模块之间**的依赖。项目 README 写的依赖方向是
> `server → module-* → framework/*`，原来的代码（如 `crm` 模块里的 `SysUserMiniDO`）
> 都在刻意避免 `crm → system`。这里为了复用 OSS 配置与封装而引入，方向单一、无循环。
> 若以后要消除：把 `OssProperties` / `OssConfig` / `OssStorageService` 三个类挪到
> `crm-framework/crm-common`，然后各模块删掉这行依赖即可（改完各模块反而零依赖）。

---

## 四、三条不要做的事

1. **不要自己拼 OSS URL**（`"https://" + bucket + "." + endpoint + "/" + key`）——
   一律走 `buildUrl(objectKey)`。将来换 CDN 或自定义域名时只需要改一处；
   而且 endpoint 可能带 `https://` 前缀，手拼很容易拼出 `https://bucket.https://...`。
2. **不要让一个模块 HTTP 调另一个模块的上传接口** —— 自己调自己绕圈，还得自己带 token。
3. **不要把原始文件名当作对象 key** —— 中文、空格、超长、`../` 都会变成 OSS 上的疑难杂症。
   对象 key 由 `OssStorageService` 用 uuid 生成，原始文件名只存进 `sys_attachment.file_name`。

---

## 五、现有接口一览

| 接口 | 所在模块 | 说明 |
| :--- | :--- | :--- |
| `POST /system/file/upload` | system | 通用上传；传 `bizType`+`bizId` 会写 `sys_attachment` |
| `GET /system/file/{id}` | system | 下载附件（后端流式转发，附件不存在返回 `code:1401`） |
| `DELETE /system/file/{id}` | system | 删除附件：先逻辑删除库记录，再尽力删除 OSS 对象 |
| `POST /crm/product/image` | crm | 产品图片专用；固定传到 `crm/product/` 目录，限图片且 ≤5MB，不写附件表 |

### 产品图片的前端用法

```js
// 1) 选图后先上传，拿到 OSS URL
const { data } = await uploadApi('/crm/product/image', formData)   // formData: { file }
// 2) 把 URL 填进表单的 imageUrl 字段（字段名与前端表单一致）
form.imageUrl = data.url
// 3) 正常保存产品
await request.post('/crm/product', form)
```

---

## 六、排查手册

| 现象 | 原因 |
| :--- | :--- |
| 启动报「缺少 OSS 配置项 oss.xxx」 | `application-dev.yml` 缺该键，按提示补 |
| 上传报 500「文件上传失败」 | 看日志里 `[OSS] 上传失败` 的堆栈：常见是 AK 无权限（`AccessDenied`）、bucket 不存在（`NoSuchBucket`）、endpoint 与 bucket 不同区域 |
| 返回的 URL 打开是 403 | bucket 不是公共读，或对象被设了私有 ACL。当前设计依赖公共读；若改为私有，需要改成"后端签发签名 URL" |
| URL 打不开、内容不对 | 检查 `oss.urlPrefix` 是否配错、bucket 是否绑了自定义域名 |
| 前端渲染不出来 | 确认存进字段的是 `data.url`（完整 URL），而不是 `objectKey` |
| 对象删不掉/有孤儿对象 | 设计上**不自动删除**旧图片（同一 URL 可能被多处引用）。需要清理时按 `sys_attachment.file_path` 或 `crm_product.image` 里的 key 人工删 |
