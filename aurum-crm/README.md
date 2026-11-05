# AurumCRM · 企业级智能 CRM 系统

基于 Spring Boot 3 + Vue 3 的前后端分离 CRM，覆盖 **线索 → 客户 → 商机 → 合同 → 回款/发票** 全流程（LTC）。

---

## 技术栈

| 层 | 选型 | 版本 |
| :--- | :--- | :--- |
| JDK | OpenJDK | 17 |
| 构建 | Maven | 3.9+ |
| 框架 | Spring Boot | 3.3.5 |
| 认证 | Sa-Token | 1.39.0 |
| ORM | MyBatis-Plus | 3.5.7 |
| 连接池 | Druid | 1.2.23 |
| 数据库 | MySQL | 8.0+（开发环境 8.4.5） |
| 缓存/锁 | Redis + Redisson | 7.2.3 / 3.32.0 |
| 接口文档 | Knife4j | 4.5.0 |
| 工具 | Hutool / MapStruct / Lombok | 5.8.32 / 1.6.2 / 1.18.38 |

> **为什么不用 Spring Security**：本项目只需要认证 + 权限校验，Sa-Token 的 API 更少更直观，答辩时能讲清楚每一行。Spring Security 的过滤器链在中小型项目里是负担。
>
> **为什么不用 Flowable**：BPMN 2.0 学习成本高、表结构 30+ 张难以讲清。业务只需线性审批，自研三层模型（模板 → 实例 → 任务）核心表 4 张。

---

## 模块结构

```
aurum-crm/
├── crm-framework/              技术框架层（与业务无关）
│   ├── crm-common/             统一响应、异常、登录用户上下文
│   ├── crm-mybatis/            BaseDO、分页、字段自动填充、数据权限拦截器
│   └── crm-security/           Sa-Token 封装、认证异常处理、密码加密
├── crm-module-system/          用户、角色、部门、菜单、字典、编号规则、日志
├── crm-module-crm/             线索、客户、商机、合同、产品
├── crm-module-finance/         回款、发票、核销
├── crm-module-bpm/             审批流引擎（自研）
└── crm-server/                 启动模块，聚合全部业务模块
```

依赖方向是**单向**的：`server → module-* → framework/* → common`。
`crm-mybatis` 不依赖 `crm-security`（避免循环），两者通过 `crm-common` 的
`LoginUserHolder` 传递登录信息。

### 分层与包结构（业务模块）

以 `crm-module-crm` 为例：

```
com.aurum.crm.module.crm/
├── controller/           REST 入口，只做参数校验与响应包装
├── service/              Service 接口（只放接口）
│   └── impl/             Service 实现类（@Service）
├── mapper/               只放 Mapper 接口（com.aurum.crm.**.mapper 被 @MapperScan 扫描）
└── model/                模型层
    ├── XxxDO.java        实体，对应一张表（@TableName / @TableId / BaseDO）
    ├── dto/              入参（XxxReqVO）
    ├── vo/               出参（XxxRespVO）
    └── convert/          MapStruct 转换器（componentModel = "spring"）
```

约定：

- **DO 不放 mapper 包**：`mapper` 包只放 Mapper 接口，实体统一在 `model` 包下。
- **Service 先接口后实现**：接口放 `service`，实现放 `service.impl`，Controller 注入接口类型。
- **包名与目录必须一致**：不要出现文件在 `model/dto` 却声明 `package ...crm.dto` 的情况。
- **`@Transactional` 写在实现类方法上**，不要写在接口上。
- `crm-module-system` 是骨架期建立的，沿用 `dal/dataobject` + `dal/mysql`，
  两类包都在 `MybatisPlusConfig` 的 `@MapperScan` 覆盖范围内。

---

## 文档索引

| # | 文档 | 内容 |
| :--- | :--- | :--- |
| 01 | [原型梳理](docs/01-原型梳理.md) | Axure 原型 107 页的结构分析、模块清单、接口开发优先级 |
| 02 | [建表文档](docs/02-建表文档.md) | 47 张表的设计规范、决策理由、索引策略、状态机 |
| 03 | [技术栈说明](docs/03-技术栈说明.md) | 前后端技术选型、选型决策记录、11 条踩坑记录、环境配置要点 |
| 04 | [权限设计文档](docs/04-权限设计文档.md) | 菜单/操作/数据/字段四层权限模型 |
| 05 | [接口文档](docs/05-接口文档.md) | 162 个接口定义、通用约定、前端遗漏清单 |
| 06 | [OSS接入说明](docs/06-OSS接入说明.md) | 阿里云 OSS 配置、通用上传接口与调用示例 |
| 07 | [测试BUG记录](docs/07-测试BUG记录.md) | 联调测试期缺陷的现象、根因、修复与验证 |
| 08 | [当前进度](docs/08-当前进度.md) | **现在到哪了**：模块完成度、验证状态、待办、环境要点（会过时，接口级完成度请以 Knife4j 为准）|

数据库脚本：

| 脚本 | 内容 |
| :--- | :--- |
| [crm.sql](sql/crm.sql) | **全量初始化脚本**，一条命令建好可运行的环境 |

`crm.sql` 分四部分，便于按需裁剪：

| 部分 | 内容 | 生产环境 |
| :--- | :--- | :--- |
| 一 | 表结构（47 张表） | 保留 |
| 二 | 基础数据（部门 / 角色 / 单位 / 编号规则 / 审批流 / 字典） | 保留 |
| 三 | 菜单与权限（126 条菜单 + 264 条授权） | 保留 |
| 四 | 演示账号与业务数据 | **可删除** |

> 第四部分末尾（4.10 / 4.11 节）还会补齐演示数据的审计字段 `create_by`，
> 并打印一段校验查询 —— 不补的话前端「创建人」列永远是 `--`，原因见
> [02-建表文档](docs/02-建表文档.md) 1.2 与 [07-测试BUG记录](docs/07-测试BUG记录.md) BUG-002。

> ⚠️ 脚本会先 `DROP` 再 `CREATE` 全部表，**请勿在生产环境执行**。

> `docs/archive/` 存放初版文档，仅供追溯，不是当前设计。

---

## 快速开始

### 1. 准备中间件

```bash
# MySQL（8.0+）—— 一条命令建好整个数据库
#   包含：47 张表 + 基础数据 + 菜单权限 + 演示数据
mysql -u root -p < sql/crm.sql

# Redis（无密码）
redis-server.exe redis.conf
redis-cli ping          # 应返回 PONG
```

### 演示账号

**所有账号密码统一为 `123456`**

| 账号 | 姓名 | 部门 | 角色 | 数据范围 | 能看到什么 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `admin` | 超级管理员 | 零捌集团 | 超级管理员 | 全部 | 所有数据 + 系统配置 |
| `director` | 王总监 | 销售部 | 销售总监 | 本部门及以下 | 销售部及下属三个组的客户 + 公海 |
| `sales01` | 张三 | 销售部 | 销售专员 | 仅本人 | 自己负责的客户 + 公海 |
| `sales02` | 李四 | 销售部 | 销售专员 | 仅本人 | 自己负责的客户 + 公海 |
| `finance` | 赵财务 | 财务部 | 财务 | 全部 | 客户 / 合同 / 回款 / 发票 |
| `sales03` | 王五 | 销售一组 | 销售专员 | 仅本人 | （暂无客户数据，用于演示组织层级）|
| `sales04` | 赵六 | 销售二组 | 销售专员 | 仅本人 | 同上 |
| `sales05` | 孙七 | 销售三组 | 销售专员 | 仅本人 | 同上 |
| `purchase01` | 刘采购 | 采购部 | 销售专员 ※ | 仅本人 | 人事/行政岗位的示例账号 |
| `purchase02` | 陈采购 | 采购二组 | 销售专员 ※ | 仅本人 | 同上 |
| `quality01` | 吴质检 | 质量部 | 销售专员 ※ | 仅本人 | 同上 |
| `warehouse01` | 郑仓管 | 仓运部 | 销售专员 ※ | 仅本人 | 同上 |
| `produce01` | 冯生产 | 生产部 | 销售专员 ※ | 仅本人 | 同上 |
| `finance02` | 陈会计 | 财务部 | 财务 | 全部 | 财务相关 |

> ※ 种子里只定义了 4 个角色（管理员 / 销售总监 / 销售专员 / 财务），
> 采购 / 质量 / 仓运 / 生产 这些岗位没有对应角色，示例账号统一先挂 `sales`（权限最小的普通员工角色）。
> 要更贴合需要另建角色并补 `sys_role_menu` 授权。
>
> **验证数据权限**：用 `sales01` 和 `sales02` 分别登录客户列表，
> 两人看到的客户完全不同（各 3 个私有 + 2 个公海）；用 `director`（本部门及以下）登录能看到全部 8 个。

### 2. 修改配置

编辑 `crm-server/src/main/resources/application-dev.yml`，确认数据库账号密码。

⚠️ **还要配邮件，否则注册 / 邮箱登录 / 找回密码都用不了**（会返回 `1009 验证码邮件发送失败`）：

```yaml
spring:
  mail:
    username: 你的邮箱@163.com
    password: 你的SMTP授权码     # ← 仓库里是占位符，必须自己填
```

授权码不是邮箱登录密码，获取路径：163 邮箱网页版 → 设置 → POP3/SMTP/IMAP →
开启 SMTP 服务 → 生成授权码。
（端口 `465` 配 `ssl.enable: true` 且 `starttls` 关；换 587 则反过来。）

### 3. 启动

```bash
mvn clean package -DskipTests
java -jar crm-server/target/crm-server.jar
```

或用 IDEA 直接运行 `CrmServerApplication`。

### 4. 验证

| 地址 | 预期 |
| :--- | :--- |
| http://localhost:8080/health | `{"code":0,...}` |
| http://localhost:8080/actuator/health | `db` 与 `redis` 均为 `UP` |
| http://localhost:8080/doc.html | Knife4j 接口文档 |

---

## 开发约定

### 统一响应格式

```json
{ "code": 0, "message": "操作成功", "data": { } }
```

**`code == 0` 表示成功**，非 0 表示业务失败，`message` 可直接展示给用户。

### HTTP 状态码约定 ⚠️

| 场景 | HTTP 状态 | body 的 code |
| :--- | :--- | :--- |
| 成功 | 200 | 0 |
| 业务规则拒绝 | 200 | 业务错误码（1000+） |
| 未登录 / Token 失效 | 200 | 401 |
| 权限不足 | 200 | 403 |
| 参数校验失败 | 200 | 400 |
| **未预期的服务端异常** | **500** | 500 |

**设计意图**：业务与认证错误统一走 HTTP 200 + body code，
前端 axios 拦截器只需判断 `code` 一处逻辑；
而真正的服务端故障保持 5xx，便于监控告警捕获。

> 前端拦截器需要同时处理这两种情况，别只判断 HTTP 状态码。

### 错误码分段

| 区间 | 模块 |
| :--- | :--- |
| 0 | 成功 |
| 400-599 | 通用（参数、认证、权限、服务端） |
| 1000-1999 | 系统模块（用户、角色、部门、字典） |
| 2000-2999 | 客户模块（线索、客户、商机、合同） |
| 3000-3999 | 财务模块（回款、发票） |
| 4000-4999 | 审批流 |

### 实体基类

业务 DO 继承 `BaseDO` 自动获得五个审计字段：

```java
@TableName("crm_customer")
public class CustomerDO extends BaseDO {
    @TableId
    private Long customerId;
    private String customerName;
}
```

- `createTime` / `updateTime` / `createBy` / `updateBy` 由 `DefaultDBFieldHandler` 自动填充，**业务代码不要手动赋值**
- `delFlag` 标了 `@TableLogic`，`DELETE` 自动转成逻辑删除，`SELECT` 自动追加 `del_flag = 0`
- **不要**把 `is_active` 加进 DO —— 它是数据库生成的列，只读

### 分页

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerPageReqVO extends PageParam {
    private String customerName;
}

// Service
Page<CustomerDO> page = customerMapper.selectPage(reqVO.toPage(), wrapper);
// Controller
return CommonResult.success(PageParam.toResult(page, CustomerConvert.INSTANCE::toVO));
```

### 登录用户上下文

```java
// 可能没登录的场景（定时任务、字段填充）
Long userId = LoginUserHolder.getUserId();

// 必须有登录的场景（业务代码）
LoginUser getUser = LoginUserHolder.getRequired();
```

> `LoginUserHolder` 由 `LoginUserInterceptor` 在请求进入时写入、
> 请求结束时**在 finally 中清理**。Tomcat 线程池复用线程，
> 不清会导致下一个请求读到上一个用户的身份 —— 这是真实发生过的越权事故。

---

## 待实现（按优先级）

> ⚠️ 这是一份**会变**的清单，只记「接下来要做的事」，不记「已经做到哪了」——
> 逐模块的接口状态请查 Knife4j（`/doc.html`）或《05-接口文档.md》，
> 别在这里维护完成度表格（曾经因为一份腐烂的进度表把排查者带偏过，见《03-技术栈说明》）。

| # | 内容 | 说明 |
| :--- | :--- | :--- |
| 1 | **审批流引擎** | `crm-module-bpm` 目前是**空模块**。合同的审核状态机是写死在 `ContractServiceImpl` 里的（直接用 `sys_approval_flow/node/instance/task` 四张表），回款审核、开票如果照抄一遍就是三份重复逻辑 —— 建议先把通用部分抽出来 |
| 2 | ~~**财务模块的写入侧**~~ | ✅ **已完成（2026-09-14）**：回款（登记 / 提交 / 审核 / 关注）与发票（登记 / 提交 / 开票 / **拒绝** / 作废 / 关注）的写入端点全部实现。仅接口文档 7.3 的**独立「核销」端点**未实现 —— 发票↔回款的关联已在保存发票时由 `fin_invoice_payment` 一并落库，该端点是否保留待定。⚠️ **待实机点验**：财务开票页的「确认开票 / 拒绝」 |
| 3 | ~~公海 / 线索池领取的并发控制~~ | ✅ **已不是待办（2026-09-14 核对代码后更正）**：`CustomerPoolServiceImpl#claim:161` 与 `LeadPoolServiceImpl#claim` **已经是正确的原子 CAS** —— `UPDATE ... WHERE xxx_id=? AND owner_user_id IS NULL` 然后判 `affected != 1` 抛「已被他人领取」。`assign` 与 `putToPool` 同样用了 CAS（`eq(owner, fromOwner)` 兼作并发保护）。**原文写的「没有任何锁，两个人能领到同一条」是错的。** 真正缺的是 `crm_pool_rule.claim_limit` 的**每日领取上限**没有被执行（是缺功能，不是竞态） |
| 4 | **编号规则引擎的接口层** | `BusinessNumberServiceImpl` 已经有了（行锁 + CAS，并发安全），但**没有 Controller** —— 「编号定义」页面的 4 个接口还是空的 |
| 5 | **组织机构与菜单的增删改** | `DeptController` / `MenuController` 目前都只有 `GET /tree` |
| 6 | **导出 / 导入** | ⚠️ **更正（2026-09-14）**：导出**已经铺开**（线索 / 线索池 / 客户 / 客户池 / 商机 / 报价单 / 合同 / 产品 / 产品分类 / 回款 / 发票 都有 `/export` 与 `/export-xml`），且实现方式是 **Excel → OSS → 返回下载 URL**，**不是 CSV**。真正还没有的是**导入**（如人员导入模板） |
| 7 | ~~真实短信服务商~~ | **已不再是待办**：身份验证已于 2026-09-14 从「手机号 + 短信」整体换成「邮箱 + 邮件验证码」，走真实 SMTP（163），验证码**不再打进日志**。原短信通道与 `SmsCodeService` 已删除 |
| 8 | ~~**按钮级权限**~~ | ✅ **已完成（2026-09-14）**：新增 `v-permission` 指令 + `ListToolbar` 按 `action.perm` 过滤，19 个列表页的 51 个工具栏按钮与 6 个详情页/行内按钮已接线（见后端 `docs/07` 的 BUG-017）。后端也补齐了 8 个 Controller 共 68 处 `@SaCheckPermission`（BUG-016）。⚠️ **遗留**：`v-permission` 只是界面友好，安全边界仍在后端；且下面这些页面的按钮接的是**不存在的后端端点**，本次刻意没加 `perm`（加了会把「接口缺失」伪装成「没权限」）—— 字典页（无 `DictController`）、编号规则页（无 `NumberingRuleController`）、部门增删改（`DeptController` 只有 `GET /tree`）、联系人、开票信息、线索转客户 |
| 9 | **种子数据的审计字段** | `sql/crm.sql` 的业务数据 INSERT 没有列 `create_by`，已由第四部分 4.10 节的 `UPDATE` 补齐；新增演示数据时记得同步处理，否则前端「创建人」列显示 `--` |

---

## 常见问题

**Q：启动报 `ERR AUTH <password> called without any password configured`**

Redis 没设密码，但配置里写了 `password:`（空值）。空值会被 Spring 绑定成
空字符串 `""`，Redisson 拿着它发 AUTH 命令就会报这个错。
**整行删掉**，不要留空值。

**Q：启动报 `Unsupported character encoding 'utf8mb4'`**

JDBC URL 的 `characterEncoding` 要填 **Java 字符集名 `UTF-8`**，
不是 MySQL 的 `utf8mb4`。Connector/J 8.x 会自动映射到服务端的 utf8mb4。

**Q：访问接口返回 500「服务器内部错误」而不是 401**

`NotLoginException` 被 `GlobalExceptionHandler` 的 `Exception` 兜底分支吃掉了。
`crm-security` 里的 `SecurityExceptionHandler` 用 `@Order(HIGHEST_PRECEDENCE)`
抢先处理，才有正确的 401。

**Q：Maven 报 `'dependencies.dependency.version' is missing`**

依赖版本统一在**根 POM 的 `<dependencyManagement>`** 里声明，
各模块引依赖时**不要写 `<version>`**。新增第三方依赖时改根 POM 一处即可。

**Q：编译报 `ExceptionInInitializerError` / `com.sun.tools.javac.code.TypeTag :: UNKNOWN`**

**Lombok 版本太旧，读不懂新版 JDK 的 javac 内部结构。** Lombok 作为注解处理器要往
编译器 AST 里插 getter/setter，必须读 javac 内部类 `TypeTag`，而该结构随 JDK 大版本变化。

本项目用 **Lombok 1.18.38**，实测在 JDK 17 / 21 / 25 下均正常；早期用 1.18.34 时在 JDK 25 下会报这个错。

> 这个报错常表现为「**命令行能跑、IDE 编译不过**」（反之亦然），因为两边可能用不同 JDK。
> 先 `java -version` 与 `mvn -version` 对比一下，比读代码快得多。

**Q：报 `... failed to transfer from ... during a previous attempt. This failure was cached in the local repository and resolution is not reattempted until the update interval ... has elapsed or updates are forced`**

**这是「失败被缓存」，不是「仓库里没有」。** Maven 把一次下载失败记进本地仓库，
在更新间隔内**不会自动重试** —— 所以网络恢复了它也不会好，看起来像一直坏着。

```bash
mvn -U compile     # -U = 强制重新解析，让 Maven 忘掉上次的失败
```

> ⚠️ 别用「删依赖 / 删 `target` / 清仓库」去治它，那是在解决一个不存在的问题。
> 报错原文最后一行其实已经写了解法（`Try to run Maven import with -U flag`），
> **先读完整段报错**。
>
> 反向的坑：**网络真断的时候不要加 `-U`**。`-U` 是「强制联网重新解析」，
> 网不通时它只会把本来还能用的本地缓存一起作废，让情况更糟。
> 判断方法：`nslookup maven.aliyun.com` 能不能解析出地址。

**Q：明明是联网装的依赖，为什么离线模式说 `has not been downloaded from it before`？本地仓库到底在哪？**

**先确认本地仓库的真实路径，别默认是 `~/.m2/repository`。** 本项目机器的 Maven
（`D:\JAVA\apache-maven-3.9.4`）在 `conf/settings.xml` 里把 `localRepository` 改成了：

```
D:\JAVA\apache-maven-3.9.4\mvn_cangku
```

```bash
mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout
```

> **排查依赖问题时最容易踩的坑**：跑去 `C:\Users\<你>\.m2\repository` 翻，
> 发现「jar 明明在啊」，然后开始怀疑 Maven 配置 —— 但那个目录**根本不是 Maven 在用的**。
> 项目中可能同时存在多个陈旧副本，`~/.m2` 很可能就是其中之一。
> **先跑上面那条命令拿到真实路径，再去看**。
>
> 另外每个 artifact 目录里的 `_remote.repositories` 记录了它**是从哪个仓库 id 下下来的**
> （如 `...jar>aliyun=`）。当解析走的是另一个 repo id 时，
> Maven 的 enhanced 本地仓库管理器会认为这份缓存「来源对不上」而拒绝使用 ——
> 这也能造成「文件在本地却说要下载」的假象。

**Q：发邮箱验证码报 `535 Error: authentication failed`，但授权码我核对过是对的**

**先去 163 网页版确认「SMTP 服务」是不是真的开启了**（设置 → POP3/SMTP/IMAP）。

⚠️ **163 在 SMTP 服务未开启时，对任何认证请求都返回 535** ——
这与「授权码填错」是**完全相同的错误码**。所以顺着报错去核对授权码会一直查不出来，
真正的原因在网页版的开关上。这个坑 2026-09-14 卡了整整一轮。

**四条最常踩的**：

| 现象 | 原因 |
| :--- | :--- |
| 535，授权码没错 | **SMTP 服务未开启**（本次的根因）|
| 535，服务已开启 | 授权码被**重置过** —— 每重置一次旧的立即失效；或复制时带了首尾空格 |
| 535，都没问题 | `username` 填的是**手机号**。163 用手机号注册的账号，地址是 `手机号@163.com`，SMTP 用户名必须是这个完整邮箱 |
| 535，以上都排除了 | ⚠️ **邮箱域名和 SMTP 主机不匹配**（见下）—— 最隐蔽的一条 |

**⚠️ 「网易邮箱」不一定就是 163.com —— 域名、host、username 三者必须严格对应：**

| 邮箱地址 | `spring.mail.host` | `spring.mail.username` |
| :--- | :--- | :--- |
| `<邮箱已脱敏>` | `smtp.163.com` | `<邮箱已脱敏>` |
| `xxx@126.com` | **`smtp.126.com`** | `xxx@126.com` |
| `xxx@yeah.net` | **`smtp.yeah.net`** | `xxx@yeah.net` |

163 / 126 / yeah.net **都是网易的邮箱**，很容易混。拿 `@126.com` 的账号去连
`smtp.163.com`，**报错就是同一个 535**，和「授权码错」完全无法区分。
**所以第一件事是去网页版确认自己邮箱的完整地址**（左上角或「设置 → 账号信息」）。

> 2026-09-14 二次踩坑时，`smtp.163.com` + `<邮箱已脱敏>` +
> 配置里的授权码，用 `AUTH LOGIN` 与 `AUTH PLAIN` 两种方式实测**都返回 535**，
> 而网络与 TLS 握手完全正常 —— 也就是说**这一条只能去账号侧查**，
> 代码侧没有任何可改的地方。

**排查顺序**（别先怀疑代码，先证明账号侧通不通）：

```bash
# 只做认证握手，不发信。把「授权码」换成你的
{ echo "EHLO t"; sleep 2; echo "AUTH LOGIN"; sleep 2;
  printf '%s' "<邮箱已脱敏>" | base64; sleep 2;
  printf '%s' "授权码" | base64; sleep 3; echo QUIT; } \
  | openssl s_client -connect smtp.163.com:465 -quiet 2>&1 | grep -E "235|535"
```

- `235 Authentication successful` → 账号侧没问题，是应用配置的问题（多半是没重启）
- `535` → 账号侧问题，回上面三条表里查

> ⚠️ **这个方法 2026-09-14 又用了一次，结果是 `535`** ——
> 也就是说账号侧（授权码 / 服务开关）发生了变化，
> 而**应用侧一切正常**：同样的请求走完全一样的路径，只在最后一步被 163 拒绝。
> 结论：**遇到这个报错，先跑上面这段，不要先改代码。**

**2026-09-14 补充：接口行为变了（前端提示会不一样）**

以前所有发信失败都返回 `1009「验证码邮件发送失败，请稍后重试」`——
但认证失败是**配置问题**，让用户「稍后重试」是误导（重试一万次也不会成功）。
现在认证失败单独返回：

| 错误码 | 含义 | 该做什么 |
| :--- | :--- | :--- |
| `1009` `EMAIL_SEND_FAILED` | 网络抖动 / 被限流等**可重试**的失败 | 稍后重试 |
| `1012` `EMAIL_SERVICE_AUTH_FAILED` | **认证失败（535）—— 配置问题** | 找管理员，别重试 |

同时后端日志里会直接打印**该去查哪四处**（服务开关 / 授权码 / 是否重置过 / username 是不是完整邮箱），
不用再靠猜。见 `EmailCodeServiceImpl#isAuthFailure`。

> 这一步的价值在于**把「代码问题」和「账号问题」一刀切开**。
> 否则会陷入「改配置 → 重启 → 再试 → 还是 535」的循环，而每次都只能看到同一个笼统的报错。

**Q：`/actuator/health` 变得很慢（十几秒），而且应用健康状态是 DOWN**

**Actuator 自动装配的邮件健康检查在捣鬼。** 引入 `spring-boot-starter-mail` 后，
Spring Boot 会自动注册 `MailHealthIndicator`，它每次被调用都真的去连一次 SMTP ——
SMTP 不通时既慢（实测 17.8 秒）又把整个应用判成 DOWN。

已在本项目关闭（`application.yml`）：

```yaml
management:
  health:
    mail:
      enabled: false
```

> **为什么该关**：邮件只是「注册 / 找回密码」这一个功能依赖它，不该拖垮整个服务的健康判定；
> 而且健康检查是给存活探针用的，不能让它去连一个外部第三方服务。
> 想确认邮件通不通，用「发一次验证码」这条真实链路，比健康检查准。

**Q：为什么数据库 / Redis / OSS / 邮箱的密码都直接写在 `application-dev.yml` 里？**

**这是本项目有意选择的团队约定，不是疏忽。**

多人协作同一个仓库时，凭证写在配置里，所有人拉下来就能直接跑；
改成环境变量的话，每个人还要各自配一遍，漏配的人启动即报错，
而且「我这里能跑你那里不能」会变成日常问题。

**代价**是这些凭证会进版本库，所以对应两条纪律：

1. **仓库必须保持私有**，不要把它公开或提交到公共代码托管
2. **对外交付 / 开源前，先把这四处全部换成占位符**：
   `spring.datasource.password`、`spring.data.redis.password`、
   `oss.accessKeySecret`、`spring.mail.password`（以及 `ai.providers.*.api-key`）
