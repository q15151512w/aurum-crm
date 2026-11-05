# 测试 BUG 记录

> | 项 | 内容 |
> | :--- | :--- |
> | 用途 | 记录联调 / 测试阶段发现的缺陷：现象、根因、修复、验证 |
> | 记录范围 | 前后端都记在这里（缺陷归属模块在每条里注明） |
> | 编写日期 | 2026-09-13 |
> | 当前状态 | BUG-001 ~ BUG-003 **已修复并回归通过**（2026-09-14）；BUG-004 ~ BUG-006、BUG-008 **代码已修复，待实机回归**（2026-09-14）；BUG-007 **已修复**（2026-09-14，纯编译期问题，编译通过即完成验证）；BUG-009 ~ BUG-011 **已修复并通过自检脚本验证**（2026-09-14，做报价单状态闭环时顺手发现并修掉的三个既有缺陷）；**BUG-010 第一次的「已修复」是错的**（实际未落地），已重记为第二轮；BUG-012 ~ BUG-016 **已修复，编译与单测通过，待实机回归**（2026-09-14 第二轮代码审查发现）；BUG-017 **前端权限消费已实现**（2026-09-14，`v-permission` + 工具栏 perm 过滤，构建通过，待实机点验）；**BUG-018 ~ BUG-020 已修复**（2026-09-14 实机联调报障后定位：负责人下拉权限错配、token 失效弹窗、合并事故致自检脚本失效）；**BUG-021 已确认未修复**（顶栏「查看全部」跳 404，修法待产品决策）；BUG-022 **已修复**（2026-09-14 用户报障：注册弹窗输入框有默认值，两个独立原因叠加）；BUG-023 **已修复**（2026-09-14 用户报障：注册时获取不到邮箱验证码，两个独立原因都会表现为同一症状）；BUG-024 **应用侧已改进、根因在 163 账号侧待人工处理**（SMTP 535 认证失败：不是代码 bug，但错误码与日志把排查方向带偏了）；BUG-025 **已定位，可见性已补（后端 + 前端），坏数据未修**（2026-09-14 用户报障：合同审批后仍显示「审核中」—— ① 两级审批流只走完第一级属设计如此，② 种子合同 `HT20260003` 缺审批实例属坏数据；已新增 `GET /crm/contract/{id}/approval`（含**下一步节点与预计审批人**）、待审核列表的当前环节/下一步/提交人字段与 AI 工具 `get_contract_approval`，前端合同审核页两列 + 审批进度弹窗、合同详情页「审批进度」Tab）|
| ⚠️ 阅读警告 | 本文件的**历史条目不能当作事实依据**：BUG-010 曾谎报已修复。核对完成度**请跑命令**，见「第二轮审查的元结论」|

编号规则：`BUG-NNN`。**已修复的条目不要删**，保留根因分析 —— 这类坑往往会在别处重现。

---

## BUG-001 切换员工账号登录后，页面第一次必定是 404

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **前端**（`aurum-crm-ui`） |
| 文件 | `src/router/index.js` |
| 严重程度 | 高 —— 换账号主流程不可用，每次都要手动绕 |
| 状态 | ✅ **已修复并回归通过**（2026-09-13） |

### 1.1 现象

每换一个员工账号登录，落地页**必定是 404 页**；在 404 页按 F5 刷新**依然是 404**，只能手动点页面上的「返回首页」才能进到自己的页面。用同一个账号连续登录则不复现。

### 1.2 复现步骤

1. 用 `admin` 登录 → 正常进入首页
2. 顶栏「个人设置 → 退出登陆」
3. 用 `sales01` 登录
4. **结果**：落在 404 页，地址栏是 `#/404`；F5 刷新仍是 404

只要中间有一步「退出登录」，第 3 步就必定复现。

### 1.3 根因

**两个缺陷叠加**，都在 `src/router/index.js`，单独看每一个都不致命。

#### 缺陷 A：兜底路由是匿名的，`resetRouter()` 删不掉它

注册兜底路由时没给 `name`，而删除时是按 `name` 删的：

```js
// 注册（第 86 行，修复前）
router.addRoute({
  path: '/:pathMatch(.*)*',
  redirect: '/404',
  meta: { hidden: true }
})

// 删除（第 135 行）
const catchAll = router.getRoutes().find((r) => r.path === '/:pathMatch(.*)*')
if (catchAll) router.removeRoute(catchAll.name)   // ← catchAll.name === undefined
```

`vue-router@4.6.4` 的 `removeRoute(name)` 走的是 `matcherMap.get(name)`，而 `insertMatcher()` 里是：

```js
if (matcher.record.name && !isAliasRecord(matcher)) matcherMap.set(matcher.record.name, matcher);
```

**匿名路由压根不进 `matcherMap`**。所以这里实际执行的是 `removeRoute(undefined)`，只打一句警告就返回：

```
[Vue Router warn]: Cannot remove non-existent route "undefined"
```

后果：退出登录后路由表**残留**成 `[/login, /404, /:pathMatch(.*)*]`。

#### 缺陷 B：带 `redirect` 的兜底路由，在守卫之前就被解析了 ⚠️

这是真正致命的一环。`vue-router` 的 `pushWithRedirect()` 里，`handleRedirectRecord()` 的执行**早于** `navigate()`（跑 `beforeEach` 的那一步）：

```js
const shouldRedirect = handleRedirectRecord(targetLocation, from);
if (shouldRedirect) return pushWithRedirect(...);   // ← 先解析 redirect
...
return (failure ? ... : navigate(toLocation, from))  // ← 再跑守卫
```

于是换账号登录后 `router.push('/dashboard')` 时：

1. `/dashboard` 此刻只匹配到**上一个账号残留的兜底路由** → 它的 `redirect: '/404'` 在守卫之前就生效了
2. 守卫被调用时，`to.path` **已经是 `/404`**，原本要去的 `/dashboard` 已经拿不到了
3. 守卫照常拉菜单、注册新账号的路由，然后 `next({ ...to, replace: true })` —— 但 `to` 是 `/404`，于是稳稳停在 404 页
4. 地址栏变成 `#/404`；`/404` 是常驻路由，刷新能正常匹配，所以**刷新也出不来**

**为什么只在换账号时出现**：必须有一次「退出登录」才会留下这个删不掉的残留。全新打开页面时兜底路由还没被注册过，所以首次登录反而正常。

### 1.4 证据

用真实 `vue-router@4.6.4` 跑最小复现，对比不同写法（`createMemoryHistory`，其余逻辑与 `router/index.js` 一致）：

| 写法 | 退出后路由表 | 换账号登录落点 | 守卫实际看到的路径 |
| :--- | :--- | :--- | :--- |
| **修复前** | `/login, /404, /:pathMatch(.*)*` | **/404** ❌ | `/dashboard, /dashboard, /404, /404` |
| 兜底路由加 `name` | `/login, /404` | /dashboard ✅ | 全是 `/dashboard` |
| 兜底改用 `component` | `/login, /404` | /dashboard ✅ | 全是 `/dashboard` |

第三列「守卫实际看到的路径」是 4 次调用：前 2 次属于首次登录，后 2 次属于换账号登录 —— **换账号时守卫一次都没看到 `/dashboard`**，两次都是 `/404`，这就是缺陷 B 的直接证据。

### 1.5 解决方法

**一句话**：在 `src/router/index.js` 注册兜底路由的地方，**加一个 `name`**，并把 `redirect` **换成 `component`**。改一个文件、一处，`resetRouter()` 不用动。

#### 操作步骤

1. 打开 `aurum-crm-ui/src/router/index.js`
2. 找到守卫里「④ 兜底 404」那一段（修复前在第 85-90 行，在 `next({ ...to, replace: true })` 之前）
3. 按下面的 diff 改：

```diff
       // ④ 兜底 404 —— 必须【最后】添加，否则会把未注册的路径都吃掉
-      router.addRoute({
-        path: '/:pathMatch(.*)*',
-        redirect: '/404',
-        meta: { hidden: true }
-      })
+      // ⚠️ 必须带 name：匿名路由进不了 vue-router 的 matcherMap，
+      //    resetRouter() 里的 removeRoute(catchAll.name) 实际是 removeRoute(undefined)，
+      //    只会打一句 "Cannot remove non-existent route" 就返回，兜底路由会一直残留。
+      //
+      // ⚠️ 必须用 component 而不是 redirect：record 级的 redirect 由
+      //    pushWithRedirect() 里的 handleRedirectRecord() 解析，时机在 beforeEach【之前】，
+      //    守卫拿到的 to.path 已经被改写成 /404，来不及注册新账号的路由，
+      //    于是换账号后第一次登录必定停在 404（详见 docs/开发注意事项.md 第 30 条）。
+      router.addRoute({
+        path: '/:pathMatch(.*)*',
+        name: 'CatchAll',
+        component: () => import('@/views/error/404.vue'),
+        meta: { title: '页面不存在', hidden: true }
+      })
```

#### 改完后的完整代码

```js
// ④ 兜底 404 —— 必须【最后】添加，否则会把未注册的路径都吃掉
router.addRoute({
  path: '/:pathMatch(.*)*',
  name: 'CatchAll',
  component: () => import('@/views/error/404.vue'),
  meta: { title: '页面不存在', hidden: true }
})
```

#### 两处改动分别解决什么

**不是二选一，缺一不可**：

| 改动 | 治哪个缺陷 | 不改的后果 |
| :--- | :--- | :--- |
| 加 `name: 'CatchAll'` | 缺陷 A | 兜底路由删不掉，退出登录后残留 |
| `redirect` → `component` | 缺陷 B | 残留的兜底路由仍会在守卫之前把 `to` 改写成 `/404` |

#### `resetRouter()` 为什么不用改

```js
const catchAll = router.getRoutes().find((r) => r.path === '/:pathMatch(.*)*')
if (catchAll) router.removeRoute(catchAll.name)   // ← 有 name 之后这句才真正生效
```

这段逻辑本身没写错，只是**之前拿到的 `catchAll.name` 是 `undefined` 才失效**。兜底路由有了 `name` 之后它就正常工作了。

> 为什么 `component` 能绕开缺陷 B：改用组件后兜底路由不再是 `redirect` 记录，`handleRedirectRecord()` 不会提前改写 `to`，守卫能拿到原始的 `/dashboard`，注册完路由后 `next({ ...to, replace: true })` 重新解析时就匹配到真实路由了。

### 1.6 验证

| 场景 | 预期 | 结果 |
| :--- | :--- | :--- |
| 退出登录后路由表 | `/login, /404` | ✅ 残留消失 |
| 换账号登录 → `/dashboard` | 落点 `/dashboard` | ✅ 守卫看到的就是 `/dashboard` |
| 换账号后直接进二级页 `/crm/lead` | 落点正确 | ✅ |
| 刷新 | 地址栏保留真实路径 | ✅ 不再是 `#/404` |
| 真敲错误路径 `/crm/nonexist` | 渲染 404 页 | ✅ 地址栏保留原路径 |

验证分两层：

| 层 | 方式 | 时间 |
| :--- | :--- | :--- |
| 逻辑层 | `esbuild` 解析通过（确认 `CatchAll` 里的动态 `import` 语法无误）+ 用真实 `vue-router@4.6.4` 按修复后的代码逐行走最小复现，覆盖上表 5 个场景 | 2026-09-13 |
| 实机回归 | dev server 上人工走一遍「登录 admin → 退出 → 登录 sales01」，确认直接进首页、不再出现 404 | 2026-09-13 ✅ |

> 逻辑层的复现脚本用的是 `createMemoryHistory`，其余逻辑与 `router/index.js` 保持一致，可当作回归用例。

### 1.7 行为变更说明

修复后，访问不存在的路径时**地址栏会保留原路径**（如 `#/crm/nonexist`），不再跳成 `#/404`。这是常见做法，但和修复前不同，如果有页面/文档依赖 `#/404` 这个 URL，需要一并核对。

### 1.8 防范建议

1. **凡是 `router.addRoute()` 添加的路由都要有 `name`** —— 匿名路由既删不掉，也不利于 `hasRoute()` 判断。删路由的逻辑不要依赖匿名记录。
2. **兜底路由不要用 `redirect`** —— record 级 `redirect` 的解析时机在 `beforeEach` 之前，任何「靠守卫动态注册路由」的方案都会被它抢跑。用 `component`。
3. `resetRouter()` 目前靠「路径字符串」查找兜底路由（`r.path === '/:pathMatch(.*)*'`），比较脆。后续若再加动态路由，建议把「常驻路由之外的 name」统一收集起来，按集合移除，别一个个找。
4. 这条坑与 `aurum-crm-ui/docs/开发注意事项.md` 第 27 条（页面级权限两层 + 守卫顺序）同源 —— 都是**守卫执行顺序**的问题，改守卫时两条一起看。本条已作为第 30 条记入该文件。

---

## BUG-002 新增线索/合同报「服务器内部错误」，列表多列显示 `--`

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **前后端**（字段口径不一致） |
| 文件 | `GlobalExceptionHandler`（后端）、`crm-ui` 的 lead / contract 模块 |
| 严重程度 | 高 —— 新增流程不可用；列表大面积列无数据 |
| 状态 | ✅ **已修复并回归通过**（2026-09-14） |

### 2.1 现象

新增线索、新增合同时前端统一弹「**服务器内部错误**」，没有任何字段级提示，不知道哪个字段错了、该传什么。

即使不新增，列表也有问题：线索/合同的多列显示 `--`（永远拿不到值），线索的「来源 / 行业 / 级别」直接显示 `3`、`5` 这样的码值。合同更严重 —— 列表**点进去是 `/crm/contract/undefined`**。

### 2.2 复现与实测

登录拿 token（`GET /auth/captcha` 解算术题 → `POST /auth/login`，请求头 `Authorization: Bearer <token>`），直接打真实接口。

**线索 `POST /crm/lead` 四组对照**：

| 发送的载荷 | 结果 |
| :--- | :--- |
| **A** 后端口径（`leadName` + `source: 1` 整数） | ✅ `code 0` 新增成功 |
| **B** 只有 `name`，没有 `leadName` | `code 400`「线索名称不能为空」—— 报错**清晰** |
| **C** `leadName` 有，但 `source: "广告"`（中文） | ❌ **HTTP 500**「服务器内部错误」 |
| **D** 完整前端载荷 | ❌ **HTTP 500** |

**合同 `POST /crm/contract` 四组对照**：

| 发送的载荷 | 结果 |
| :--- | :--- |
| **①** 前端现状（`products` 明细 + `contractType: '销售合同'`） | ❌ **HTTP 500** |
| **②** 改成 `items`，日期是空串 `""` | ✅ `code 0` —— **空串日期不会失败** |
| **③** `items` + 不传日期 | ✅ `code 0` |
| **④** `items` + 合法日期 | ✅ `code 0` |

② 这一组顺带证明了一件事：Jackson 会把 `LocalDate` 字段的空字符串当成 `null`，所以日期字段**不需要**改成字符串 + 宽容解析。

### 2.3 根因

三个独立问题叠加，恰好都指向同一个症状。

#### 根因 1：`GlobalExceptionHandler` 漏了 `HttpMessageNotReadableException` ⚠️

`source: "广告"` 发给后端的 `Integer source`，Jackson 反序列化失败抛 `HttpMessageNotReadableException`。而这个异常**不在** `GlobalExceptionHandler` 已处理的 6 类校验异常的继承链上，于是落到最后的 `Exception` 兜底分支：

```java
@ExceptionHandler(Exception.class)
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public CommonResult<Void> handleException(Exception e, HttpServletRequest request) {
    log.error("[系统异常] {} {}", request.getMethod(), request.getRequestURI(), e);
    return CommonResult.error(ErrorCode.INTERNAL_SERVER_ERROR);   // ← 只有一句通用文案
}
```

结果前端拿到的是 `HTTP 500「服务器内部错误」`。对照 B 组：字段名不对走的是 `MethodArgumentNotValidException`，能返回「线索名称不能为空」—— **同样是口径不对，一个报得清楚，一个完全查不出来**。

#### 根因 2：线索前端表单用的是旧的中文口径

`lead/form-config.js` 当时 import 的是 `SOURCE_OPTIONS` / `INDUSTRY_OPTIONS` / `CUSTOMER_LEVEL_OPTIONS` —— 这三个常量的 `value` 是**中文**，且取值来自已删除的 `src/mock/db.js`，与 `sql/crm.sql` 的 `sys_dict_data` 字典标签**对不上**（前端写「促销 / 商城咨询 / 上门咨询」，字典里是「官网 / 电话 / 展会 / 地推」）。

同时表单 `prop` 是 `name`，而后端只认 `leadName`。

#### 根因 3：合同前后端字段大面积不符

前端 16 列里 13 列对不上。最致命的三个：

| 前端 | 后端 | 后果 |
| :--- | :--- | :--- |
| 明细叫 `products` | `ContractSaveReqVO.items`（标了 `@NotEmpty`） | 后端收到空列表 → 400 |
| `contractType: '销售合同'` | `Integer contractType` | Jackson 失败 → 500 |
| 列表读 `row.id` | `ContractRespVO` **没有 `id`** | 详情跳转 `/crm/contract/undefined` |

`ContractConvert` 当时是**没有任何 `@Mapping` 的纯同名拷贝**，而客户/线索/商机/报价单的 Convert 都显式写了 `xxxId → id` 的别名映射 —— 合同漏了。

### 2.4 探查中顺带发现的三个问题

1. **`Map.of().get(null)` 会 NPE。** `LeadServiceImpl.ownerNameMap` 在结果为空时返回 `Map.of()`，而调用处传的可能是 `null`（线索在池中时 `ownerUserId` 为 `null`）。`Map.of()` 是 `ImmutableCollections.MapN`，`size == 0` 时 `get()` 会先 `Objects.requireNonNull(key)` → **线索池列表整体 500**。商机模块的同名方法早就用了 `Collections.emptyMap()`，线索漏了。已修。

2. **合同的 `status` 列名实不符。** `crm_contract` 有两个状态列：`audit_status`（待提审/审批中/已通过/已驳回）与 `status`（草稿/已生效/已作废/已到期）。前端列表与详情页的配色映射表键名是「未提交/审核中/审核通过/审核拒绝」—— 那是**审核态**，却挂在 `status` 名下。本次把前端列名改为 `auditStatus`，语义不变、名实相符。

3. **演示数据的「创建人」永远是 `--`，是数据问题不是代码问题。** `sql/crm.sql` 里业务数据的 INSERT **全都没有列 `create_by` 这一列**（35 处 `create_by` 全在建表语句里），所以种子数据这一列是 `NULL`。商机/报价单的 Service 早已从 `create_by` 补 name，代码没问题。新建的记录走 `DBFieldHandler` 会正常填充。

   ✅ **已修**（见 2.6 修复清单里的 `sql/crm.sql` **4.10 节**）：用 `UPDATE`
   给 7 张业务表补齐 —— 有归属人的取 `COALESCE(owner_user_id, 1)`，
   产品/产品分类无归属人，统一记在超管名下。
   **没有回头去改各表的 INSERT 列清单**：那是多行长语句，逐行插值很容易和列对不齐，
   而一条一句的 `UPDATE` 一眼就能看出改了什么。

### 2.5 本次确立的字段口径约定 ⭐

探查发现后端其实已有两套**成体系**的约定，只是线索和合同没跟上。现将它们固定为正式约定（已写入《05-接口文档》通用约定一节）：

| 字段类型 | 约定 | 判断标准 | 已遵循 |
| :--- | :--- | :--- | :--- |
| **数据字典字段** | 接口传 **TINYINT 码值**，前端 `*_CODE_OPTIONS` + `#cell-xxx` slot 映射标签 | 值存在 `sys_dict_data` 表里 | 客户、线索、合同 |

> ⚠️ **上表「取数方式」一栏已于 2026-09-15 更新**：前端不再直接引 `constants/options.js` 的
> `*_CODE_OPTIONS`，改为统一走 `composables/useDictOptions.js`（数据源是
> `GET /system/dict-data/batch`，那些常量降级为兜底种子）。**「传码值、不传中文」这条约定本身不变**
> —— 这里保留原文作为 BUG-002 当时的记录；现行写法见《05-接口文档》通用约定规则 1。
| **状态机枚举字段** | **后端**转中文标签返回，前端 options 保持中文 | 值写死在 Java 枚举里 | 商机、报价单、合同（本次新增两个枚举） |
| **对外字段名** | 请求方向用 **DDL 字段名**；响应方向补前端要的**别名与跨表派生字段** | — | 全部 |

配套的 Java 样板：`OpportunityStage` / `QuotationStatus`，本次新增 `ContractAuditStatus` / `ContractStatus`（都有 `ofCode` / `ofLabel` / `labelOf` / `labelOptions`）。

### 2.6 修复清单

**后端**

| 文件 | 改动 |
| :--- | :--- |
| `GlobalExceptionHandler` | 补 `HttpMessageNotReadableException` 处理 → 400 + 指明「哪个字段、什么值、期望什么类型」 |
| `ContractRespVO` / `ContractConvert` | 补 `id`/`name` 别名、`finalAmount`、`customerName`/`opportunityName`/`quotationNo`/`creator` |
| `ContractAuditStatus` / `ContractStatus` | **新建**，`auditStatus`/`status` 由码值转中文标签 |
| `ContractServiceImpl` | `page()`/`get()` 批量补派生字段（客户名 / 商机名 / 报价单号 / 创建人） |
| `LeadDO` / `LeadSaveReqDTO` / `LeadRespVO` / `LeadConvert` / `LeadServiceImpl` | 接上 `level`、`nextContactTime`、`creator`；修 `Map.of()` NPE |
| `OpportunityDO` / `SaveReqVO` / `RespVO` / `Convert` / `ServiceImpl` | 接上 `nextContactTime`（含「清空日期」那条单独 update） |
| `RegionText` | **新建**，`province/city/district` → 前端要的 `region` 拼接串（`@AfterMapping` 调用） |
| `CustomerRespVO` / `LeadRespVO` | 加派生的 `region` 字段，列表展示与编辑回填都用它 |
| `sql/crm.sql` | `crm_lead` 加 `level`、`next_contact_time`；`crm_opportunity` 加 `next_contact_time`；新增 4.10 节补齐 7 张业务表的 `create_by`；4.11 校验节加了「创建人是否已补齐」查询 |

**前端**

| 文件 | 改动 |
| :--- | :--- |
| `constants/options.js` | 新增 `SOURCE_CODE_OPTIONS` / `INDUSTRY_CODE_OPTIONS` / `CONTRACT_TYPE_CODE_OPTIONS`；旧的中文常量标 `@deprecated`；文件头写清两套口径的适用场景 |
| `lead/form-config.js` | `name` → `leadName`；三个字典字段改用码值 options |
| `lead/index.vue` | 列 prop 改 `leadName`；加 `#cell-source` / `#cell-industry` / `#cell-level` 映射 slot |
| `lead/detail.vue` | 同样加码值映射 |
| `api/lead.js` | 加 `splitRegion()`（把 `region` 拆成 province/city/district），与 `api/customer.js` 同一套做法 |
| `contract/form-config.js` | `name`→`contractName`、`orderDate`→`signDate`、`startTime`→`startDate`、`endTime`→`endDate`、`products`→`items`、`finalAmount`→`amount`；`contractType` 改码值；`contractNo` 改只读（后端自动生成） |
| `contract/index.vue` / `detail.vue` / `audit.vue` | 字段名同步；`status` → `auditStatus`；移除「整单利润」列（全库无 `profit` 列） |
| `scripts/check-api-mock.mjs` | 补 `@/constants/regions` 的别名映射 |

> **`check:mock` 的修复说明**：`api/customer.js` 有一行 `import { parseRegion } from '@/constants/regions'`，而该脚本的别名替换表里没有 `@/constants`，副本会留下裸 `@/constants` 让 node 解析失败 —— **整个 check:mock 以 `ERR_MODULE_NOT_FOUND` 挂掉**，不是断言失败而是加载失败，所以「66 项全通过」这个保护其实已经失效了一段时间（见 `开发注意事项.md` 第 26 条同类教训：检查脚本自己悄悄失效）。补上映射后恢复 66 项全通过。

### 2.7 验证

| 层 | 结果 |
| :--- | :--- |
| 后端编译 | ✅ `mvn compile` 通过 |
| 前端静态检查 | ✅ `npm run check:static` 全部通过（含「每个 FIELDS 的 prop 都被 emptyForm 覆盖」） |
| 前端 api 层 | ✅ `npm run check:mock` 66 项全通过（修复脚本后） |
| 前端构建 | ✅ `npm run build` 通过 |
| **实机回归** | ✅ **通过**（2026-09-14）—— 已重跑 `sql/crm.sql` 并重启后端 |

实机回归覆盖的用例（2026-09-14 整体确认通过）：

1. 在目标库重跑 `sql/crm.sql`（后端连的是远程库，不是本地）。
   ⚠️ 该脚本会 **DROP 并重建全部 47 张表**，现有业务数据会清空 ——
   本次只保留了这一份全量脚本，没有增量脚本，因为结构变更已经写进建表语句、
   时间点又在项目早期，重跑比维护一份 ALTER 更不容易出错。
2. 重启后端
3. **反向用例**：发一个类型错的字段（如 `source: "广告"`）→ 期望 **400 + 可读消息**（这是根因 1 的验收点，应不再是 500）
4. 线索：新增（码值 + 省市区）→ 保存 → 列表显示中文标签 → 详情回填 → 编辑保存
5. 合同：新增（含明细）→ 列表不再出现 `/crm/contract/undefined` → 审核态显示中文标签

### 2.8 防范建议

1. **请求体解析失败必须单独兜异常。** 任何「字段类型不匹配」都会走 `HttpMessageNotReadableException`，不兜就是无信息量的 500。加新校验异常处理时，别忘了它。
2. **加字段前先看后端 DDL 的类型。** 前端下拉的 `value` 是中文还是数字，决定了请求会不会 500。`constants/options.js` 的文件头已写明两套口径的适用场景，新增下拉请照它选。
3. **VO 的 `id` 别名是硬要求。** `useTable` / `ProList` 靠 `row.id` 做行键与详情跳转，缺了不报错，只是悄悄跳到 `/undefined`。新增模块的 Convert 必须写 `xxxId → id`。
4. **空集别返回 `Map.of()`。** 用 `Collections.emptyMap()` —— `Map.of()` 的 `get(null)` 会抛 NPE。这类 NPE 只在「整页数据都没有归属人」时触发，本地很难碰到。
5. **自检脚本失效是静默的。** 修完 `check:mock` 才想起它已经挂了 —— 它挂的方式是加载失败而不是断言失败，看起来像「环境问题」。定期跑，别等改代码时才跑。

---

## BUG-003 超管账号唯独进不去首页看板（权限最高的账号，权限点反而匹配不上）

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **后端**（`aurum-crm`，Sa-Token 权限匹配） |
| 文件 | `SuperPermission` / `SaTokenConfig` / `SaPermissionImpl` / `AuthConstants` / `DashboardServiceImpl` |
| 严重程度 | 中高 —— 超管看板 8 个接口全 403；但**只影响看板**，其余 38 个权限点全正常，极易误判成「看板数据权限 / 菜单没配」 |
| 状态 | ✅ **已修复并回归通过**（2026-09-14）—— 采用方案 ①，改动见 3.5 |

> ⚠️ **本条曾在版本回退中被整体冲掉过一次**（2026-09-14），后按原方案重打。
> 这份记录本身也只是文档，**回退代码时它们会一起消失** —— 若将来发现看板又坏了，
> 先确认 `SuperPermission.java` 是否还在、`SaTokenConfig#overridePermissionMatching()` 是否还在。

### 3.1 现象

最高等级账号（超级管理员 `admin`）登录后，**首页看板拿不到数据** —— 页面能正常打开，但 `/dashboard/todo`、`/dashboard/stats`、`/dashboard/focus` 等 8 个接口全部 403 无权限，看板是空壳。

而**低权限**角色（销售总监 / 销售专员 / 财务）的看板一切正常。

> 这是本 BUG 最反直觉的地方：**权限等级越高的账号，越是唯一进不去首页看板的人。**
> 预期「首页看板的权限限制应该是最低的」，实际却是超管被挡在门外。

### 3.2 复现步骤

1. 用 `admin` 登录 → 进首页 → 看板各卡片无数据，Network 里 `/dashboard/*` 全 403
2. 退出，换 `sales01`（销售专员）登录 → 首页同上 → 看板**数据正常**

只要账号是超管角色就必现，与浏览器、缓存无关。

### 3.3 根因

**三个事实叠加**，每一个单独看都不算错，凑在一起才致命。

#### 事实 A：超管的权限集合是「合成」的，不含具体权限码

`AuthServiceImpl.java:256-260`：

```java
// 超管的 *:*:* 不在数据库里，必须在这里合成 ——
// 漏了会让前端的超管判断失败、按钮全灰，而登录本身是成功的，很难定位
ctx.setPermissions(superAdmin
        ? new LinkedHashSet<>(AuthConstants.SUPER_PERMISSIONS)   // ← 只有 ["*:*:*"]
        : permissionsOf(roleIds));                              // ← 普通角色走这里
```

`AuthConstants.java:61,71`：

```java
public static final String SUPER_ROLE_KEY = "admin";
public static final Set<String> SUPER_PERMISSIONS = Set.of(SuperPermission.WILDCARD);
```

判断条件是 `roleKeys.contains("admin")`（精确等值、大小写敏感）。

⚠️ 关键点：超管走的是**三元表达式的第一个分支**，**完全绕过** `permissionsOf(roleIds)`。
所以尽管 `sql/crm.sql:1773` 确实给超管授了全量菜单（`SELECT 1, menu_id FROM sys_menu`），
这些 `dashboard:view` **一条都不会进超管的权限集**。超管权限集里永远只有 `*:*:*` 这一个值。

#### 事实 B：Sa-Token 的 `*:*:*` 是**通用通配符**，不是「匹配一切」的特例 ⚠️

`SaPermissionImpl.java` 原先的注释写着：

> 超管的 `*:*:*` 也在 Session 里，Sa-Token 按通配模式匹配，
> 即 `*:*:*` 能命中 `crm:lead:list` 这类具体标识。

**这句话对三段式成立，对两段式不成立** —— 写注释的人大概只拿三段式验证过。

`sa-token-core:1.39.0` 的 `SaFoxUtil.vagueMatch(String patt, String str)` 实现（反编译字节码确认）：

```java
public static boolean vagueMatch(String patt, String str) {
    if (patt == null && str == null) return true;
    if (patt == null || str == null) return false;
    if (!patt.contains("*")) return patt.equals(str);   // 无通配符 → 退化为 equals
    return vagueMatchMethod(patt, str);                 // 有通配符 → 通用通配 DP
}
```

`vagueMatchMethod` 是经典的**通配符匹配 DP**（`*` 匹配任意字符序列）。**模式里的 `:` 是字面量**，
所以 `*:*:*` 展开后要求目标串里**至少有两个冒号**：

- `*:*:*` vs `crm:lead:list` → `*`=`crm`、`:`、`*`=`lead`、`:`、`*`=`list` → ✅ 命中
- `*:*:*` vs `dashboard:view` → 目标串**只有一个冒号**，第二个 `:` 无处可匹配 → ❌ **拒绝**

**Sa-Token 没有对 `*:*:*` 做任何特殊处理**（1.39.0 无此特判），它就是普通通配模式。

#### 事实 C：`dashboard:view` 是全项目**唯一**的两段式权限点

把后端所有 `@SaCheckPermission` 的**注解取值**抽取去重（只取行首的注解本身，
⚠️ 别把 javadoc 里提到的 `@code @SaCheckPermission` 也算进来，那会把数字撑大），
共 **39 个权限点**，按冒号段数分类：

| 段数 | 数量 | 例子 | `*:*:*` 能否命中 |
| :--- | :--- | :--- | :--- |
| 三段式（2 个冒号） | 38 | `crm:lead:list`、`system:user:create`、`fin:payment:submit` | ✅ 全部命中 |
| **两段式（1 个冒号）** | **1** | **`dashboard:view`** | ❌ **命中不了** |

`DashboardController.java:46-49`：

```java
@RequestMapping("/dashboard")
@Validated
@SaCheckPermission("dashboard:view")     // ← 全项目唯一的两段式权限点
public class DashboardController {
```

> 这就是「**为什么偏偏只有首页看板中招**」的答案：`dashboard:view` 恰好是全项目唯一的两段式权限码。
> 如果当初写成 `dashboard:index:view`，这个 BUG 根本不会出现。
>
> `sys_menu` 里另有 7 个两段式权限码（`crm:lead`、`crm:customer`、`crm:opportunity`、
> `crm:contract`、`crm:product`、`fin:payment`、`system:dept`），但它们是 `menu_type=1` 的**目录节点**，
> **没有任何 `@SaCheckPermission` 引用**，所以不产生 403。

#### 为什么普通角色反而正常

`sql/crm.sql:1779-1782 / 1813-1816 / 1843-1846` 给销售总监、销售专员、财务都**显式授了** `'dashboard:view'`。
他们走 `permissionsOf(roleIds)`，权限集里是**字面量** `dashboard:view`，
`vagueMatch` 因 `patt` 不含 `*` 而退化成 `equals` → 精确命中 → 通过。

### 3.4 证据

把 `sa-token-core-1.39.0.jar` 里的 `SaFoxUtil` 解出，用真实类跑实测：

| 模式 `patt` | 目标 `str` | `vagueMatch` 结果 |
| :--- | :--- | :--- |
| `*:*:*` | `dashboard:view` | ❌ **DENY** |
| `*:*:*` | `crm:lead:list` | ✅ PASS |
| `*:*:*` | `crm:customer:create` | ✅ PASS |
| `*:*:*` | `system:user:list` | ✅ PASS |
| `*:*:*` | `fin:payment:submit` | ✅ PASS |
| `dashboard:view` | `dashboard:view` | ✅ PASS |

前两行并列就是完整的根因证明：**同一个通配符，三段式过、两段式拒**。
最后一行则证明普通角色为什么会过（退化成 `equals`）。

### 3.5 解决方法

三个方案，**采用 ①**。

#### 方案 ① 覆写 Sa-Token 的权限匹配（✅ 已采用）

在 `SaTokenConfig` 里加一段，让 `*:*:*` 真正等价于「拥有全部权限」：

```java
// Sa-Token 默认的 vagueMatch 把 *:*:* 当普通通配模式，要求目标串有两个冒号，
// 于是 dashboard:view 这类两段式权限码匹配不上（BUG-003）。
// 这里显式约定：*:*:* 就是「全部权限」，与前端 hasPermission、DashboardServiceImpl 的语义保持一致。
@PostConstruct
public void overridePermissionMatching() {
    SaStrategy.instance.hasElement = (list, element) ->
            list != null && (list.contains(SuperPermission.WILDCARD)
                    || list.stream().anyMatch(p -> SaFoxUtil.vagueMatch(p, element)));
}
```

- `SaStrategy.instance.hasElement` 是公有字段（类型 `SaHasElementFunction`），可直接覆写，1.39.0 确认支持。
- `SaHasElementFunction` 的签名是 `BiFunction<List<String>, String, Boolean>`，
  即 `apply(List<String> 权限集, String 待校验权限码)` —— 上面的 lambda 与之匹配。
- 保留 `vagueMatch` 那一支，是为了不弄丢「角色被授予 `crm:lead:*` 这类部分通配」的既有能力。
  本方法是**追加**超管这一条，不是替换整个匹配语义。

#### 本次实际改动

| 文件 | 改动 |
| :--- | :--- |
| `crm-common/.../core/SuperPermission.java` | **新建**，定义 `WILDCARD = "*:*:*"` |
| `crm-security/.../config/SaTokenConfig.java` | 新增 `@PostConstruct overridePermissionMatching()`，覆写 `SaStrategy.instance.hasElement` |
| `crm-security/.../core/SaPermissionImpl.java` | **更正注释** —— 原文声称「`*:*:*` 能命中 `crm:lead:list` 这类具体标识」，是本次踩坑的直接源头 |
| `crm-module-system/.../core/AuthConstants.java` | `SUPER_PERMISSIONS` 改为引用 `SuperPermission.WILDCARD`，不再写字符串字面量 |
| `crm-module-crm/.../service/impl/DashboardServiceImpl.java` | 删掉本地的 `WILDCARD_PERMISSION = "*:*:*"`，改用 `SuperPermission.WILDCARD`（消除第 3 份重复定义） |

> **为什么不把常量留在 `AuthConstants`**：`SaTokenConfig` 在 `crm-security`，
> 它只依赖 `crm-common`（见后端 README 的依赖方向说明），**看不到** `crm-module-system`。
> 常量留在原处，框架侧就只能另抄一个字符串，于是又变成两份定义 ——
> 这正是 `AuthConstants` 里 `DataScope` 那段注释警告过的情形。
> 所以按同一个先例，上提到 `crm-common`，与 `DataScope` 并列。

> **关于 `DashboardServiceImpl` 的第三份定义**：它原先在 `shortcuts()` 里用
> `permissions.contains("*:*:*")` 判断超管 —— 那是**字面量 contains**，
> **不经过 Sa-Token 的通配匹配**，所以这处本来就工作正常，不是本 BUG 的受害者。
> 本次一并改指向 `SuperPermission.WILDCARD`，纯粹是为了消掉第三份重复定义
> （顺带在那个判断旁写清了「为什么这里不受 BUG-003 影响」）。
>
> 这也说明超管的判断口径在本项目里**有三处、两套依据**：
> `AuthServiceImpl` 与 `buildMenuTree` 看 **roleKeys**，
> `SaTokenConfig`（注解校验）与 `DashboardServiceImpl.shortcuts` 看 **permissions**。
> 后者中只有走 Sa-Token 匹配的那条路会踩 BUG-003。新增超管判断时请留意选哪一条。

#### 方案 ② 超管权限做并集

改 `AuthServiceImpl` 的超管分支，让超管同时拿到 `*:*:*` 和真实权限码。

**优点**：改动最小。
**缺点**：治标 —— 超管重新依赖 `sys_role_menu` 的种子数据，
而 `buildMenuTree` 的注释明确说过要**避免**这种依赖；且下次谁再写两段式权限码还会复现。

#### 方案 ③ 把 `dashboard:view` 改成三段式

改成 `dashboard:index:view`，同步改 `sql/crm.sql:1581`、`DashboardController.java:48`、前端 `src/mock/menu.js`。

**缺点**：最麻烦（已有库要 `UPDATE` 迁移），**且没解决根因** —— 换了名字而已，
下一个两段式权限码还会炸。

#### 前端为什么不用改

`store/user.js` 的超管判断是**显式**的：

```js
hasPermission(permission) {
  if (!permission) return true
  return this.permissions.includes('*:*:*') || this.permissions.includes(permission)
}
```

它直接 `includes('*:*:*')`，**不依赖 Sa-Token 的通配匹配**，所以超管在前端永远是放行的。

这也决定了本 BUG 的**症状形态**：`router/index.js:125` 的路由守卫会**放行**，
页面能正常打开；失败的只是页面对后端的请求。所以**不是 404，也不是菜单不显示**，
排查时不要往「路由 / 菜单」方向找。

### 3.6 验证

| 层 | 方式 | 结果 |
| :--- | :--- | :--- |
| 逻辑层（根因） | 解出 `sa-token-core-1.39.0.jar` 的 `SaFoxUtil`，用真实类对 6 组模式/目标跑 `vagueMatch` | ✅ 见 3.4，根因确证 |
| 静态层 | 全后端抽取 `@SaCheckPermission` 注解取值去重，按冒号段数分类 | ✅ 39 个权限点，两段式仅 `dashboard:view` |
| 编译 | `mvn -o compile` | ✅ `BUILD SUCCESS` |
| 逻辑层（修复后） | 用**真实 `SaFoxUtil`** 跑与 `overridePermissionMatching()` 逐字一致的 lambda，覆盖 12 个用例 | ✅ 全部通过，见下表 |
| **实机回归** | 重启后端，用 `admin` 登录看首页看板 | ✅ **通过**（2026-09-14）|

修复后的 12 个用例（`check(身份, 权限集, 待校验权限码, 期望)`）：

| 组 | 身份 / 权限集 | 待校验 | 期望 | 结果 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | 超管 `["*:*:*"]` | `dashboard:view` | ✅ 通过 | ✅ |
| 1 | 超管 `["*:*:*"]` | `crm:lead:list` | ✅ 通过 | ✅ |
| 1 | 超管 `["*:*:*"]` | `system:user:create` | ✅ 通过 | ✅ |
| 1 | 超管 `["*:*:*"]` | `fin:payment:submit` | ✅ 通过 | ✅ |
| 2 | 销售专员（含 `dashboard:view`） | `dashboard:view` | ✅ 通过 | ✅ |
| 2 | 销售专员（含 `dashboard:view`） | `system:user:create` | ❌ 拒绝 | ✅ |
| **3** | **无看板权限角色 `["crm:lead:list"]`** | **`dashboard:view`** | **❌ 拒绝** | **✅** |
| 3 | 空权限集 `[]` | `dashboard:view` | ❌ 拒绝 | ✅ |
| 3 | 空权限集 `[]` | `crm:lead:list` | ❌ 拒绝 | ✅ |
| 3 | `null` 权限集 | `dashboard:view` | ❌ 拒绝 | ✅ |
| 4 | 部门主管 `["crm:lead:*"]` | `crm:lead:list` | ✅ 通过 | ✅ |
| 4 | 部门主管 `["crm:lead:*"]` | `crm:customer:list` | ❌ 拒绝 | ✅ |

> **第 3 组是本次最重要的验收点**：覆写的是「`*:*:*` 等价于全部权限」这个**语义**，
> 而不是「所有请求一律放行」。没有这一组，就无法排除「把权限校验整个改松了」这种更糟的结果。

实机回归清单：

| # | 步骤 | 期望 | 结果 |
| :--- | :--- | :--- | :--- |
| 1 | 重启后端 | — | ✅ 已做 |
| 2 | 用 `admin` 登录 → 首页看板 | 看板有数据，不再是 403 | ✅ **通过**（2026-09-14）|
| 3 | **反向用例**：用 `sales01` 登录 → 首页看板 | 仍正常（确认没把普通角色改坏）| ⬜ 未单独确认 |
| 4 | **反向用例**：造一个**不授 `dashboard:view`** 的角色 → 登录 | 仍 **403**（确认覆写没把权限放开）| ⬜ 未单独确认 |

> 第 4 条是方案 ① 的**关键验收点**。不过它已有**逻辑层**的等效证据：上表第 3 组专门覆盖了
> 「无看板权限角色 / 空权限集 / `null` 权限集」三种情况，结果都是**拒绝** ——
> 用的是与线上逐字一致的 lambda 和**真实** `SaFoxUtil`。
> 所以实机第 4 条属于**锦上添花**，不是未验证的风险点。

### 3.7 防范建议

1. **权限码统一三段式 `模块:对象:动作`。** `dashboard:view` 是两段式，是本次唯一的受害者。
   两段式本身不违反任何规则，但它会让 `*:*:*` 这类占位通配符**静默失效** ——
   新增菜单时请照 `模块:对象:动作` 三段写。
2. **超管权限别只放一个 `*:*:*`。** 通配符依赖框架的匹配语义，而匹配语义是会变的
   （换框架、升版本、加自定义实现）。方案 ① 是把语义**显式钉死**在代码里，
   而不是依赖「大家都以为 `*:*:*` 匹配一切」这个默契。
3. **通配符匹配一定要拿最短的样本验证。** 本次注释里写着「`*:*:*` 能命中 `crm:lead:list` 这类具体标识」——
   验证用的是三段式，恰好掩盖了缺陷。凡是通配逻辑，**用最简单的那个样本**（段数最少的）试。
4. **「权限最高的账号没权限」优先怀疑超管特判，而不是角色配置。** 症状是「越高级越进不去」时，
   基本可以排除 RBAC 数据问题（数据问题会让**所有人**都进不去），
   直接去看超管的**合成权限**和**框架的匹配实现**。
5. **前端与后端的超管判断口径要保持一致。** 前端 `includes('*:*:*')` 是显式判断，
   后端 `vagueMatch` 是模式匹配 —— 两边对 `*:*:*` 的理解不同，
   导致「页面进得去、接口全 403」这种**半坏**状态，比彻底 403 更难定位。
6. **文档、注释、代码会共享同一个错误假设。** 本 BUG 的错误假设同时存在于
   `SaPermissionImpl` 注释、《05-接口文档》和代码逻辑里 —— 只改代码的话，
   下一个照文档写代码的人还会再踩一次。修完请一并核对文档。

### 3.8 本次同步更新的文档

| 文档 | 改动 |
| :--- | :--- |
| `docs/04-权限设计文档.md` | 5.3 与第六节原写「合并所有角色的 permissions（取并集）」—— 这对超管**不成立**。补上超管分支（权限被替换、菜单取全量）与 `*:*:*` 的匹配语义说明 |
| `docs/05-接口文档.md` | 原写「Sa-Token 走通配匹配，所有权限点都通过」—— 这句在修复前是**错的**。改为「由 `SaTokenConfig` 显式放行」，并补上第九节缺失的权限点 `dashboard:view` 标注 |
| `aurum-crm-ui/docs/开发注意事项.md` | 新增第 31 条：前端放行 ≠ 后端放行，两端口径不同会产生「半坏」状态 |

> 与 BUG-001 的做法一致（当时也同步记入了 `开发注意事项.md`）。

---

## BUG-004 编译报错「找不到符号 `LEAD_ALREADY_IN_POOL`」——常量加进了一个没人用的重复枚举

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **后端**（`aurum-crm`，`crm-common`） |
| 文件 | `common/exception/ErrorCode.java`（正主）、`common/core/ErrorCode.java`（重复副本） |
| 严重程度 | 中 —— 编译不过，后端构建整个中断 |
| 状态 | ✅ 代码已修复，`crm-common` 编译通过；**整体后端未重新构建** |

### 4.1 现象

```
LeadServiceImpl.java:214:49
java: 找不到符号
  符号:   变量 LEAD_ALREADY_IN_POOL
  位置: 类 com.aurum.crm.common.exception.ErrorCode
```

### 4.2 根因

项目里有**两个几乎逐字节相同**的 `ErrorCode` 枚举：

| 类 | 引用情况 |
| :--- | :--- |
| `com.aurum.crm.common.exception.ErrorCode` | ✅ 全项目 **27 个文件** import 它 |
| `com.aurum.crm.common.core.ErrorCode` | ❌ **零引用**（grep `common.core.ErrorCode` 无匹配） |

`LEAD_ALREADY_IN_POOL` 这个常量被加进了 `core` 那个副本，而 `LeadServiceImpl.java:7` 导入的是 `exception` 那个 → 调用方报「找不到符号」。

判定 `core` 是孤儿的关键证据：**连与它同包的 `CommonResult.java` 都显式写了** `import com.aurum.crm.common.exception.ErrorCode;`。两个文件 `diff` 下来只差**包名一行**加新增常量，说明 `core` 那份是复制 `exception` 后改包名产生的。

### 4.3 解决方法

在**真正被使用的** `exception/ErrorCode.java:118` 补上常量：

```java
LEAD_ALREADY_IN_POOL(2005, "该线索已在线索池中"),
```

编号取 2005：线索段 2001–2004 已占用，与现有码不冲突。语义上也是对的 —— 该错误码用于「线索已在池中（`owner_user_id IS NULL`）却又点了放入线索池」，原先复用的 `LEAD_NOT_IN_POOL` 会提示成「该线索不在线索池中」，**与用户眼前的事实正好相反**。

### 4.4 验证

| 层 | 方式 | 结果 |
| :--- | :--- | :--- |
| 引用方 | 确认 `LeadServiceImpl.java:7` 导入的正是被修改的类 | ✅ |
| 定义方 | 确认常量已在 `exception/ErrorCode.java:118` | ✅ |
| 全量差集 | 把全项目所有 `ErrorCode.XXX` 引用与枚举定义做差集比对 | ✅ **无其他缺失常量**，此类编译错误已清干净 |
| 编译 | `mvn -o -pl crm-framework/crm-common compile` | ✅ exit 0 |
| 整体后端 | 完整 `mvn compile` | ⬜ **未跑** |

### 4.5 防范建议

1. **重复类一旦产生，就会持续吞掉改动。** 两份 `ErrorCode` 的差异只体现在「改哪一份」上，编译器只在**调用方**报错，很容易被误判成「漏定义」而不是「定义在别处」。
2. **建议删掉 `common/core/ErrorCode.java`。** 本次**没有删** —— 它不是本次改动产生的，且删除属于独立决定。留着的话，下次谁让 IDE 自动补全导错了包，会一模一样地再踩一次。
3. **同包引用是判活时最容易漏的一类。** `core` 那份之所以「看起来像活的」，正因为同包内引用不需要 import。用全限定名 grep 会漏掉这种情况，**要连包内一起去查**。

---

## BUG-005 「放入线索池」强制填写放入原因

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **前端**（`aurum-crm-ui`） |
| 文件 | `src/views/crm/lead/index.vue:192`（列表页批量）、`src/views/crm/lead/detail.vue:107`（详情页单条） |
| 严重程度 | 中 —— 主流程被多出来的必填步骤挡住 |
| 状态 | ✅ 代码已修复；`check:static` / `check:mock` / `build` 全通过。**未实机点过** |

### 5.1 现象

点「放入线索池」后弹窗要求填「放入原因」，不填点不了确定（提示「放入原因不能为空」）。

### 5.2 根因

前端用了 `ElMessageBox.prompt` 并挂了硬性校验：

```js
const { value: reason } = await ElMessageBox.prompt(..., {
  inputValidator: (v) => (v && v.trim() ? true : '放入原因不能为空')   // ← 拦截在这里
})
```

**后端一直是允许不填的**，核对过三处，全都支持空原因：

| 层 | 代码 | 结论 |
| :--- | :--- | :--- |
| DTO | `ToLeadPoolReqDTO` 的 `reasonCode` / `reasonRemark` | 两个字段都**没有** `@NotNull` / `@NotBlank` |
| Controller | `LeadController.java:102` | `@RequestBody(required = false) ToLeadPoolReqDTO reqDTO` |
| Service | `LeadServiceImpl#putToPool` | 每个字段都判空：`reqDTO == null ? null : reqDTO.getReasonCode()` |

所以这是**纯前端**多出来的限制，接口本身早就支持空原因。

### 5.3 解决方法

`ElMessageBox.prompt` → `ElMessageBox.confirm`，请求体改为发空对象：

```js
await leadToPool(id, {})
```

**保留了确认弹窗**：需求是「不应该让填理由」，**不是**「不应该有弹窗」；同页的「删除」「线索转客户」都是这个交互，批量操作下防误点仍有价值。

> ⚠️ **请求体不能省略，必须发 `{}`。** `request.js:28` 默认带 `Content-Type: application/json`，
> 而 Spring 对「POST + application/json + **空 body**」会抛 **415**，
> **不会**因为写了 `@RequestBody(required = false)` 就当成 null 放行。
> 发 `{}` 的结果完全一致（两个字段都是 null → 写 NULL），但没有这个风险。

### 5.4 验证

| 层 | 方式 | 结果 |
| :--- | :--- | :--- |
| 静态检查 | `npm run check:static` | ✅ 全部通过（182 个请求可静态解析） |
| api/mock | `npm run check:mock` | ✅ 66 项全通过 |
| 构建 | `npm run build` | ✅ exit 0 |
| 残留检查 | 全仓库 grep `inputValidator` / 「放入原因不能为空」 | ✅ 线索 + **客户**（下方 5.5 遗留 1）路径下均已无残留，仅剩 `finance/invoice/issue.vue` 的发票号 —— 那是真必填 |
| **实机回归** | 浏览器点「放入线索池」 | ⬜ **未做** |

> `开发注意事项.md` 第 19 条明确写着「**build 通过 ≠ 代码能跑**」，
> 所以上表的构建通过**不能当作运行时保证**。

### 5.5 遗留

1. ✅ **已一并修复**：`customer/index.vue` 的「放入公海」原有一模一样的必填理由模式，现已按同一套做法改成「只确认、发 `{}`」。
   客户**详情页** `detail.vue:331` 本来就是 `ElMessageBox.confirm`（没有理由输入框），无需改动 —— 所以同一个「放入公海」动作，修复前**列表页和详情页的行为不一致**。
2. `npm run check:api` 跑不起来，报找不到 `ui/crm-ui/aurum-crm/docs/05-接口文档.md` —— 它假设前后端同级目录，而实际后端在 `master/crm/aurum-crm/`。**与本次改动无关**，属既有的路径假设问题。

### 5.6 防范建议

1. **「必填」要由后端契约决定，不要在前端凭感觉加。** 本次的必填拦截完全在前端，后端从头到尾都支持空值 —— 前端单方面加限制，会让一个不需要理由的操作变得繁琐。
2. **`ElMessageBox.prompt` 用于「收集必要输入」，不是「确认」。** 只是要一个确认时用 `confirm`，`prompt` 会把确认变成填写。
3. **POST 空 body 在 Spring 下不是「等于 null」。** 见 5.3 的 ⚠️ —— 这是本次差点踩进去的坑，发 `{}` 是零成本的规避。

---

## BUG-006 创建时间显示成带 `T` 的 ISO 格式

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **后端**（`aurum-crm`，Jackson 全局配置） |
| 文件 | `common/jackson/JacksonConfig.java`、`crm-server/src/main/resources/application.yml` |
| 严重程度 | 中低 —— 功能可用，但**所有含时间字段的列表页**显示难看 |
| 状态 | ✅ 代码已修复；隔离实验验证了 Jackson 语义；`crm-common` 编译通过。**后端未重启，线上尚未生效** |

### 6.1 现象

线索列表「创建时间」列显示 `2026-09-14T09:13:37`（中间带 `T`），期望 `2026-09-14 09:13:37`。

### 6.2 复现与实测

登录拿 token（`GET /auth/captcha` 解算术题 → `POST /auth/login` → 请求头 `Authorization: Bearer <token>`，账号 `admin` / `123456`），打真实接口：

```
GET /crm/lead?pageNo=1&pageSize=2
→ "createTime": "2026-09-14T09:13:37"
```

前端**全量搜过，没有任何日期解析/格式化逻辑**（`utils/`、`components/` 里只有一个 `toLocaleString` 用于金额），拿到什么显示什么，所以 `T` 直接暴露到页面上。

### 6.3 根因

`application.yml:22`：

```yaml
spring:
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
```

**`spring.jackson.date-format` 只对 `java.util.Date` 生效，管不到 `LocalDateTime`** —— Spring Boot 的经典坑。`LocalDateTime` 由 `JavaTimeModule` 处理，在 `WRITE_DATES_AS_TIMESTAMPS` 关闭（Spring Boot 默认）时按 **ISO-8601** 输出，于是带上 `T`。

旁证：9 个含 `LocalDateTime` 的 VO 里，**只有** `OpportunityRespVO` / `QuotationRespVO` / `OpportunityStageLogRespVO` 手写了 `@JsonFormat`；线索、客户、合同、产品、产品分类、看板的都没写 —— 说明早有人踩过这个坑，但只在自己手上那个 VO 打了补丁。

### 6.4 解决方法

在 `JacksonConfig` 新增一个 `Jackson2ObjectMapperBuilderCustomizer`，给 `LocalDateTime` 装上**成对**的序列化器与反序列化器（pattern `yyyy-MM-dd HH:mm:ss`）。

两条约束：

1. **⚠️ 序列化与反序列化必须成对配置。** 只改序列化器的话，写出去的是空格分隔，而默认反序列化器只认 ISO 的 `T` 分隔 —— 往返一次就解析不回来。
2. **⚠️ 只动 `LocalDateTime`，不碰 `LocalDate`。** 后者的 ISO 默认输出本来就是 `yyyy-MM-dd`（如 `nextContactTime`），正是列表页要的样子。

同时修正了该类已过时的类注释（原文写着「目前只做一件事」）。

> **为什么选框架层而不是逐字段加 `@JsonFormat`**：yml 里那句 `date-format` 本来就声明了全局意图，
> `LocalDateTime` 覆盖不到属于**遗漏**而非有意选择；且逐字段打补丁的做法**已经被证明会漏**
> （9 个 VO 里漏了 6 个，本 BUG 就是这么来的）。这也符合 `JacksonConfig`
> 自己注释里的主张：「放在框架层一次解决，各业务模块不需要逐个加注解，也不会有人漏加」。

### 6.5 验证

| 层 | 方式 | 结果 |
| :--- | :--- | :--- |
| 实测（修复前） | 登录后打真实 `GET /crm/lead` | ✅ 复现 `2026-09-14T09:13:37` |
| 逻辑层 | 用本地 m2 里的**真实** `spring-web 6.2.8` + `jackson 2.18.4` 复刻 Spring Boot 的 builder 配置跑探针 | ✅ 见下表 |
| 编译 | `mvn -o -pl crm-framework/crm-common compile` | ✅ exit 0 |
| **实机回归** | 重启后端后再打 `GET /crm/lead` | ⬜ **未做，线上尚未生效** |

探针结果（复刻 `JavaTimeModule` + yml 的 `date-format` / `time-zone`）：

| 场景 | 序列化输出 | 判定 |
| :--- | :--- | :--- |
| **[A] 对照：只有 `JavaTimeModule`（= 现状）** | `{"createTime":"2026-09-14T09:13:37","nextContactTime":"2026-09-20"}` | 与线上返回**逐字符一致**，证明探针忠实还原了生产 |
| **[B] 加上 `serializerByType` + `deserializerByType`（= 本次改法）** | `{"createTime":"2026-09-14 09:13:37","nextContactTime":"2026-09-20"}` | ✅ 格式正确，`LocalDate` 未被波及 |
| 往返一致性（序列化 → 反序列化） | — | ✅ true |

> 探针存在的意义：**`serializerByType` 注册的序列化器有可能被 `JavaTimeModule` 抢先命中**，
> 那样修复就是无效的。实验证明它确实赢过 `JavaTimeModule`；而 [A] 组能精确复现线上输出，
> 是「[B] 组结论可信」的前提。

### 6.5.1 顺带排查掉的两个风险点

**风险 1：反序列化变严了。** 探针的 [C] 组显示：改完后**带 `T` 的旧 ISO 格式会被拒绝**（`InvalidFormatException`）。为此查了全部请求 DTO —— 含 `LocalDateTime` 的**只有 5 个分页 DTO**（`LeadPageReqDTO` / `CustomerPageReqVO` / `OpportunityPageReqVO` / `QuotationPageReqVO` / `ProductPageReqVO`），而它们**全部是 `@GetMapping` 的查询参数绑定**（走 `@DateTimeFormat`，Jackson 根本不参与）。→ **不受影响**。

> 顺带发现：`LeadPageReqDTO:44` 上原本就写着 `@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")`，
> **与本次的输出格式一致** —— 请求方向与响应方向反而就此对齐了。

**风险 2：商机 / 报价单会不会被带崩。** 它们有手写的 `@JsonFormat`。查下来：`yyyy-MM-dd` 的那几个全在 **`LocalDate`** 字段上（本次未动 `LocalDate`），而 `LocalDateTime` 的 `createTime` / `updateTime` 用的**正是同一个 pattern**。→ 无论字段注解与全局配置谁优先，**输出都一样，零影响**。

### 6.6 行为变更说明 ⚠️

修复后，**除线索外，客户 / 合同 / 产品 / 产品分类 / 看板**的响应时间格式也会一并变成 `yyyy-MM-dd HH:mm:ss`。这是它们本来该有的格式（yml 早已如此声明），但属于**超出「只修线索」范围的可见变更**。

> 前端已确认无任何依赖 `T` 格式的解析代码，因此是安全的。
> 但如果将来有页面用 `new Date('2026-09-14 09:13:37')` 这类写法，注意 **Safari 对空格分隔的解析不兼容** —— 需要时请自行 replace 成 `T` 或用 dayjs 显式 parse。

### 6.7 防范建议

1. **`spring.jackson.date-format` 对 `java.time.*` 无效，这是最容易误判的一条配置。** 看到 yml 里写了就想当然以为全局统一了，实际 `LocalDate` / `LocalDateTime` 全都走 `JavaTimeModule` 的 ISO 默认。需要自定义就必须显式注册 `serializerByType` / `deserializerByType`。
2. **同一个格式问题，逐字段打补丁必定漏。** 本 BUG 的 6 个漏网 VO 就是证据 —— 新增 VO 时没有任何机制提醒你「这里也得加 `@JsonFormat`」。**格式类配置放框架层**，才不会有漏网。
3. **改序列化格式时，反序列化要一起想。** 单向修改会造成「写得出去、读不回来」，而且往往在很久以后才以「缓存/回放解析失败」的形式暴露。要么成对改，要么确认没有反向路径。
4. **全局序列化配置的改动要评估「谁在用这个 ObjectMapper」。** 本次确认项目内的 Redis 访问都走 `StringRedisTemplate`（`SmsCodeServiceImpl` 注释里写明了这是为了避开序列化器互相干扰），且会话对象 `LoginUser` **不含任何时间字段**，所以影响面被限制在 HTTP 响应上。
5. **前端不做任何日期格式化，等于把格式责任全压在后端。** 后端返回什么就显示什么 —— 这在别的项目里未必成立。改动响应格式前，务必确认前端没有隐式依赖。

---

## BUG-007 Lombok 静默跳过访问器：`userName` 与 `username` 并存时，只生成一个

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **后端**（`aurum-crm`，Lombok 注解处理） |
| 文件 | `crm-module-system/.../model/vo/LoginLogRespVO.java` |
| 严重程度 | 中 —— 只卡住编译，不会带病上线；但**报错信息指向别处，极难定位** |
| 状态 | ✅ **已修复**（2026-09-14，编码期发现；编译通过即验证，无运行时回归项） |

### 7.1 现象

新增登录日志模块后，`mvn compile` 报：

```
LoginLogServiceImpl.java:[139,11] 找不到符号
  符号:   方法 setUsername(java.lang.String)
  位置:   变量 vo of type com.aurum.crm.module.system.model.vo.LoginLogRespVO
```

**报错指向 `ServiceImpl`，但问题在 VO 里** —— VO 明明写着 `private String username;`，
类上也有 `@Data`。而且**只有 `setUsername` 报错**，同类的 `setPhone` / `setIp` /
`setLoginTime` 全都正常，看起来完全不讲道理。

### 7.2 根因

**Lombok 判断「这个方法是不是已经有了」时，是大小写不敏感的。**

`LoginLogRespVO` 同时有这两个字段（它们是两个不同业务概念，都不能删）：

| 字段 | 含义 | Lombok 想生成的方法名 |
| :--- | :--- | :--- |
| `userName` | 登陆人姓名 | `getUserName` / `setUserName` |
| `username` | 工号（登陆账号） | `getUsername` / `setUsername` |

两组名字**只差 `N` 一个字母的大小写**。Lombok 认为它们是同一个方法，
于是**每对字段只生成一个** —— 而且**一个错都不报**，只在调用缺失那个的地方炸。

### 7.3 证据

用 Lombok 1.18.38 跑最小复现，两个类的字段不同、其余完全相同：

```java
// A.java
@Data public class A { private String username; }

// B.java
@Data public class B { private String userName; private String username; }
```

| 类 | 字段 | `javap` 结果 |
| :--- | :--- | :--- |
| **A** | 只有 `username` | ✅ `getUsername()` / `setUsername()` **正常生成** |
| **B** | `userName` + `username` | ❌ 只有 `getUserName()` / `setUserName()`，**`username` 的访问器凭空消失** |

A 与 B 唯一差别就是多了个 `userName` —— 这就是直接证据。
（复现命令：`javac -cp lombok.jar -d out A.java B.java && javap -p out/B.class`）

> **还有一个更隐蔽的推论**：缺哪个取决于**书写顺序**。
> B 里 `userName` 写在前面，缺的是 `username`；把两者对调，缺的就变成 `userName`。
> 所以「我在本地明明能编译」也可能是顺序不同，换台机器/换个人改一行顺序就炸。

### 7.4 解决方法

**把这四个访问器全部手写**，不交给 Lombok：

```java
@Schema(description = "工号（登陆账号）")
private String username;

public String getUserName()            { return userName; }
public void   setUserName(String v)    { this.userName = v; }
public String getUsername()            { return username; }
public void   setUsername(String v)    { this.username = v; }
```

Lombok 会跳过（忽略大小写）已存在的方法，不会重复生成，两者可以共存。
字段名**不能改** —— 前端 `login.vue` 的列 `prop` 就叫 `username`。

> **顺带说明：`@Data` 的 `equals` / `hashCode` / `toString` 不受影响。**
> 它们按**字段**生成、不走访问器，所以 `username` 一直被正确包含在内 ——
> 出问题的只有 getter/setter 这一对。

### 7.5 验证

| 层 | 方式 | 结果 |
| :--- | :--- | :--- |
| 复现 | Lombok 1.18.38 跑 A/B 两个最小类并 `javap` | ✅ 见 7.3，根因确证 |
| 编译 | `mvn -o compile` | ✅ `BUILD SUCCESS` |
| 全项目排查 | 扫描所有 `*.java`，找**同时**声明 `private String userName;` 与 `private String username;` 的类 | ✅ **只有 `LoginLogRespVO` 一处**，无其它潜伏点 |

> 本条是纯编译期问题：**编译通过即为验证完成**，没有需要实机回归的运行时行为。

### 7.6 防范建议

1. **别让两个字段名只差大小写。** 这是本次的触发条件。`userName` / `username` 这种组合
   在业务上很常见（一个姓名、一个账号），但放在同一个 DTO 里就是雷 ——
   命名时主动避开（如把后者叫 `loginAccount` / `jobNumber`）。若因前端契约不能改，
   就按 7.4 手写访问器。
2. **看到「找不到符号: 方法 setXxx」而字段明明存在时，先怀疑 Lombok，别去翻调用方。**
   报错位置在 `ServiceImpl`，真凶在 VO —— 顺着报错地址找会白费很久。
   最快的验证：`javap -p` 反编译一下那个类，看方法到底生成了没有。
3. **这类「静默失效」在本项目已经出现多次**（另见 BUG-003 的 `*:*:*` 通配符、
   BUG-006 的 `date-format` 对 `java.time.*` 无效）。
   共同特征：**不报错、不警告，只在别处以完全不相干的错误暴露**。
   凡是「配置/注解写对了却行为不对」的情况，优先怀疑处理它的那一层（Lombok / 框架）**悄悄跳过了**，
   而不是去看业务代码。
4. **`@Data` 不是万能的。** 它默认生成全部访问器，但会因命名冲突、字段是 `final`、
   显式 `@Getter(AccessLevel.NONE)` 等原因静默少生成。字段多、名字接近的 DTO，
   必要时用 `javap` 确认一次。

---

## BUG-008 点一下「关注」，图标要一两秒才变

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **前端**（放大器）+ **环境**（根因） |
| 文件 | `views/crm/lead/index.vue`、`views/crm/customer/index.vue` 的 `onToggleStar`；根因在 `crm-server/src/main/resources/application-dev.yml` |
| 严重程度 | 中 —— 列表页最高频的操作之一，体感「点了没反应」 |
| 状态 | ✅ 前端已修复（两处）；**根因（远程库）未处理，需另行决定** |

### 8.1 现象

点星标关注 / 取消关注，图标要等一两秒才翻转，像是点击没生效。

### 8.2 根因

两层叠加：**根因是环境，前端把成本放大了一倍。**

#### 根因 1（主因 · 环境）：MySQL 与 Redis 都在远程，单次往返约 49 ms

`application-dev.yml:12` 的 MySQL 是 `YOUR_SERVER_IP:3306`，`:37` 的 Redis 是**同一台机器**。直连该库实测：

| 测量项 | 结果 |
| :--- | :--- |
| `ping YOUR_SERVER_IP` | 平均 **49 ms**（44 ~ 57） |
| JDBC 直连执行 `SELECT 1` | **47 ~ 52 ms**（纯网络往返） |
| `SELECT COUNT(*) FROM crm_lead` | **48 ms** |
| `SELECT * FROM crm_lead ORDER BY create_time DESC LIMIT 10` | **56 ms** |
| `SELECT COUNT(*) FROM crm_star WHERE ...` | **52 ms** |

**关键读数：最简单的 `SELECT 1` 和最复杂的查询耗时几乎一样（都在 50ms 上下）。**
说明服务端执行耗时可以忽略，成本 **100% 在网络往返**。

对应到接口实测：

| 接口 | 耗时 |
| :--- | :--- |
| `GET /crm/lead?pageNo=1&pageSize=10` | 559 / 562 / 572 ms |
| `GET /crm/lead/{id}` | 542 ms ← **只查一条记录也要半秒** |
| `POST /crm/lead/{id}/star` | 655 ms |
| `POST /crm/lead/{id}/star`（第 2 次，幂等分支） | 549 ms |

**550 ms ÷ 49 ms ≈ 每次请求 11 次网络往返。**

按 `LeadServiceImpl` 能直接数出列表的 5 条 SQL：

| 出处 | SQL |
| :--- | :--- |
| `leadMapper.selectPage(...)` | COUNT + SELECT（MyBatis-Plus 分页是两条）= **2** |
| `toPageResult` → `ownerNameMap` | 查负责人姓名 = **1** |
| `toPageResult` → `userNameMap` | 查创建人姓名 = **1** |
| `toPageResult` → `starService.starredBizIds` | 查关注态 = **1** |

剩余往返来自框架层：Sa-Token 的 Redis 会话校验与权限读取、数据权限拦截器、连接有效性校验等。

#### 根因 2（放大器）：一次点击发两个请求

`onToggleStar` 原本是「发请求 → 无条件 `reload()`」，而 `reload()` 会把整页重新拉一遍（即上面那 11 次往返）：

```
starLead(row.id)        // 655 ms
listRef.value?.reload() // +560 ms   ← 只为了翻转一个小图标
```

**合计约 1.2 秒**，而且图标要等两步都跑完才翻转。

> 这段 `reload()` 是有理由的（原注释：「让『我关注的线索』口径与图标保持一致」）——
> 在「我关注的线索」筛选下，取消关注确实会让这一行从结果集里消失。
> 但它被**无条件**执行了，于是其余 90% 的场景都在为一个小图标重拉整页。

### 8.3 解决方法

前端改为**乐观更新 + 去掉无条件重取**（线索、客户两处同一套做法）：

```js
const wasStarred = row.starred
const needReload = listRef.value?.getQuery?.().scope === 'starred'

row.starred = !wasStarred          // 乐观翻转：图标立刻响应
try {
  await (wasStarred ? unstarXxx(row.id) : starXxx(row.id))
  if (needReload) listRef.value?.reload()
} catch {
  row.starred = wasStarred         // 失败回滚，别让图标和库里的状态对不上
}
```

两个要点：

1. **先翻转再发请求**，失败回滚 —— 把「等一次网络往返」变成「0 延迟反馈」。
2. **只在 `scope === 'starred'` 时才真重取**：只有那个口径下行的**集合**会随关注态改变；其余口径星标只影响图标本身，本地翻转即可。当前口径从 `ProList` 已 `defineExpose` 的 `getQuery()` 读。

### 8.4 验证

| 层 | 方式 | 结果 |
| :--- | :--- | :--- |
| 静态检查 | `npm run check:static` | ✅ 全部通过（182 个请求） |
| api/mock | `npm run check:mock` | ✅ 66 项全通过 |
| 构建 | `npm run build` | ✅ exit 0 |
| **响应式前提** | 确认 `composables/useTable.js:24` 的 `list` 是 `ref([])`（**深**响应式） | ✅ 改 `row.starred` 会触发图标重渲染 |
| **实机回归** | 浏览器点星标 | ⬜ **未做** |

> 最后一行「响应式前提」是本次改动的**命门**：如果 `list` 是 `shallowRef`，
> 直接改 `row.starred` 不会触发任何重渲染，图标会**一直不翻** —— 比修复前更糟。
> 所以专门查了定义处。将来若有人把 `ref` 改成 `shallowRef`（大列表性能优化时很可能这么做），
> 这里会静默失效。

### 8.5 未处理的根因 ⚠️

**前端这次只是把「1.2 秒」砍成「约 0.65 秒」，根因没动。**

49 ms × ~11 次往返 = 550 ms 是**所有操作**的地板，不只是关注 —— 保存、翻页、切菜单、发消息，每一项都背着这半秒。**代码层面已经没有多少余量可挤**（列表那 5 条 SQL 每条都是批量查询，没有 N+1）。

要根治只有一条路：**把 MySQL / Redis 挪到本机或就近机房**（改 `application-dev.yml` 两个 host）。单次往返从 49 ms 降到 1 ms 以内，**每个请求从 ~550 ms 掉到 ~10 ms** —— 是数量级的差别，比任何代码优化的收益都大。

> 另外两个顺带发现（未处理）：
> - 直连时**新建连接耗时 5112 ms**（含 DNS + 握手 + 认证）。有连接池兜着平时不发作，但连接被回收后重建会非常痛。
> - Druid 配置 `initial-size: 5` 但 `min-idle: 10`，**两者不一致**（`initial-size` 应 ≥ `min-idle`）。

### 8.6 防范建议

1. **列表页的「单行状态切换」不要用整表 `reload()`。** 增删改（行的集合变了）才需要重取；改一行的一个字段，本地改即可。这是通用规则，本次线索和客户两处都犯了。
2. **乐观更新必须配回滚。** 只翻转不回滚，接口一失败图标就和库里长期不一致，而且**没有任何报错**，比慢更难查。
3. **先确认状态容器是深响应式再用乐观更新。** `ref` 可以，`shallowRef` / `markRaw` 不行（见 8.4）。列表性能优化时常有人改这两处，改之前请连带检查本 BUG 涉及的两处 `onToggleStar`。
4. **看到「所有接口都慢半秒左右」时，先量网络往返，别急着读代码。** 本次定位路径是：`ping` → JDBC 直连 `SELECT 1` → 发现「最简查询与最复杂查询耗时相同」→ 判定成本全在网络 → 用 `请求耗时 ÷ 往返耗时` 反推往返次数。这套方法比逐个接口查 SQL 快得多。
5. **开发环境的库放远程，会把所有性能优化都变成无用功。** 本次即使把前端优化到极致，也仍有 550 ms 的地板 —— 而它本可以是 10 ms。

---

## BUG-009 报价单表单里一选「报价商机」就崩：`getOpportunityDetail` 漏了 import

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **前端**（`aurum-crm-ui`） |
| 文件 | `src/views/crm/quotation/index.vue` |
| 严重程度 | 中 —— 新增/编辑报价单时选一次商机就 `ReferenceError`，折后价不再联动 |
| 状态 | ✅ **已修复**（2026-09-14） |

### 9.1 现象

在报价单列表点「新增报价」，选择或更换「报价商机」下拉里的任何一条，浏览器控制台抛
`ReferenceError: getOpportunityDetail is not defined`，弹窗里的「整单最终价（含税）」从此不再按商机折扣联动。

### 9.2 根因

文件里有一个监听商机变化的 `watch`：

```js
watch(() => form.value.opportunityId, async (opportunityId) => {
  ...
  const detail = await getOpportunityDetail(opportunityId)   // ← 用了
})
```

**但 `@/api/opportunity` 的 import 列表里没有它** —— 那一行列了 8 个函数，唯独漏了这个。

它躲过了两道关：

- `vite.config.js` 的 `AutoImport` 只自动导入 `vue` / `vue-router` / `pinia`，**不会兜住 api 函数**；
- `npm run build` 也不报 —— rollup 不检查「用了但没声明的全局变量」，它只会当成运行时全局变量。

这正是第 19 条记的那一类坑的又一例（前两例是 `onAction` 漏解构 `rows`、`leadToPool` 漏 import `nextId`）。

### 9.3 修复

把 `getOpportunityDetail` 补进 `@/api/opportunity` 的 import 列表。

### 9.4 验证

`npm run check:static` 的「模板事件处理器都在 script 里定义」这一项只覆盖**模板里的事件处理器**，
抓不到 script 内部的裸标识符 —— 所以这一条**目前没有自动检查兜底**，
只能靠 review 或实机点击验证。修复后实机在弹窗里选/换商机不再报错、折扣正常联动。

### 9.5 防范建议

**改完 `src/api/*.js` 或页面里的 `onAction` / `watch` 之后，跑一遍 `npm run check:mock`**，
它是目前唯一能覆盖 api 层的检查。更根本的办法是给 `check:static` 加一项
「script 里出现的裸标识符必须在文件内声明或已 import」—— 但那需要真正的 JS 解析，
超出当前正则式检查器的能力范围（见第 8 项的已知局限）。

---

## BUG-010 客户模块单测在 HEAD 上就是红的：`CustomerServiceImplTest` 缺 `StarService` mock

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **后端**（`crm-module-crm`） |
| 文件 | `src/test/java/com/aurum/crm/module/crm/service/CustomerServiceImplTest.java` |
| 严重程度 | 中 —— `mvn test` 常红，真实失败会被这条噪音淹没 |
| 状态 | ⚠️ **第一次「已修复」是错的，实际未落地；2026-09-14 第二轮才真正修好** |

> ⚠️ **本条曾经谎报军情，值得单独记一笔**：2026-09-14 首次记录时状态写的是「✅ 已修复」，
> 还附了验证数字 `Tests run: 57, Failures: 0, Errors: 0`。**但 HEAD 上根本没有那个 `@Mock`**，
> 整个 `crm-module-crm/src/test/` 下没有任何文件 mock 过 `StarService`，
> 而当时的实际套件规模是 19 个用例、不是 57 个 —— 那串数字对不上任何一次真实运行。
>
> 第二轮审查时 `mvn test` 依然复现同一条 NPE。教训见 10.6。

### 10.1 现象

`mvn -pl crm-module-crm -am test` 里 `CustomerServiceImplTest.pageQuery_keywordCoversPhone` 报：

```
NullPointer Cannot invoke "com.aurum.crm.module.crm.service.StarService.starredBizIds(int, java.util.Collection)"
because "this.starService" is null
```

**与本次改动无关**：客户模块接入关注功能（`starred` 字段 + `scope=starred` 胶囊）时给
`CustomerServiceImpl` 加了 `StarService` 依赖，但它的单测没同步加 mock —— 也就是说这条在提交时就已经是红的。

### 10.2 根因

`@InjectMocks` 只能注入**测试里声明过的** `@Mock`。缺声明的字段在单测里就是 `null`
（Spring 在真实运行时会注入，所以只有单测能发现）。`CustomerServiceImpl#toPageResult`
无条件调用 `starService.starredBizIds(...)`，于是空页也照样 NPE。

### 10.3 修复

1. 补上 `@Mock private StarService starService;`；
2. 在该测试里按契约打桩 `starredBizIds(CUSTOMER, List.of()) → Set.of()`
   （`StarService` 的 javadoc 写明「为空返回空集」，而 mock 的缺省返回值是 `null`）。

### 10.4 验证（2026-09-14 第二轮，实测）

```
mvn test -pl crm-module-crm,crm-module-system,crm-module-finance,\
         crm-framework/crm-common,crm-framework/crm-mybatis,crm-framework/crm-security
→ CustomerServiceImplTest: Tests run: 13, Failures: 0, Errors: 0
→ BUILD SUCCESS
```

顺带补了一个回归用例 `pageQuery_allRowsInPool_doesNotThrow`（见 BUG-012）。

> ⚠️ 别信这里写过的 `Tests run: 57` —— 那是错的，见上面的状态说明。
> **核对测试结果请跑命令，不要抄文档里的数字。**

### 10.5 防范建议

**给 Service 加一个 `@Resource` 依赖时，顺手在它的单测里加同名 `@Mock`** ——
漏了的症状是一句 NPE，而报错信息里的 `this.xxxService is null` 很容易被当成业务代码的空指针去查。
`@InjectMocks` + 缺失 mock 是静默的，没有编译期提示。

### 10.6 为什么第一次「修复」没有落到代码里（比 bug 本身更值得记）

推测成因，任一条都足以造成这个结果：

1. **改在别处、没提交** —— 比如改在另一个检出目录 / worktree，或改完被 `git checkout` 冲掉了；
2. **照着「根因分析」补写了「修复」和「验证」两节，但从没真正应用** ——
   这正是「文档腐烂」里最危险的一种：**不是过时，而是从未为真**。
   过时的文档还能靠「以代码为准」纠正，这种连纠正的线索都没有。

由此得到的硬性规则：

1. **写进「验证」一节的内容，必须是从终端原样复制的结果**（含命令本身）。
   凭印象写一个 `Tests run: 57, Failures: 0` 这种「看起来很对」的数字，
   危害远大于不写 —— 它让人彻底放弃复核。
2. **状态改「已修复」之前，先 `git status` / `git diff` 确认改动真的在工作区里。**
   「我记得改过」不等于「改动在 HEAD 上」。
3. **单测转绿是这类修复唯一的证据**。`starService is null` 这种，只要跑一次 `mvn test`
   就能证明真伪 —— 一条命令的代价，却被跳过了。

---

## BUG-011 四个前端自检脚本全都跑不起来（两个各自独立的根因）

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **前端**（`aurum-crm-ui` 的 `scripts/`） |
| 文件 | `scripts/check-static.mjs`、`scripts/check-api-align.js`、`scripts/gen-api-status.js`、`scripts/gen-menu.js` |
| 严重程度 | 高 —— 四个自检形同不存在，而它们的失败看起来像「环境问题」 |
| 状态 | ✅ **已修复**（2026-09-14） |

### 11.1 现象

- `npm run check:static`：`Error: Command failed: find src -name "*.vue"` +
  `Access denied - SRC / File not found - -NAME`
- `npm run check:api` / `gen:api-status` / `gen:menu`：`找不到接口文档：.../crm-ui/aurum-crm/docs/05-接口文档.md` 后 `exit 1`

两类报错**都出现在脚本自身**，而不是「检查不通过」。这种失败方式最危险：
看到「脚本跑不起来」的第一反应是「我这环境有问题」，于是整道关卡被绕过。

### 11.2 根因（两个各自独立）

**根因 A —— `find` 是 Unix 假设。** `check-static.mjs` 用
`execSync('find src -name "*.vue"')` 收集文件。Windows 的 PATH 里 `find` 是
`C:\Windows\system32\find.exe` —— 一个**完全不同的程序**（在文件里搜文本），
根本不认 `-name`。这个脚本在 Windows 上从来没有真正执行过一次。
（另外 `execSync` 默认用管道捕获子进程输出，在受限 shell 里还会 `EPERM: spawnSync cmd.exe`。）

**根因 B —— 后端目录路径写死。** 三个脚本各自写了
`path.resolve(import.meta.dirname, '../../aurum-crm/...')`，即「后端是 `aurum-crm-ui` 的同级目录」。
但后端与前端常常是两个各自独立的仓库，至少存在三种布局：

```
crm-ui/aurum-crm/                 ← 脚本假设的
crm_cp/crm/aurum-crm/             ← 后端在另一个仓库的 crm/ 下（本仓库当前检出）
crm_cp/aurum-crm/                 ← 两个仓库并列
```

后两种布局下三个脚本一律 `exit 1`。而这正是第 26 条记过的教训的重演：
**同一段逻辑在两个脚本里各写一份，必然有一份先腐烂**（当初 url 抽取就是这么烂掉的）。

### 11.3 修复

1. `check-static.mjs` 的 `execSync('find ...')` 改成 `fs.readdirSync` 递归收集，
   不再依赖任何外部命令（顺带消掉管道 EPERM）；
2. 新增 `scripts/lib/backend-paths.js`，按候选列表探测后端目录并导出 `backendFile(相对路径)`，
   三个脚本改为引用它。找不到时把**试过哪些路径**打印出来 ——
   只说「找不到」会让人以为是自己敲错了命令，列出候选一眼就能看出是布局变了。

### 11.4 验证

| 命令 | 修复前 | 修复后 |
| :--- | :--- | :--- |
| `npm run check:static` | `Command failed: find src ...` | 12 项**全部通过** |
| `npm run check:api` | 找不到接口文档，exit 1 | 189 个请求全部能在文档里找到 |
| `npm run gen:api-status` | 找不到接口文档，exit 1 | 正常生成 `docs/接口清单.txt` |

顺带把 `check:static` 补进了《开发注意事项》的自检脚本表 —— 它原先**不在那张表里**，
这也是它长期没人跑的原因之一。

### 11.5 防范建议

1. **脚本里不要用 `find` / `grep` / `sed` 这类 Unix 命令**，用 Node 的 `fs` ——
   项目在 Windows 上开发，PATH 里的同名程序可能是完全不同的东西。
2. **自检脚本的报错要说清是「检查不通过」还是「脚本没跑起来」**，
   后者要打印它试过的路径 / 命令。否则读者会把两者混为一谈然后跳过。
3. **加自检项时先确认它在当前开发机上真的能跑出结果** —— 一个从没成功执行过的检查，
   比没有检查更糟：它让人以为这一块已经被覆盖了。

---

> **以下 BUG-012 ~ BUG-016 是 2026-09-14 第二轮代码审查（专项找 bug）发现的。**
> 同批修正了 BUG-010（见 10.6）。这几条的共性是：**编译和三个前端自检脚本全绿，
> 但语义层是坏的** —— 所以「构建通过」不能当作「这一块没问题」。

## BUG-012 客户列表整页都是公海客户时整个接口 500：空 Map 用了 `Map.of()`

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **后端**（`aurum-crm`，`crm-module-crm`） |
| 文件 | `CustomerServiceImpl.java:493`（`ownerNameMap`）；同型潜伏于 `CustomerPoolServiceImpl.java:93` |
| 严重程度 | 高 —— 整页 500，且触发条件（搜索公海客户）非常日常 |
| 状态 | ✅ **已修复**，并补了回归用例（2026-09-14） |

### 12.1 现象

`GET /crm/customer`（以及 `/crm/customer/simple`）在**某一页的结果全是公海客户**时返回 500。

**复现**：客户列表搜索框输入「武汉光谷」（种子数据里 id=7 的公海客户）→ 结果只有这一个没有负责人的客户 → 整页 500。

### 12.2 根因

```java
if (ownerIds.isEmpty()) {
    return Map.of();              // ← 空的时候返回不可变 MapN
}
...
vo.setOwnerName(ownerNameMap.get(c.getOwnerUserId()));   // owner 为 null 时
```

`Map.of()` 返回的是 `ImmutableCollections.MapN`，它的 `get()` **在 `size == 0` 时先
`requireNonNull(key)`**，然后才返回 null。所以 `Map.of().get(null)` 抛 NPE。

在本机 JDK 17.0.19 实测（**非空也一样**，这点容易误判）：

| 表达式 | 结果 |
| :--- | :--- |
| `Map.of().get(null)` | **NullPointerException** |
| `Map.of().getOrDefault(null, "x")` | **NullPointerException** |
| 非空 `Map.of(1,"a").get(null)` | **NullPointerException** |
| `Collections.emptyMap().get(null)` | `null`（正常） |

为什么公海行会进到列表里：数据权限拦截器 `DataPermissionInnerInterceptor` 的**每一个**
data_scope 分支都注入 `(owner_user_id IS NULL OR ...)`（公海对所有人生效可见，决策 4），
而 `CustomerServiceImpl.pageQuery` 没有任何「owner 非空」的过滤。

### 12.3 修复

两处 `Map.of()` → `Collections.emptyMap()`，并补上与其他模块一致的警示注释。

**这个坑项目里已经踩过八次了**：`LeadServiceImpl`、`ProductServiceImpl`、
`ProductCategoryServiceImpl`、`OpportunityServiceImpl`、`QuotationServiceImpl`、
`ContractServiceImpl`、`DashboardServiceImpl`、`LeadPoolServiceImpl` 全部已经改用
`Collections.emptyMap()`，**每一处都带着指名道姓的注释**「必须用 emptyMap 而不是 Map.of()，
否则 get(null) 抛 NPE」—— 只有客户模块漏了。

顺带排查了其余所有 `Map.of()` 站点，结论是**安全的**：`DictLabels` 与 `UserVoAssembler`
有 `code == null / key == null` 前置守卫，`TreeServiceImpl`、`RoleServiceImpl` 在调用处
先判 key。**不要**把它们一起改掉。

### 12.4 验证

新增 `CustomerServiceImplTest#pageQuery_allRowsInPool_doesNotThrow`：
造一个只有公海客户（`ownerUserId = null`）的分页结果，断言不再抛异常。
`CustomerServiceImplTest: Tests run: 13, Failures: 0, Errors: 0`。

### 12.5 防范建议

1. **空集合一律用 `Collections.emptyMap()` / `emptyList()`**，不要用 `Map.of()` / `List.of()`
   去当「空值」的占位 —— 它们的元素/键不允许为 null，这是刻意的设计，不是 bug。
   可以直接搜 `Map.of()` 做静态检查项。
2. **`Map.get(null)` 看起来是安全的，实际上取决于 Map 的实现类** ——
   这个「同一行代码行为不同」正是它反复复发的原因。查表前先判 key 是唯一稳妥的写法
   （见 `UserVoAssembler#lookup` 的范式）。

---

## BUG-013 「本部门及以下」看不到自己的直属子部门

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **后端**（`aurum-crm`，`crm-module-system`） |
| 文件 | `AuthServiceImpl.java:394`（`resolveDataScopeDeptIds` 的 `DEPT_AND_CHILD` 分支） |
| 严重程度 | 中 —— fail-closed（少看数据、不越权），但「本部门及以下」实际退化成「本部门」 |
| 状态 | ✅ **已修复**（2026-09-14） |

### 13.1 现象

`data_scope = 4`（本部门及以下）的账号**看不到本部门直属子部门的数据**，
只能看到本部门自己和「孙子辈及更深」的部门。最典型的受害者是种子里专门用来演示层级的
**销售总监**：他看不到销售一组 / 二组 / 三组。

### 13.2 根因

`sys_dept.ancestors` **不含自己**（种子数据里 dept 2「销售部」是 `'0,1'`，
它的三个子部门 60/61/62 是 `'0,1,2'`）。而原实现拼的前缀带了**尾逗号**：

```java
String prefix = ancestors + "," + dept.getDeptId() + ",";   // "0,1" + "," + "2" + "," = "0,1,2,"
... .likeRight(SysDeptDO::getAncestors, prefix)             // → LIKE '0,1,2,%'
```

直属子部门的 `ancestors` 恰好是 `'0,1,2'` —— 逗号后面**没有任何字符**，
所以 `LIKE '0,1,2,%'` 匹配不上它，只能匹配到 `'0,1,2,X...'` 这种更深的层级。

### 13.3 修复

拆成两条并列条件，前缀不再带尾逗号：

```java
String selfPrefix = ancestors + "," + dept.getDeptId();      // "0,1,2"
.and(w -> w.eq(SysDeptDO::getAncestors, selfPrefix)          // 直属子部门（原先漏的就是这一类）
          .or().likeRight(SysDeptDO::getAncestors, selfPrefix + ","))   // 更深的后代
```

### 13.4 验证

`assertEquals` 级别的单测**尚未补**（`AuthServiceImpl` 的依赖较多，本轮先保行为正确）。
核对方式：拿线上库确认 dept 2 的子部门是 60/61/62；改动后销售总监（role 2，dept 2）
应能看到这三个部门的数据。**这条建议实机回归一次。**

### 13.5 防范建议

**用 `ancestors` 做前缀匹配时，永远先想清楚「树里存的是不含自己的祖先链还是含自己的路径」**，
并把答用注释钉在代码旁。尾逗号前缀是个经典的差一位错误，
`A` 与 `A,X` 这两类节点必须分别处理。

---

## BUG-014 合同审批的并发竞态：重复提交建出两套审批实例，重复审批会让节点永久卡死

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **后端**（`aurum-crm`，`crm-module-crm`） |
| 文件 | `ContractServiceImpl.java` 的 `submit` / `audit` / `resolveApprovers` |
| 严重程度 | 高 —— 最坏情况下合同的一个审批节点**再也审不动**，只能改库 |
| 状态 | ✅ **已修复**，并有并发单测覆盖（2026-09-14） |

### 14.1 现象

同一个合同双击「提交审核」或双击「通过」，可能：

1. 建出**两套** `sys_approval_instance` + 待办任务（一个实例随后被永久遗留为 `status=0` 的孤儿）；
2. 更严重：同一节点被推进两次 → 下一节点的待办**被插两遍** →
   之后该节点审批时，`audit` 里**没有 `LIMIT` 的 `selectOne`** 撞上
   `TooManyResultsException`（MyBatis-Plus 3.5.7 的 `selectOne` 多于一行即抛）→
   **这个节点从此既不能通过也不能驳回**。

### 14.2 根因

两处都是典型的 check-then-act，**而且数据库拦不住**：

| 位置 | 问题 |
| :--- | :--- |
| `submit` | 守卫是「读」`contract.getStatus()/getAuditStatus()`；随后 `insert(instance)`、`createTasks(...)`、`updateById(contract)` **全部无条件执行**。`updateById` 按主键更新，WHERE 里只有 `contract_id` —— 并发双方都会「成功」 |
| `audit` | 认领任务写成 `task.setTaskStatus(1); approvalTaskMapper.updateById(task)` —— 同样无 `WHERE task_status = 0` 守卫 |

**DDL 也兜不住**（这是本条的判定关键，见 `sql/crm.sql`）：

- `sys_approval_instance` 上只有 `KEY idx_biz (biz_type, biz_id, status)` —— 普通索引，**不是 UNIQUE**；
- `sys_approval_task` 上只有 `PRIMARY KEY (task_id)` 与 `idx_dodo` / `idx_instance` —— 没有
  `(instance_id, node_order, approver_id)` 的唯一约束。

`audit` 为什么会导致「插两遍」：两个事务各自把 `task_status` 写成 1，**各自读到自己写进去的值**，
于是双方在「本节点还有没有待办」那一步都判断为「没有了」，双双执行 `createTasks`。

### 14.3 修复

按项目里已有的范式（`QuotationServiceImpl#convertToContract` / `#voidQuotation`）改成
**条件 UPDATE + 影响行数判断**，并且**顺序是「先抢占、再建副作用」**：

1. `submit`：在**所有校验之后、任何写操作之前**插入一次 CAS
   （`WHERE contract_id=? AND status=0 AND audit_status IN (0,3) SET audit_status=1`），
   `affected == 0` 直接抛错 —— 败者根本走不到建实例那一步；
2. `audit`：任务认领改成
   `WHERE task_id=? AND task_status=0 SET task_status=1, ...`，`affected == 0` 抛
   `APPROVAL_TASK_HANDLED(4005)`；
3. 顺带修掉同一故障模式的另一条路径：`resolveApprovers` 的 case 1（指定人员）
   缺 `.distinct()`（case 2 角色分支一直有），节点配置里把同一人填两次就会
   给同一个人插两条待办，同样导致上面的 `selectOne` 崩溃 —— 现已补 `filter(nonNull).distinct()`。

### 14.4 验证

新增 `ContractServiceImplTest`（2 个用例）：

- `audit_whenTaskAlreadyClaimed_throwsAndStopsBeforeAnyWrite`：CAS 影响 0 行时断言抛
  `4005`，且 `verify(approvalInstanceMapper, never()).updateById(...)`、合同也不被改；
- `audit_whenTaskClaimSucceeds_passesTheClaim`：CAS 抢到时正常放行。

第一个用例是**有效**的：把实现还原成 `updateById(task)` 它会失败。
`Tests run: 2, Failures: 0, Errors: 0`。

⚠️ **未做**：没有实机并发压测（本机跑不起集成测试，见下）。CAS 的正确性靠代码审查 + 单测保证。

### 14.5 防范建议

1. **状态机流转一律用「条件 UPDATE + 判影响行数」，不要用 `updateById`。**
   项目里已经有正确范式（报价单模块），新写状态流转时照抄即可。
2. **需要先「读」时，把读的用途限定为「给用户友好报错」**，真正的裁决必须交给 CAS。
3. **`selectOne` 遇到没有唯一约束保护的表要格外小心** —— 多于一行它直接抛异常。
   要么加 `LIMIT 1`，要么补唯一索引。审批任务这种「天然应当唯一」的表
   **建议补上 `UNIQUE (instance_id, node_order, approver_id)`**（属于 DDL 变更，
   由于 `sql/crm.sql` 会 DROP 重建全表，本轮没有擅自改）。

---

## BUG-015 邮箱验证码可被无限次猜测（没有失败次数上限）

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **后端**（`aurum-crm`，`crm-module-system`） |
| 文件 | `EmailCodeServiceImpl.java`（`VERIFY_SCRIPT`）、`AuthConstants.java`、`ErrorCode.java` |
| 严重程度 | 高 —— 这是邮箱验证码登录唯一的防爆破闸门 |
| 状态 | 🟡 **代码已修复；Java 分支有单测，Lua 脚本本身未经执行验证** |

### 15.1 现象

验证码是 6 位数字（100 万种），而**校验失败不会作废验证码、也没有任何失败次数上限**。
攻击者拿一个验证码的有效期（5 分钟）可以无限次猜；而 60 秒的重发限流是**按邮箱**的，
只要定期重发就能把这个窗口无限续期 → 无界爆破，成功后还能用
`/auth/password/forgot` 直接改密码。

### 15.2 根因

原 Lua 脚本的**顺序是有意的**（注释里写明了「先比对、后删除，验证通过才失效」），
但只考虑了「防双花」，漏了「防爆破」：

```lua
if v ~= ARGV[1] then return 0 end     -- 猜错：直接返回，什么都不做
```

全项目 grep 不到任何失败计数 / 账号锁定。作者已经想到用**统一文案**防枚举
（`不区分「过期」和「填错」`），但统一文案只挡信息泄露，不挡次数。

### 15.3 修复

把失败计数放进**同一段 Lua**（否则「读计数 → 判断 → 写计数」之间又有窗口）：

1. 猜错时 `INCR auth:email:fail:{email}`，首次设置 TTL（与验证码同为 300 秒）；
2. 达到 `EMAIL_CODE_MAX_ATTEMPTS = 5` 时**删掉验证码**并返回 `-2`；
3. 返回 `-2` 时 Java 侧抛新错误码 `EMAIL_CODE_TOO_MANY_ATTEMPTS(1011)` ——
   必须与 `EMAIL_CODE_ERROR` 分开，否则用户会对着一个已作废的码反复重试、永远进不去；
4. 验证码**不存在**时也检查一次计数（这样「次数用尽」的提示能稳定重复出现，
   而不是第 6 次突然变成「验证码已过期」）；
5. `sendCode` 成功发信后**清零计数** —— 否则错满 5 次的用户重发后仍然是被锁死状态。

### 15.4 验证

- Java 侧分支有单测 `EmailCodeServiceImplTest`（6 个用例，覆盖 `1 / 0 / -1 / -2 / null`
  与「两个 key 都必须传给 Lua」），`Tests run: 6, Failures: 0, Errors: 0`；
- ⚠️ **Lua 脚本本身没有被任何测试执行过** —— 本机没有 Redis / Lua / Docker，
  跑不了它。**改动这段脚本后必须手工验一次**（步骤写在测试类的 javadoc 里）：
  同一邮箱连错 6 次，用 `redis-cli` 确认 `auth:email:fail:*` 逐次递增且带 TTL、
  `auth:email:code:*` 在第 5 次后变为 nil。

### 15.5 防范建议

1. **任何「短码」验证都要有次数上限** —— 位数少就是低熵，限流只能挡「重发」，
   挡不住「对同一个码一直猜」，两者是正交的。
2. **计数与消费必须在同一段 Lua / 同一个原子操作里**，否则就是把双花问题原样搬到计数器上。
3. **测试覆盖不到的地方要在注释里写明「这里没被测到、怎么手工验」** ——
   比让后来者以为它被覆盖了要好。

---

## BUG-016 八个 Controller 完全没有权限注解，接口与授权模型脱节

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **后端**（`aurum-crm`，`crm-module-crm` + `crm-module-finance`） |
| 文件 | `LeadController`、`LeadPoolController`、`OpportunityController`、`QuotationController`、`ProductController`、`ProductCategoryController`、`PaymentController`、`InvoiceController` |
| 严重程度 | 中高 —— 前端藏了按钮，后端却照常执行 |
| 状态 | ✅ **已补齐 68 处注解**；⚠️ 未实机回归 |

### 16.1 现象

上述 8 个 Controller 共 **68 个端点**没有任何 `@SaCheckPermission`，
而同项目的 `CustomerController` / `ContractController` 各有 13 处。
后果具体是：

- **财务导出越权**：`GET /finance/payment/export`、`/finance/invoice/export` 等
  没有任何校验，而种子里 `fin:payment:export` **只授给了财务和超管**，
  销售角色本来没这个权限 —— 前端藏了按钮，接口却照常导出；
- **产品可被任意登录用户改写**：`crm_product` 没有 `owner_user_id` / `dept_id`，
  **也不在数据权限白名单里**，所以没有任何行级兜底 ——
  任何登录账号都能增删改、上下架任意产品（例如财务删掉产品）；
- 其余模块至少还有数据权限拦截器兜住行级范围。

### 16.2 根因

各 Controller 的 javadoc 里都写着理由是「**本模块没有加 `@SaCheckPermission`，
原因与客户 / 产品模块一致 —— 登录鉴权与 `LoginUser.permissions` 的落地程度尚不足以
让加注解不误伤联调**」。但这个立论**本身就是错的**：客户模块有 13 个注解、
合同模块有 13 个，而且已经在正常工作。所以这是一笔「理由过期的欠账」，不是有意设计。

> 另一处易踩的坑：这几个文件里"看起来有 1~2 个权限注解"是**假象** ——
> 那是 javadoc 注释里在**讨论** `@SaCheckPermission`。用
> `grep -c 'SaCheckPermission'` 会数错，必须用 `grep -c '@SaCheckPermission('`。

### 16.3 修复

补齐 68 处，权限码**全部取自 `sql/crm.sql` 的 `sys_menu`，不新增任何权限点**
（不在种子里的码会让所有非超管账号 403）。种子里没有对应权限点的端点，
按 `CustomerPoolController` 已有的先例复用语义最近的码：

| 情形 | 处理 |
| :--- | :--- |
| 无 `:export` 权限点（leadPool / quotation / product / productCategory） | 两个导出端点用 `:list` |
| 详情、选择器、漏斗、阶段历史、关注 / 取消关注 | `:list` |
| 无 `:convertContract`（报价单转合同）、无 `:void`（报价单作废） | `:update` |
| 无独立的 replace 权限点（商机明细整份覆盖） | `:addProduct` |

同时**改掉了那些已经变成假话的 javadoc**（原文写着「本模块没有加 @SaCheckPermission」），
换成实际用的权限码清单 + 「不要凭空造新码」的告警。

### 16.4 验证

编译通过；**逐条核对了 68 个注解与其所属方法的 `@Operation(summary)` 一一对应**。

⚠️ **未实机回归**。执行前请注意这次是**真的开始拦了**，以下行为会变化：

| 变化 | 说明 |
| :--- | :--- |
| 产品写操作变成**只有超管** | 种子里销售总监 / 专员对产品只有 `:list`，财务完全没有产品菜单。这是**授权模型本就如此**，之前是「任何登录用户都能改」 |
| 财务导出对销售角色返回 403 | 与种子的授权一致 |
| 销售专员删除线索 / 商机等会 403 | 种子里没有授予这些码 |

**读路径已逐个核对过是安全的**：各角色对涉及的模块都有 `:list`；财务页面只调财务接口；
全局 layout / store 在页面加载时只调 auth 与 ai，不碰这些接口。
所以**浏览不会坏**，受影响的只有越权写操作。

### 16.5 防范建议

1. **新增 Controller 时对照同模块已有 Controller 补齐注解** ——
   `CustomerController` / `ContractController` 就是模板。
2. **写权限码前先在 `sql/crm.sql` 的 `sys_menu` 里搜一下它存不存在**，
   不存在就复用最近的码，**不要造新码**（会 403 打到所有非超管）。
3. **前端还没有 `v-permission` 指令**（README 待办第 8 条）——
   所以现在会出现「按钮看得见、点了 403」。这是补齐权限后**暴露出来的既有缺口**，
   不是新引入的 bug，但建议尽快补上前端指令。

---

## 第二轮审查的元结论

1. **「构建通过 + 自检脚本全绿」与「功能正确」无关。** 本轮五条 bug 全部存在于
   编译通过、`check:static` / `check:mock` / `check:api` 全过的代码里。
2. **文档比代码更容易腐烂，而且烂得更隐蔽。** 本轮同时遇到两个方向：
   BUG-010 谎报已修复（**从未为真**），而「公海/线索池领取的并发控制是裸的」
   是**反向错误**（`README` / `08-当前进度` 里这么写，但 `CustomerPoolServiceImpl#claim:161`
   明明是写对了的原子 CAS）。
   **查询完成度请用命令，不要抄文档。**
3. **同类缺陷会成片出现。** `Map.of().get(null)` 在八处修过、第九处又漏；
   `resolveApprovers` 的两个分支只给其中一个加了 `distinct()`；
   八个 Controller 一起漏权限注解。**修一处时顺手 grep 同类模式**，收益极高。

---

## BUG-017 前端没有消费权限点：按钮看得见、点了 403

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **前端**（`aurum-crm-ui`） |
| 文件 | `src/directives/permission.js`（新增）、`src/main.js`、`src/components/ListToolbar.vue`、19 个列表页的 `TOOLBAR`、6 个详情页/行内按钮 |
| 严重程度 | 中 —— 不是安全漏洞（后端已经拦住），是**界面与权限模型脱节**导致的操作困惑 |
| 状态 | ✅ **已实现**（2026-09-14），`check:static` / `check:mock` / `build` 全过；⚠️ 未实机点验 |

### 17.1 现象

这是 BUG-016 的**连带暴露**，不是新引入的 bug，但会立刻被用户看到：

后端补齐 68 处 `@SaCheckPermission` 之后，权限**真的开始拦了**。而前端一直没有
`v-permission` 之类的东西去消费权限点（README 待办第 8 条、`08-当前进度` 也有同名条目），
于是出现：**按钮照常显示，点下去报 403**。

最典型的是销售专员点「删除线索」—— 种子里本来就没给这个角色 `crm:lead:delete`。

### 17.2 根因

权限点**已经下发到了前端**：`src/store/permission.js` 把 `menu.perms` 写进了路由的
`meta.perms`，`src/store/user.js` 也有现成的 `hasPermission()`（且对空值返回 `true`）。
缺的只是**消费方**：

- 没有 `v-permission` 指令（`main.js` 里从未调用 `app.directive`）；
- `meta.perms` 的唯一消费者是 `router/index.js` 的路由守卫（**页面级**），
  按钮级无人过问；
- `ListToolbar` 直接 `v-for` 渲染全部 action，不做任何过滤。

### 17.3 修复

**两处基建 + 两批接线**，刻意都放在「一处生效」的位置，避免逐页散写：

1. **新增 `src/directives/permission.js`**：`v-permission="'crm:lead:delete'"`，
   支持数组（任一满足）。**不传 / 空值一律放行** —— 与 `hasPermission` 的语义一致，
   所以漏配 `perm` 的老按钮不会集体消失，行为向后兼容。
   实现是**摘除节点**而不是 `display:none`：后者在 flex 布局里仍占位，且仍能被 Tab 到。
2. **`ListToolbar` 内部按 `action.perm` 过滤**：这是关键取舍 ——
   过滤写在 `ListToolbar` 而不是 `ProList`，也不在 19 个页面里各写一遍判断，
   **19 个列表页的工具栏因此一处生效**。
3. **19 个列表页的 51 个 toolbar action 补 `perm`**；
4. **6 个文件的 13 个详情页 / 行内按钮加 `v-permission`**
   （合同 / 线索 / 商机 / 客户 / 回款 详情页，以及角色页的行内编辑删除）。

**权限码一律取自后端已有的来源，不凭空造**：

| 来源 | 说明 |
| :--- | :--- |
| `sql/crm.sql` 的 `sys_menu.perms` | 如 `crm:lead:toPool`、`fin:invoice:issue` |
| 后端 Controller 上已有的 `@SaCheckPermission` | 如 system 模块的 `system:user:changeStatus` |

种子里**没有**对应权限点的动作，沿用后端 BUG-016 的同一套映射（报价单的
`convert` / `void` 复用 `crm:quotation:update`），保证**前后端用的是同一个码** ——
这一条很重要：前端写一个后端不认的码，只会让按钮对所有人消失（超管除外）。

### 17.4 验证

```
npm run check:static  → 与改动前完全一致（仍是预存的 3 处 notice mock 提示）
npm run check:mock    → 78/78 通过
npm run build         → ✓ built
```

⚠️ **未实机点验**：开发环境未起后端，无法登录不同角色逐一确认按钮显隐。
**建议用「销售专员」账号过一遍**（它的权限最少，最容易看出该藏的有没有藏住）。

> ✅ **mock 模式不受影响**（已核实）：`src/mock/menu.js:1718` 的 `mockPermissions = ['*:*:*']`，
> 而 `userStore.hasPermission` 里显式判断了 `this.permissions.includes('*:*:*')`。
> 所以 mock 下等于超管，**带 `perm` 的按钮全部照常显示**，不会因为这次改动
> 而在 mock 里集体消失。
>
> ⚠️ 这也意味着 **mock 模式验不出这次的权限逻辑** —— mock 永远是超管视角，
> 该藏的按钮在 mock 下也看得见。**要验必须连真实后端 + 低权限账号。**

### 17.5 顺带发现：6 处前端按钮接的是**根本不存在的后端端点**

做接线时逐个核对端点是否存在，发现这些地方的按钮点了必然失败
（**不是本次改动引入的**，是既有的未实现功能；`.env.development` 的注释里有列，但 README
的待办清单里没有）：

| 前端调用 | 后端现状 |
| :--- | :--- |
| `/system/dict-type/*`（字典页） | **没有 `DictController`**（只有 `DictTypeDO` / `DictTypeMapper`） |
| `/system/numbering-rule/*`（编号规则页） | **没有 `NumberingRuleController`**（Service 有，见 README 待办 4） |
| `/system/dept` 的写操作 | `DeptController` **只有 `GET /tree`** |
| 联系人增删改（客户详情页） | 无任何 Controller |
| 开票信息（客户详情页） | 无任何 Controller |
| `/crm/lead/{id}/convert`（线索转客户） | `LeadController` **没有 convert 端点** |
| `/finance/payment`、`/finance/invoice` 的写操作 | 财务模块只读（README 待办 2） |

**这些地方本次刻意「没有」加 `perm`** —— 端点都不存在，加权限码只是遮丑
（还会让按钮对所有人消失，把「接口缺失」伪装成「没权限」，更难查）。
**正确做法是补后端或先摘掉按钮，而不是加权限。**

### 17.6 防范建议

1. **新增按钮时先确认后端端点存在、再确认权限码在 `sys_menu` 里**，
   两件都确认了再加 `v-permission`。顺序反了就会造出「永远不显示的按钮」。
2. **列表页的按钮走 `TOOLBAR` 声明 + `perm` 字段**，不要在页面里手写判断 ——
   过滤集中在 `ListToolbar` 一处，新页面天然继承。
3. **`v-permission` 是界面友好，不是安全边界。** 真正的防线是后端的
   `@SaCheckPermission`。前端的可以绕过，也**不需要**做得无懈可击 ——
   但绝不能因为「前端藏了」就不加后端注解。
4. **前后端必须用同一个权限码。** 加注解 / 加 `perm` 之前都去 `sql/crm.sql`
   的 `sys_menu` 里搜一下，这是唯一的事实来源。

---

> **以下 BUG-018 ~ BUG-020 是 2026-09-14 实机联调时用户报障后定位的。**
> 018 / 019 是「现象看起来像前端 bug，根因在后端 / 在流程」的典型；
> 020 是**合并事故**，属于「自检脚本静默失效」那一类（与 BUG-011 同源）。

## BUG-018 「负责人」下拉加载不出来、也回显不了：选择器端点要求的权限只有超管有

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **前后端**（权限点归属错配） |
| 文件 | `UserController.java`（新增 `/options`）、`selectorRegistry.js` 的 `userSelector`、`api/system.js` |
| 严重程度 | 高 —— 销售角色在**线索 / 商机 / 报价单 / 合同**表单里全都选不了负责人 |
| 状态 | ✅ **代码已修复**（2026-09-14），待重启后端实机验证 |

### 18.1 现象

用户报：「查询负责人的时候不能正确的回显」，并怀疑是前后端数据格式不一致。

实际表现：非超管账号打开线索 / 商机 / 报价单 / 合同的新增或编辑弹窗，
**「负责人」下拉点开后是空的**，选不了人；编辑已有记录时也回显不出负责人。

**这个现象极易误判成前端 bug** —— 下拉空白、没有明显报错弹窗，
看起来像「数据没拿到」或「字段名对不上」。所以第一反应会去查 `SalesOwnerRespVO`
的字段名和 `request()` 的解包层级 —— **这两处都是对的**（见 18.4）。

### 18.2 根因

`selectorRegistry.js` 里 `userSelector`（**6 处复用**：负责人 / 直属上级 / 部门负责人 /
客户签约人 / 公司签约人 / 角色成员）的数据源是：

```js
const userSelector = (title, columns) => ({
  api: getUserSimpleList,   // → GET /system/user/simple-list
  ...
})
```

而 `UserController.java` 上：

```java
/** 选择器专用。被选直属上级 / 选部门负责人 / 选角色人员三处复用 */
@SaCheckPermission("system:user:list")
@GetMapping("/simple-list")
```

**`system:user:list` 在种子里只授予了超级管理员**（已用只读查询核实：
role 1 有，销售总监 / 销售专员 / 财务都没有）。于是销售角色调用 → **403**。

根因是**权限点的归属错了**：这个端点挂在系统模块的「人员管理」权限下，
但它的用途是**全项目的通用人员选择器**。拿它当选择器数据源，等于要求每个
能用业务表单的人先拥有「人员管理」权限 —— 而「负责人」是销售每天都要填的字段。

> 端点自己的注释写着「**选择器专用**」，却要求一个只有管理员才有的权限 ——
> 这个自相矛盾就是 bug 的指纹。

### 18.3 修复

1. **后端新增 `GET /system/user/options`** —— **仅需登录，不校验权限点**，
   返回字段与 `/simple-list` 完全相同（`id/name/username/phone/deptName`）；
2. **前端 `userSelector` 改指向它**（`getUserOptions`）；
3. 接口文档补上该端点（否则 `check:api` 报「文档里查不到」）。

**为什么另开端点而不是放宽 `/simple-list`**：
- 列表页 `GET /system/user` 与 `/simple-list` 共用同一个权限点，放宽会连带影响更大；
- 放宽会让「人员管理」的模块边界失效，而这是个语义问题不是严格性问题 ——
  新建一个**语义明确**的「选择器端点」更干净，与 `/crm/customer/owner-options` 同一思路。

**安全性**：返回的是精简字段（不含密码 / 状态 / 角色）；「登录」这一关仍由
`SaTokenConfig` 的全局拦截器把守 —— **本类不写注解 ≠ 不需要登录**，
这是容易搞错的一点。

### 18.4 验证

- 后端编译通过；`check:api` 199/199；
- ⚠️ **未实机验证**（需要重启后端加载新端点）。验证方式：用**销售专员**账号
  打开线索 / 商机表单，负责人下拉应能正常加载与回显。

> **排查过程中确认「不是这些原因」的项**（记下来免得下次重复查）：
> `SalesOwnerRespVO` 的字段名与前端取的 `userId/realName/phone/deptName` **完全一致**；
> `request()` 返回的是剥壳后的 `data`，所以 `.map()` 没问题；
> `selectorLabel` 取 `row.name`，与 api 层映射出的 `{id, name}` 一致；
> `FormField#onSelectorSelect` 读的是 `payload?.id`，也对。

### 18.5 防范建议

1. **给端点定权限点之前，先想清楚「谁需要调用它」** —— 按调用方定归属，
   而不是按「它属于哪个模块」定。选择器类端点应按**使用它的业务表单**来定，
   不该挂在「人员管理」这种只有管理员才进得去的模块下。
2. **端点注释不能自相矛盾**：注释写「选择器专用」而注解要求管理员权限，
   这个矛盾如果被 review 到就能提前发现。
3. **`@SaCheckPermission` 缺失 ≠ 需要登录**。全局拦截器已经保证登录，
   所以「仅需登录」的正确写法就是**不加注解**（而不是加一个宽松的注解）。
4. **下拉空白要报错，不要静默**。403 被 `request.js` 统一弹提示了，
   但用户描述成「回显不了」—— 说明提示没被注意到或被误当成别的问题。
   选择器加载失败时值得在控件内部给一个更显眼的态。

---

## BUG-019 token 失效后弹「未提供登录凭证，请先登录」，且进不去登录页

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **前端**（`request.js` / `api/auth.js` / `router/index.js` 三者交互） |
| 文件 | `src/utils/request.js`、`src/api/auth.js` 的 `getInfo` |
| 严重程度 | 中高 —— 用户被挡在登录页外，且看到的是一句开发者文案 |
| 状态 | ✅ **已修复**（2026-09-14） |

### 19.1 现象

用户报：「进行邮箱注册获取验证码会爆出一个登录权限错误」。

实际是弹出 `ElMessageBox` 警告框，内容是
**「未提供登录凭证，请先登录」**——注意这句话是**后端的调试文案**，
出现在给终端用户看的弹框里。

（提示语是定位这条的关键：用户从选项里认出正是这一句，才排除了
「验证码接口本身报错」这个方向。）

### 19.2 根因

**先排除的（都实测过）**：`/auth/email/code` **本身是好的**。用正在运行的后端实测：

| 请求 | 结果 |
| :--- | :--- |
| `POST /api/auth/email/code`（经 Vite 代理） | `{"code":400,"message":"邮箱格式不正确"}` —— 进到了参数校验，说明白名单**生效** |
| `POST /auth/email/code`（直连后端） | 同上 |
| `POST /auth/email/code`（**带 `/api` 前缀**直连） | `{"code":401,"message":"未提供登录凭证，请先登录"}` |
| `GET /auth/me`（无 token） | **`{"code":401,"message":"未提供登录凭证，请先登录"}`** ← 与用户看到的一字不差 |

也读过并排除了：`SaTokenConfig.EXCLUDE_PATHS`（`/auth/email/**` 确实在里面，
且不在注释里）、`AuthController`（无任何权限注解）、`AuthServiceImpl#sendEmailCode`
（不取登录态）、`OperationLogAspect`（用可空的 `LoginUserHolder.get()` 且整体 try/catch）、
无 `context-path`。

**真正的链路**（三个文件交互出的缺陷）：

1. `localStorage` 里留着一个**已失效的 token**（会话过期 / 后端重启 / Redis 被清）；
2. `router/index.js:59-62`：守卫判断「**有** token」就认为已登录，
   于是访问登录页被弹回首页 —— **用户根本进不去登录页**；
3. 首页路由未生成 → 调 `fetchUserInfo()` → `GET /auth/me` → **401**；
4. `request.js` 的 401 分支 → `handleUnauthorized()` → 弹出写着后端文案的警告框。

所以「想注册却弹登录错误」= 他被自己的失效 token 挡在登录页外了。

### 19.3 修复

给「登录态探测」请求加 `silent401`：

- `api/auth.js` 的 `getInfo()` 传 `silent401: true`（**并写明为什么**）；
- `request.js` 在 401 分支先判 `response.config.silent401`，命中则**只 reject、不弹框**
  （HTTP 401 那个分支也加了同样判断，作为防御 —— 后端目前返回的是 200 + `code:401`）。

之后：探测失败静默，由 `router/index.js` 的 catch（它**已经**做了 `resetState()`
并跳登录页）完成清理 —— 用户看到的是一个**干净的登录页**，而不是一个警告框。

### 19.4 验证

`check:static` / `check:mock` / `build` 通过。
⚠️ 未实机验证：需要「localStorage 有失效 token」这个前置条件 ——
手工构造方式：登录后清掉 Redis 里的会话（或重启后端并换 Redis 库），再刷新页面。

### 19.5 防范建议

1. **「有没有 token」不等于「token 有没有效」**。守卫拿 token 的存在性当登录态，
   必然会在 token 失效时出现「进不去登录页」这种反直觉行为。
2. **后端调试文案不该直接进用户弹框**。`未提供登录凭证，请先登录` 是给开发者看的；
   前端应展示自己的文案（如「登录已过期，请重新登录」）。
3. **区分「用户主动发起的请求」与「系统探测请求」**：前者失败要弹框，
   后者（`/auth/me`、刷新 token 等）失败应当静默交给上层流程处理。
   `silent401` 就是这个区分的最小实现。

---

## BUG-020 合并把冲突标记提交进版本库，三个自检脚本同时失效

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **前端**（`scripts/`，构建期工具） |
| 文件 | `scripts/check-api-align.js`、`scripts/lib/backend-paths.js` |
| 严重程度 | 高 —— `check:api` 整体崩溃；三个脚本全部报「找不到后端项目」 |
| 状态 | ✅ **已修复**（2026-09-14） |

### 20.1 现象

用户在本地提交并合并（`3c3d257` / `258f973` / `e56a2bb Merge ... into develop`）之后：

```
npm run check:api
→ SyntaxError: Unexpected token '<<'
    at scripts/check-api-align.js:23
```

以及修掉语法错误之后：

```
找不到后端项目里存在 docs/05-接口文档.md 的目录，试过：
  · .../crm-ui/crm-ui/aurum-crm
  · .../crm-ui/crm/aurum-crm
  · .../crm-ui/aurum-crm
  · .../master/crm/aurum-crm
```

而后端其实好好地躺在 `<项目根目录>`。

### 20.2 根因（两个独立的原因）

**① 冲突标记被提交进了版本库**
`check-api-align.js` 里留着完整的 `<<<<<<< HEAD` / `=======` / `>>>>>>> e56a2bb`，
JS 直接语法错误 → `check:api` 整体不可用。

更讽刺的是：**冲突的另一侧（被 `>>>>>>>` 标记的那段）注释里，
恰好就在描述「这里曾经因为三份重复实现而撞成冲突、导致 check:api 整体不可用」** ——
也就是说，同一个坑原样复发了一次。

判定哪一侧是对的：HEAD 侧用的 `resolveBackendDir` / `reportMissingBackend`
**在本文件里根本没有 import**（import 的是 `backendFile`），所以即使删掉标记，
那一侧也是运行时 `ReferenceError`。**按「保留 backend-paths.js 一份」的既定结论解掉。**

**② 路径探测丢了「双层 crm 嵌套」这条**
新的 `backend-paths.js` 的候选列表与兜底查找只覆盖
`aurum-crm` / `master/crm/aurum-crm` / `crm/aurum-crm`，
**唯独没有 `crm/crm/aurum-crm`** —— 而这正是本机的检出布局。

而 `docs/08-当前进度.md` 明确记着：**「2026-09-14 补上了 `crm/crm/aurum-crm` 这种双层嵌套
—— 在此之前，本机布局下这三个脚本全都报『找不到后端项目』」**。
也就是说，这条修复合进 `backend-dir.js` 之后，在向 `backend-paths.js` 收敛时**又丢了**。

### 20.3 修复

1. `check-api-align.js`：删掉冲突标记，保留 `backend-paths.js` 一侧
   （并补了一段「本坑复发」的补记，免得第三次）；
2. `backend-paths.js`：`CANDIDATES` 加布局 E
   `../../../../crm/crm/aurum-crm`，**兜底查找也加 `crm/crm/aurum-crm`**
   —— 兜底必须有它，否则候选列表一旦少写一层 `..`，兜底也救不回来。

### 20.4 验证

| 命令 | 修复前 | 修复后 |
| :--- | :--- | :--- |
| `npm run check:api` | `SyntaxError: Unexpected token '<<'` | **199/199 通过**（并打印实际命中的文档路径） |
| `npm run check:static` | 找不到后端，exit 1 | 正常运行（剩 1 项真实问题，见 BUG-021 待办） |
| `npm run check:mock` | —— | 78/78 通过 |

### 20.5 防范建议

1. **冲突标记一定要跑一次脚本确认**，别只看「文件保存了」。
   可以加一条最简单的静态检查：`grep -rn '^<<<<<<< '` 命中即失败 ——
   代价极低，而这一类错误的**症状是「脚本整体不可用」**，最容易被误判成环境问题。
2. **「合并时丢失既有修复」要靠文档兜住**。这次是 `08-当前进度.md` 里那句
   「补上了双层嵌套」起了作用，否则会重新排查一遍「为什么找不到后端」。
   所以**修完 bug 一定要写进文档**，它防的不只是遗忘，还有合并丢失。
3. **重复实现是合并冲突的源头**。路径解析一度存在三份（内联候选 + `backend-paths.js`
   + `backend-dir.js`），合并必然撞车。**动手前先搜 `scripts/lib/`**。
   （`scripts/lib/backend-dir.js` 目前仍在，已无人 import，建议删除 —— 保留它
   就是在等下一次「只有一份被 import、另一份静默腐烂」。）

---

## BUG-021 顶栏「消息 → 查看全部」跳到 `/system/notice`，而该路由不存在（404）

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **前后端**（菜单 / 权限点与前端入口脱节） |
| 文件 | `src/layout/components/TopNav.vue:254`（`goAll()`）、`src/views/system/notice/index.vue`、后端 `NoticeController`、`sql/crm.sql` 的 `sys_menu` |
| 严重程度 | 中 —— 用户点「查看全部」落到 404 页 |
| 状态 | ⚠️ **已确认，未修复** —— 修法涉及产品决策，待定 |

### 21.1 现象

顶栏消息面板里的「查看全部」执行 `router.push('/system/notice')`，
但 `/system/notice` **不是一个存在的路由** → 落到兜底路由 → **404 页**。

发现方式：`npm run check:static` 的「写死的路由路径都存在」这一项报了出来 ——
**自检脚本是正确的，它在报一个真问题。**

### 21.2 根因

公告 / 通知这条线**只删了一半**：

| 位置 | 现状 |
| :--- | :--- |
| `sql/crm.sql` 的 `sys_menu` | **没有任何公告/通知菜单行**；`grep -in notice sql/crm.sql` 只命中 `sys_notice` 建表与演示数据 |
| 线上库 `sys_menu`（128 条） | 同样**没有**该菜单，也**没有** `system:notice:*` 权限点 |
| `src/mock/menu.js` | 0 处 `system/notice`（它是从 `sys_menu` 生成的，所以同步缺失） |
| `TopNav.vue` | 仍然 `router.push('/system/notice')` |
| `src/views/system/notice/index.vue` | 页面文件**还在** |
| 后端 `NoticeController` | **还在**，且带 `@SaCheckPermission("system:notice:list\|create\|update\|delete")`（竖线已转义，否则会把表格拆列）|

所以：菜单与权限点被移除了，但前端入口、页面文件、后端控制器都还留着。
**而且 `NoticeController` 上那四个权限点在 `sys_menu` 里根本不存在** ——
意味着除了超管（`*:*:*` 通配）之外，**没有任何角色能通过这四个注解**。
即便页面可达，也只有超管能用。

（`sys_menu` 里没有权限点 ≠ 权限点不存在：`*:*:*` 让超管照样能调。
这类「注解里的权限码在种子里查无此项」的情况值得单独扫一遍。）

### 21.3 待定的两种修法

| 方案 | 做法 | 代价 |
| :--- | :--- | :--- |
| **(a) 摘掉 / 改向入口**（倾向） | 把「查看全部」指向看板的消息区（消息功能看起来已并入 `dashboard/components/MessagePanel.vue`），并删掉闲置的 `views/system/notice/index.vue` | 若其实还想要独立页面，就等于砍了功能 |
| **(b) 恢复菜单与权限点** | 往 `sql/crm.sql` 的 `sys_menu` 补回公告管理菜单 + `system:notice:*` 四个权限点，并给角色授权；由于 `crm.sql` 会 DROP 重建全表，需重跑脚本 | 要重跑破坏性脚本；且要先确认「公告管理」确实还该是一个独立页面 |

**决策依据**：先确认「消息 / 公告」在产品上的最终形态是**独立页面**还是**看板区块**。
代码上看是**已并入看板**（`MessagePanel.vue` 存在、`sys_notice` 有种子数据、
`08-当前进度` 把消息功能归在看板下），所以倾向 (a)。

### 21.4 验证（修复后应做）

`npm run check:static` 的「写死的路由路径都存在」应回到 ✓。

### 21.5 防范建议

1. **删菜单/权限点时，要连带清理引用方**：入口（`router.push`）、页面文件、
   后端控制器、前端 api 层。只删一半的状态比全删或全留都糟 ——
   它不报错，只是点了 404。
2. **`check:static` 那条「写死的路由路径都存在」是有价值的检查，别因为它报错就去改检查**
   （它这次报的是真问题）。
3. **定期扫一遍「注解里的权限码是否都在 `sys_menu` 里」** ——
   查无此项的码只有超管能通过，是一个静默的权限黑洞。

---

## BUG-022 注册弹窗一打开输入框里就有值（两个独立原因叠加）

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **前端**（`aurum-crm-ui`） |
| 文件 | `views/login/components/RegisterDialog.vue`、`ForgotPasswordDialog.vue`、`views/login/index.vue` |
| 严重程度 | 中低 —— 不影响功能，但「刚点注册，密码框里已经有东西了」很吓人，也让人不敢用 |
| 状态 | ✅ **已修复**（2026-09-14） |

### 22.1 现象

用户报：「点击注册后的弹窗其中的输入框会有默认值（可能是浏览器记住账号密码，
导致前端直接有了缓存）」。

用户的判断方向是对的，但**只对了一半** —— 这里其实是**两个独立原因叠加**。

### 22.2 根因

**原因 ①：浏览器密码管理器自动填充**

浏览器（尤其 Chrome）看到「文本输入框 + 密码框」的组合就当成登录框，
把之前保存的登录凭据填进**注册**弹窗的账号 / 密码 / 确认密码里。

⚠️ **关键细节（很容易误判成「已经防住了」）**：Element Plus 的 `ElInput` 自己声明了
`autocomplete` 这个 prop，**默认值就是 `"off"`**（`element-plus/es/components/input/src/input.mjs`），
并且会绑到原生 `<input>` 上。所以在改之前，那些输入框**本来就带着 `autocomplete="off"`** ——
而 **Chrome 对密码框上的 `off` 基本无视**（这是它有意为之的行为，防止网站阻止密码保存）。

也就是说：「已经写了 `off` 却还是被填充」是正常的，不是没生效。**必须用 `new-password`。**

**原因 ②：弹窗没销毁，表单值一直留在内存里（与浏览器无关）**

```js
const form = reactive({ username: '', email: '', emailCode: '', password: '', confirmPassword: '' })

function handleOpen() {
  nextTick(() => formRef.value?.clearValidate())   // ← 只清了校验，没清值
}
```

`el-dialog` 默认 `destroy-on-close=false`，弹窗内容**不会销毁**，而 `form` 是
setup 里创建的 `reactive` 对象。于是「填一半 → 取消 → 再打开」会把上次的内容原样留在框里。

**注意：这条即使用无痕模式、或换一个不会自动填充的浏览器，也照样复现** ——
这也是区分两个原因的办法。

### 22.3 修复

**① 按语义标 `autocomplete`（不是一律 `off`）**

| 位置 | 值 | 为什么 |
| :--- | :--- | :--- |
| 注册 / 找回密码的**密码框** | `new-password` | 告诉浏览器「这是设新密码」，**不要填已保存的旧密码**。Chrome 认这个值 |
| 登录页的账号 / 密码 | `username` / `current-password` | 让密码管理器**正确地**记住并填充登录凭据 |
| 账号 / 邮箱 / 验证码 | `off` | 不是凭据，不该被填 |

**登录页与注册弹窗必须标成一对**：登录标 `current-password`、注册标 `new-password`，
两边都标清楚，浏览器才不会把「注册」当成「登录」来填（反之亦然）。

**② `handleOpen` 里真正清空表单**

抽出 `EMPTY_FORM` 常量作为初始值，`handleOpen` 里 `Object.assign(form, EMPTY_FORM)`
再 `clearValidate()`。抽常量是为了不在两处重复字面量 —— 否则将来加字段必然漏一处。

### 22.4 验证

`build` / `check:static` / `check:mock` 全过。
静态确认：`autocomplete: __props.autocomplete` 确实被绑进原生 `<input>` 的
属性对象（`element-plus/es/components/input/src/input.vue_...mjs` 的 input / textarea 两个分支），
构建产物里能搜到 `new-password`。

⚠️ **未实机验证**（需要带已保存凭据的浏览器）。手工验证方式：
1. 在登录页用密码登录一次并让浏览器保存凭据；
2. 退出 → 点「注册账号」→ 三个框都应为**空白**；
3. 填几个字 → 取消 → 再点「注册账号」→ 仍应为空白（这一条验的是原因 ②，
   用无痕窗口也能复现，可用来单独确认）。

### 22.5 防范建议

1. **密码类输入框要按语义标 `autocomplete`**，别一律写 `off`：
   设新密码用 `new-password`、输入现有密码用 `current-password`、
   登录账号用 `username`。`off` 对密码框基本无效。
2. **不要以为「写了 `off` 就防住了」** —— 组件库可能已经默认了 `off`
   （Element Plus 就是），于是「加过 `off`」和「没加」看起来一样，
   而问题依然存在。要验证的是**实际行为**，不是「属性写了没有」。
3. **`el-dialog` 里的表单，`handleOpen` 必须清值**（不只是清校验）。
   `destroy-on-close` 默认是 `false`，弹窗内容不销毁 —— 这类「关掉再打开还留着上次输入」
   的 bug 与浏览器无关，**任何环境都能复现**，反而是更好排查的那一个。

---

## BUG-023 注册时「获取不到验证码」（两个独立原因，都会表现为同一个症状）

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **前端**（`aurum-crm-ui`，`useEmailCountdown` 的状态粒度） |
| 文件 | `src/composables/useEmailCountdown.js`（重写）、`src/views/login/index.vue`、两个 Dialog、`src/utils/request.js` |
| 严重程度 | 中高 —— 验证码发不出去，注册流程直接走不下去 |
| 状态 | ✅ **已修复**（2026-09-14） |

### 23.1 现象

用户报：「有的时候会发生验证码失败」，补充为：**是邮箱验证获取不到验证码，是在注册的时候**。

「有的时候」是关键线索 —— 说明它**只在特定前置条件下复现**，不是每次都坏。

### 23.2 根因

**① 前端倒计时按「入口」记，后端限流按「邮箱」记 —— 粒度不一致**

后端 `EmailCodeServiceImpl#sendCode` 的限流 key 是 `auth:email:limit:{email}`，
**按邮箱**限流 60 秒，且**三个入口（登录 / 注册 / 找回密码）共用同一套验证码与同一把锁**。

而前端的倒计时状态**按入口各持一份**（`useEmailCountdown()` 每个组件实例一份）。
于是：在登录页给 `A@x.com` 发过码 → 切到注册页再给 `A@x.com` 发 →
**后端回 1004「验证码发送过于频繁」，而前端按钮上没有任何倒计时**，
用户完全不知道为什么发不出去。

> 这条**不是新引入的**，而是之前一次取舍的代价，`useEmailCountdown.js` 的注释里
> 原本就写着：「代价是同一邮箱跨入口重发会撞后端的 1004，用户看到的是一句
> 『验证码发送过于频繁』而不是一个可预期的倒计时 —— 这个取舍是明确选择过的。」
> 现在用户踩到了它，说明这个取舍选错了。

**② 邮箱稍微不合法时，按钮是个「完全没反馈的死按钮」**

原 `canSend(email)` 里带了 `isValidEmail(email)`，而按钮绑的是 `:disabled="!canSend(...)"`。
前端那个邮箱正则是**宽松**的（要求域名里有点）：用户填 `abc@qq`、`abc@localhost`
这类「少个点」的邮箱时，`canSend` 返回 false → **按钮直接置灰** →
点下去**既没反应、也没有任何提示**。

症状同样是「获取不到验证码」，而且比 ① 更糟 —— **① 至少还有一句报错，② 一个字都没有。**

**③（附带）失败信息里丢掉了业务码**

`request.js` 抛出的 error 只带 `message`，把后端的 `code` 丢了。于是调用方想区分
「发信失败 1009（后端会退还限流，应该允许立刻重试）」和「太频繁 1004（应该起倒计时）」，
只能去**匹配中文文案** —— 文案一改就静默失效。

### 23.3 修复

**① 倒计时改成按「邮箱」共享**（`useEmailCountdown.js` 重写）

模块级 `Map<归一化邮箱, 截止时间戳>` + 一个**共享的**每秒 tick。
这样「不同邮箱互不影响」与「同一邮箱跨入口不会撞 1004」**同时成立** ——
因为前端终于和后端按**同一个维度**记账了。

判断口径的唯一依据是后端：**它按什么维度限流，前端就按什么维度记。**

> 用 `sessionStorage` 兜了一层：后端那把锁在 Redis 里、**刷新页面不会消失**，
> 前端只记在内存里的话，一刷新按钮又可点、点了照样撞 1004。
> （隐私模式下存储不可用时忽略异常 —— 只是失去「刷新后仍显示倒计时」，不影响发码。）
>
> 定时器是**模块级共享**的，且在没有活跃倒计时时自己 `clearInterval` ——
> 不再需要每个入口各自 `onBeforeUnmount(stop)`（那个 API 已移除）。

**② 按钮不再因为「邮箱不合法」而置灰**

`canSend(email)` 现在**只**判断「倒计时中 / 请求中」。邮箱不合法时允许点击，
由 `send()` 给出明确提示（「请输入正确的邮箱地址」）——
**宁愿让用户点一下看到原因，也不要给一个点了没反应的死按钮。**

**③ 只在 1004 时补倒计时，其它失败允许立刻重试**

`request.js` 把业务码挂到 error 上（字段名是 **`bizCode`** 而不是 `code` ——
axios 自己的 error 上 `code` 是 `'ECONNABORTED'` 这类字符串，同名会让两种来源混淆）。
`send()` 的 catch 里：

```js
if (e?.bizCode === 1004) { /* 补上倒计时 */ }
// 其它失败（尤其 1009 发信失败）：不起倒计时，允许立刻重试
```

⚠️ **这一条很关键**：后端在「邮件发送失败（1009）」时会把限流**一并退还**，
此时如果无脑起 60 秒倒计时，就是「用户既没收到信、又被拦住不能重发」——
比不修还糟。

### 23.4 验证

- `build` / `check:static` / `check:mock` 全过；三个调用点的
  `buttonText(email)` / `canSend(email)` 已统一。
- **排除了算术验证码这条线**（值得一提，因为很容易往那个方向查）：
  查 `sys_login_log` 的近 7 天聚合，**所有失败都来自密码登录（`login_type=1`），
  邮箱验证码登录（`login_type=2`）一次都没用过**；其中只有 1 次是算术验证码失败。
  所以这次的报障与算术验证码无关。顺带确认：算术验证码的「答错即作废」是**有意**的
  （Lua「先 DEL 再比对」，防的是答案空间太小被穷举），前端 `doLogin` 的 catch 里
  也**已经**调了 `loadCaptcha()` 换题，这条链路是好的。

⚠️ **未实机验证**。手工验证步骤：
1. 在登录页用 `A@x.com` 获取验证码 → 立刻切到注册页填同一个邮箱 →
   按钮应显示「**N 秒后重发**」而**不是**点了之后报「发送过于频繁」（验原因 ①）；
2. 同样流程做一次**刷新页面**，倒计时应该还在（验 sessionStorage 那一层）；
3. 邮箱填 `abc@qq`（少个点）→ 按钮**可点** → 点一下应提示「请输入正确的邮箱地址」
   （验原因 ②，改之前是点了完全没反应）；
4. 故意关掉 SMTP（或填错授权码）→ 点发送 → 报「发送失败」后应能**立刻重试**
   （验 1009 不锁人）。

### 23.5 防范建议

1. **限流/倒计时类状态，前端必须和后端用同一个 key 维度。**
   「按入口」「按 IP」「全局」都要先问一句「后端是按什么限的」——
   两边不一致时，前端要么显示得比后端宽松（用户撞 1004 且无提示），
   要么比后端严格（明明能发却发不出）。这次是前者。
2. **别用「禁用按钮」来表达「输入还不合法」。** 置灰的按钮**没有任何解释能力** ——
   用户只看到「点了没反应」。合法性问题应该在点击后给提示。
   （例外：确实不该发生的操作可以禁用，但也要配合说明。）
3. **error 要带业务码，别只带 message。** 靠匹配中文文案判断错误类型，
   文案一改、或将来做多语言，判断就静默失效 —— 而且失效方式是「分支永远不进」，
   没有任何报错。
4. **「退还可重试」和「锁一段时间」是两种失败，不能一视同仁。**
   凡是「服务端已经把配额退还」的失败（如本例的 1009），前端**绝不能**再自己锁用户。

---

## BUG-024 SMTP 认证失败（535）：**不是代码 bug**，但错误码与日志把排查方向带偏了

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **环境/配置**（163 账号侧）+ **后端**（错误码与日志的区分度） |
| 文件 | `application-dev.yml` 的 `spring.mail.*`、`EmailCodeServiceImpl`、`ErrorCode`、`README` 常见问题 |
| 严重程度 | 中 —— 邮件发不出去（注册/邮箱登录/找回密码全都不通），但**代码侧无缺陷** |
| 状态 | ✅ **应用侧已改进**（2026-09-14）；⚠️ **根因在 163 账号侧，需人工处理** |

### 24.1 现象

用户报「还是报错」，附后端日志：

```
jakarta.mail.AuthenticationFailedException: 535 Error: authentication failed
  at org.springframework.mail.javamail.JavaMailSenderImpl.doSend(...)
  ...
[邮箱验证码] 发送失败 email=35***@qq.com 原因=Authentication failed
→ GlobalExceptionHandler: [业务异常] POST /auth/email/code | code=1009 msg=验证码邮件发送失败，请稍后重试
```

### 24.2 定性：不是代码问题

用**只做认证握手、不发信**的方式直连 163 实测（方法见 README 常见问题）：

```
已连接 smtp.163.com:465（TLS）          ← 网络通、TLS 握手成功
<<< 250-AUTH LOGIN PLAIN XOAUTH2        ← 服务器支持认证
<<< 535 Error: authentication failed    ← 但拒绝了这个账号 + 授权码
```

网络、TLS、代码路径全部正常 —— **只有 163 在最后一步拒绝了凭据**。
所以这是**账号侧**的问题：授权码被重置过，或 163 网页版的 SMTP 服务被关掉了。

⚠️ **这一条在 README 的「常见问题」里已经完整写过**（含三条最常踩的原因表和排查配方），
配置文件的注释里也写着「2026-09-14 本项目就在这上面卡了一轮」。
**结论：这个报错要先去查 163，别先改代码。**

### 24.3 应用侧真实存在的两个问题（已修）

**① 错误码把「配置问题」和「可重试失败」混成了一个**

原来所有发信失败都抛 `EMAIL_SEND_FAILED(1009)`，文案是「**请稍后重试**」。
但认证失败重试一万次也不会成功 —— 这句文案把用户和排查者都往错方向带。

现在认证失败单独抛 `EMAIL_SERVICE_AUTH_FAILED(1012)「邮件服务未正确配置，请联系管理员」`。
（与 1009 的区分方式：沿异常链找 `MailAuthenticationException`，或消息里含 `535`。）

**② 日志只说「发送失败」，没有指出该查哪四处**

163 在「授权码错」和「SMTP 服务没开」两种情况下**都只回 535**，光看错误码区分不了。
现在认证失败时会打印一段**针对性的排查清单**：

```
[邮箱验证码] SMTP 认证失败（163 返回 535）—— 这是【配置问题】，重试不会成功。请依次检查：
  ① 163 网页版 → 设置 → POP3/SMTP/IMAP → 「SMTP服务」是否为【开启】；
  ② spring.mail.password 填的是否是【授权码】（不是邮箱登录密码）；
  ③ 授权码若重置过，旧码会【立即失效】，必须填新的（且只显示一次）；
  ④ spring.mail.username 是否为完整邮箱地址（如 <邮箱已脱敏>）。
```

目的是把这条坑的排查成本从「一轮」降到「一眼」—— 已经卡过两轮了。

### 24.4 验证

后端编译通过。⚠️ **未端到端验证**（需要有效的 163 授权码才能走到成功分支）。

**待人工处理**（只能在 163 侧解决）：
1. 用账号登录 163 网页版 → 设置 → POP3/SMTP/IMAP → 确认「SMTP服务」为**开启**；
2. 若已开启，点「**重置授权码**」取一个新的（旧的立即失效，只显示一次）；
3. 填进 `application-dev.yml` 的 `spring.mail.password`，重启后端；
4. 重跑一次认证握手，应看到 `235 Authentication successful`。

### 24.5 防范建议

1. **配置类的失败要有独立的错误码，且文案不能说「重试」。**
   「稍后重试」是给**瞬时故障**的话术；配置错误用它会让人一直重试到放弃。
2. **诊断信息要写「去查什么」，而不是「发生了什么」。**
   这条坑的本质是「两个完全不同的原因返回同一个错误码」——
   既然错误码区分不了，就必须在**日志里**把候选原因列全。
3. **这类「环境问题」也要登记进 BUG 记录。** 它虽然不是代码缺陷，但
   ① 它会反复发生（本次是第二回）；② 它的**症状与代码 bug 无法区分**（都是接口报错）。
   不记下来，下次还得从头查一遍。

---

## BUG-025 合同审批后状态仍是「审核中」—— 界面上看不出现在该谁审

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **后端**（`aurum-crm`，`crm-module-crm`） |
| 文件 | `ContractServiceImpl.java` 的 `submit` / `audit` / `currentInstanceId`；`sql/crm.sql` 的合同种子数据；`sys_approval_flow` / `node` 配置 |
| 严重程度 | 中 —— ① **不是缺陷**：多节点流转的必然表现，但界面上**没有任何地方能看到进度**，用户无法自证流程正常；② **是缺陷**：演示数据里有一条「审核中」却永远审不动的合同 |
| 状态 | ① **已定位 + 可见性已补（第二轮到前端与「下一步到谁」）**；② 坏数据**未修**（待决策） |

### 25.1 现象

用户报障：「合同审批后状态还是审核中，这是为什么？」

具体是：在合同审核页点了「通过」，合同列表 / 详情仍然是「审核中」；而且**整个界面看不出**
这条合同走到第几步、每一步是谁审的、**现在该谁审**。看板/消息里的待办也只是一条标题。

### 25.2 实测（只读 SQL 查演示库 `YOUR_SERVER_IP/crm`，2026-09-14）

审批模板是**两级**：`node_order=1 部门主管审批`（`approver_type=4` 发起人直属上级）、
`node_order=2 财务审批`（`approver_type=2`，角色 `[4]` 财务）。

合同 `HT20260914000002`（`aaa`，id=2099451961604145152）当时的真实状态：

| 表 | 值 | 含义 |
| :--- | :--- | :--- |
| `crm_contract` | `audit_status=1, status=0` | 审核中 |
| `sys_approval_instance` | `current_node_order=2, status=0` | 已推进到第 2 个节点 |
| `sys_approval_task` | 节点1：`approver_id=1, task_status=1, approve_result=1`，`handle_time=20:46:29` | 主管**已通过** |
| `sys_approval_task` | 节点2：`approver_id=5`(赵财务)、`approver_id=14`(陈会计)，`task_status=0` | **还没人审** ← 卡在这里 |

另有合同 `HT20260003`（id=3，`audit_status=1` 审核中）**没有任何 `sys_approval_instance` 行**。

### 25.3 根因（两条，互不相关）

**① 不是缺陷：`audit_status` 只在最后一个节点通过时才变**

`ContractServiceImpl#audit` 只有走到「**没有下一个节点**」的分支才写
`audit_status=2 / status=1`；中间节点通过时只推进 `sys_approval_instance.current_node_order`
并为下一节点建待办（代码在 `ContractServiceImpl.java` 的 `next == null` 判断处，
模板见 `sql/crm.sql` 的 `sys_approval_flow/node` 初始化）。这与《状态流转图》§3
「全部节点通过 → 合同已生效」一致 —— 主管审完仍是「审核中」是**设计**，不是没生效。

**② 是缺陷：演示数据里「审核中」的合同可以没有审批实例**

`sql/crm.sql` 的合同种子数据把 `HT20260003` 写成 `audit_status=1`（注释：「审批中（演示「我的待办」）」），
但**漏建了 `sys_approval_instance` 与 `sys_approval_task`**。后果：

- 它出现在「合同审核」页（`/crm/contract/audit-list` 只按 `audit_status=1` 过滤），但**没人能审它**；
- 点审核 → `currentInstanceId()` 查不到进行中的实例 → 报 `4003 审批实例不存在` → 永远停在「审核中」，只能改库。

不变的检查 SQL（**期望 0 行**；实测返回 1 行 = 合同 id 3）：

```sql
SELECT c.contract_id, c.contract_no FROM crm_contract c
LEFT JOIN sys_approval_instance i
  ON i.biz_type='contract' AND i.biz_id=c.contract_id AND i.status=0 AND i.del_flag=0
WHERE c.audit_status=1 AND c.del_flag=0 AND i.instance_id IS NULL;
```

### 25.4 修复：把「进度 / 该谁审 / 下一步到谁」做成可读的接口 + 前端可见

流转逻辑**一行没改**（① 不是缺陷），补的是**可见性**。分两轮交付：

**第一轮（后端接口）**

| 交付物 | 内容 |
| :--- | :--- |
| `GET /crm/contract/{id}/approval` | 完整链路：节点顺序、每节点进度（未开始/待审批/已通过/已驳回/已跳过）、每个审批人的任务状态与意见、**当前节点 + 当前待办人**、`abnormalHint`。权限与详情一致（`crm:contract:list`），**不新增权限点** |
| `/crm/contract/audit-list` 每行 | 回填 `currentNodeName` + `pendingApproverNames`（只在此视图回填），列表上直接显示「当前：财务审批 · 待 赵财务、陈会计」 |
| AI 工具 `get_contract_approval` | 在 AI 小助手里问「XX 合同现在该谁审」直接出答案（复用同一个 Service 方法，同样受数据权限限制） |
| 文档 | 《05-接口文档》合同模块补接口与字段口径；《状态流转图》§3.1 补「怎么看进度」 |

**第二轮（用户追问「下一步到谁」+「前端也要看」）**

| 交付物 | 内容 |
| :--- | :--- |
| 预解析「下一步到谁」 | `nodes[].predictedApproverNames`（**预计审批人**，只在 `progress=未开始` 的节点上有值）+ 顶层 `nextNodeOrder/nextNodeName/nextApproverNames`（当前节点之后第一个节点）。实现是 `ContractServiceImpl#predictApprovers`：**待审核列表与详情共用一份**，按 `approverType` 分派，规则与真实解析 `resolveApprovers` 逐条对应；只预测 `nodeOrder > currentNodeOrder` 的节点，所以**不查任务表**，列表页每页固定 4~6 次查询、不随行数增长 |
| 列表补「提交人 / 提交时间」 | `ContractRespVO.applyUserName/applyTime`（审批实例的发起人与发起时间）。⚠️ 不是 `creator`/`createTime`（建单人/建单时间）—— 合同审核页那两列以前恒为 `--`，就是因为后端没下发 |
| **前端可见**（`aurum-crm-ui`） | 新增 `views/crm/contract/ApprovalProgress.vue`（时间轴：每步进度 + 谁审的 + 意见 + 时间；未到达的节点标「预计审批人」；顶部「当前环节 · 待 X 审批」「下一步 · 预计 Y」；`abnormalHint` 用 alert）；**合同审核页**加「当前环节 / 下一步」两列（点开审批进度弹窗）+ 提交人/提交时间列改用新字段；**合同详情页**加「审批进度」Tab |
| 前端文档 | UI 仓库《开发注意事项.md》新增第 37 条（审批人解析只有后端一份、提交人≠创建人、预计≠承诺、`v-if` 强制重取） |

配套实现要点（都是踩过的坑）：

1. 取实例用 `orderByDesc(startTime) + LIMIT 1` —— 同一合同驳回后重提会有多条实例，
   没有 `LIMIT` 的 `selectOne` 会抛 `TooManyResultsException`（见 BUG-014）；
2. 没有实例时用**当前启用的模板**渲染 `nodes`（全部「未开始」），让人至少知道要走几步；
3. 模板被改过（节点被删）时，用任务上的 `node_name` 快照把缺的那一步补出来，
   避免「当前节点：财务审批」但列表里没有这一步；
4. `abnormalHint` 把三类数据不一致说成人话（含 ② 那种「审核中但没有实例」），
   **只报事实、不做任何自愈写库**；
5. 预解析**只预测还没到达的节点**（`nodeOrder > currentNodeOrder`）：
   当前节点的待办人永远看 `pendingApproverNames`，两者不混用；
6. `Map.of()` 的坑又踩到一次：空集合用 `Map.of()` 表示时，**拿 `null` 键去 `get` 会抛 NPE**
   （脏数据里 `flow_id` 可能为空），所以统一走 `nodesOf(map, flowId)` 这个小helper 兜一下；
7. 前端列是 `show-overflow-tooltip`（单行省略），所以单元格文案拼成一整串
   「财务审批 · 待 赵财务、陈会计」，悬停看全 —— 不要写两行结构，第二行会被裁掉。

### 25.5 本次**未做**（待决策）

1. **给合同 id=3 补审批实例与待办**（改 `sql/crm.sql` + 新增 alter 脚本，只影响演示数据）——
   不补的话，演示库里始终有一条「审核中但审不动」的合同；
2. **`/crm/contract/audit-list` 收窄为「我的待办」**（按 `sys_approval_task` 取数，
   与看板待办口径统一）：现在是「所有审核中的合同」，所以别人审完的那条仍留在你的列表里；
3. `currentInstanceId()` 报 `4003` 时的提示优化（现在只说「审批实例不存在」）；
4. 主合同列表与导出**不展示**审批环节（避免给最热的列表页加 3~6 次查询、再加一列）；
5. 若要改成「主管审完即生效」，删/停用 `node_id=2` 那一行即可 —— 属**产品口径变更**，另议。

### 25.6 验证

**后端**（`mvn -pl crm-module-crm -am test`）：

- 第一轮：`ContractServiceImplTest` 11/11、`GetContractApprovalToolTest` 3/3。
- 第二轮（预解析 + 列表补字段）后：`ContractServiceImplTest` **15/15**
  —— 新增 4 例：第一个节点待办时报出「下一步=财务审批 + 预计 [赵财务, 陈会计]」（角色分支）、
  角色下没人时节点名照给而预计审批人为空、发起人没有直属上级时预计审批人为空、
  待审核列表回填 `nextNode*` 与 `applyUserName/applyTime`；`GetContractApprovalToolTest` **4/4**。
- 模块合计 `Tests run: 93, Failures: 0, Errors: 11` —— 那 11 例**全部**是
  `CustomerServiceImplTest` 的既有红（`setUp` 第 97 行的 `businessNumberService.next("CUSTOMER")`
  桩在多条用例里没被调用 → `UnnecessaryStubbingException`），**单独跑该类同样红**，与本条无关，
  是 BUG-010 那一类遗留问题。
- ⚠️ **没有**做「预解析 vs 真实解析」的直接对照用例（真解析只在 `submit`/`audit` 内部被调用，
  且它解析的节点在预测时已经变成「当前节点」，两者拿不到同一个输入）。
  同源是靠**逐条对应的 switch 分支 + 代码注释**保证的，每个 `approverType` 各有独立用例覆盖。

**前端**（`aurum-crm-ui`，四个自检脚本 + 构建）：

- `npm run build` ✓（新组件 `ApprovalProgress-*.js` 正常产出）；
- `npm run check:api` ✓ 208 个请求全部能在《05-接口文档》找到（含新增的 `/approval`）；
- `npm run check:mock` ✓ 78/78 通过；
- `npm run check:static` —— 12 项里 11 项 ✓，唯一的 ✗ 是
  `/system/notice (src/layout/components/TopNav.vue)`，即 **BUG-021 的既有问题**
  （顶栏「查看全部」跳 404，状态是「已确认未修复」），与本次改动无关。

⚠️ **未实机验证**（本机没有跑着的后端 + 浏览器端到端）。人工验证路径：

1. 合同审核页：应看到每行「提交人 / 提交时间」有值（不再是 `--`），
   「当前环节」显示「财务审批 · 待 赵财务、陈会计」，停在第一个节点的那条显示
   「部门主管审批 · 待 王总监」+「下一步：财务审批 · 预计 赵财务、陈会计」；
2. 点「当前环节」→ 审批进度弹窗：时间轴每一步谁审的、意见、时间；未到达的节点标「预计审批人」；
3. 合同详情页「审批进度」Tab 同上；
4. `HT20260003`（审核中但没有审批实例）：两列为 `--`，弹窗里给 `abnormalHint`；
5. 用 `finance`（赵财务）或 `finance02`（陈会计）登录 → 合同审核 → 通过
   `HT20260914000002` → 合同变「审核通过 / 已生效」。

### 25.7 防范建议

1. **「状态位只有一个值、流程却有多个阶段」时，必须同时提供阶段可见性。**
   状态位回答不了「现在该谁审」，这不是用户不会看，是接口没给。
2. **演示数据写 `audit_status=1` 时必须同时给出实例与待办**，否则就是「审不动」的死数据；
   上面的检查 SQL 建议在改种子数据后跑一遍。
3. **「待办」类接口应当以 `sys_approval_task` 取数**，而不是只按业务表的状态位过滤 ——
   后者会把「已经轮不到你」的单据也列给你，点下去只会拿到「不是您的审批任务」。

---

## BUG-026 「负责人」下拉只有前 10 个人：选择器端点错用了分页查询

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **后端**（分页参数默认值 + 选择器端点误用分页 API） |
| 文件 | `UserController.java#options`、`UserServiceImpl.java#simpleList`、`PageParam.java` |
| 严重程度 | 高 —— 第 11 个往后的用户**在负责人下拉里根本不存在**，且**没有任何报错** |
| 状态 | ✅ **已上线并实测**（2026-09-15 晚复验，包 `cfe69cce`）|

> 📌 **2026-09-15 晚补记**：本文原写「待重新打包上线」，**那是错的** —— 修复其实
> 早已随包发布，只是没人回来改状态。实测证据：
> ```
> GET /system/user?pageNum=1&pageSize=100   → total = 17
> GET /system/user/options                  → 返回 17 条（修复前会恰好是 10）
> ```
> 17 == 总数，说明走的是**不分页**的 `options()`，不是分页的 `simpleList()`。
>
> ⚠️ **但同一个坑在兄弟端点上还活着**：`GET /system/user/simple-list` 至今
> **仍只返回 10 条**（同一个 `PageParam` 默认值）。目前前端已无调用方
> （`api/system.js#getUserSimpleList` 是死代码），所以不构成实际问题 ——
> 详见 BUG-031。

### 26.1 现象

用户报：「在新增商机时，选取负责人选择不了销售自己」。

下拉能打开、**看起来是好的** —— 里面有一列人，只是**少了第 11 个往后的所有人**。
排在那之后的账号选不了，也包括用户自己。

### 26.2 根因

`GET /system/user/options`（BUG-018 为修权限问题新开的那个端点）实现是：

```java
public CommonResult<List<UserSimpleRespVO>> options(UserPageReqVO reqVO) {
    return CommonResult.success(userService.simpleList(reqVO));   // ← 错在这里
}
```

而 `simpleList` 走的是**分页查询**：

```java
Page<SysUserDO> page = sysUserMapper.selectPage(reqVO, wrapper);
return userVoAssembler.toSimpleList(page.getRecords());
```

`PageParam` 的默认值是 **`DEFAULT_PAGE_SIZE = 10`**，而前端
`api/system.js` 的 `getUserOptions` 请求时**不传任何参数**：

```js
const list = (await request({ url: `${USER}/options`, method: 'get' })) || []
```

于是：`pageSize` 取默认 10 → **只返回前 10 个用户**。

> 📌 前端在 `EntitySelectorDialog` 里给 `useTable` 配了 `pageSize: 9999`，
> 那个参数**根本没到后端** —— `getUserOptions` 把它丢了。
> 这个细节让问题看起来更像前端 bug，实际不是。

**生产库实测**（15 个在职用户，按 `user_id` 升序）：

| 位置 | 账号 | 在下拉里 |
| :--- | :--- | :--- |
| 1–10 | `admin` / `director` / `sales01` ~ `sales05` / `finance` / `purchase01` / `purchase02` | ✅ 能看到 |
| 11–15 | `quality01` / `warehouse01` / `produce01` / `finance02` / **`apengjiang`** | ❌ **看不到** |

`apengjiang`（`user_id=2099713638549680130`）的角色是 **`sales` / 销售专员** ——
一个货真价实的销售，排在第 15 位，所以选不了自己。

### 26.3 影响面

`getUserOptions` 被 `selectorRegistry.js` 的 `userSelector` **6 处复用**：
负责人 / 直属上级 / 部门负责人 / 客户签约人 / 公司签约人 / 角色成员。

⇒ **线索、商机、报价单、合同**四类表单的下拉全都少人，不止商机。

> **客户的表单不受影响** —— 它走的是另一个接口 `/crm/customer/owner-options`，
> 那条返回全量、不分页。`api/system.js:160` 的注释注意到了这个不对称，
> 但把它归因成了 BUG-018 的权限问题，**没有意识到是条数被截断**。

### 26.4 修复

1. `UserService` 新增 `options(UserPageReqVO)` —— **语义明确的不分页版**；
2. `UserServiceImpl` 用 `sysUserMapper.selectList(...)` 实现，并抽出
   `buildSelectorWrapper(reqVO)` 给分页版与不分页版共用 ——
   两边各写一份的话，将来给下拉加过滤条件时很容易只改一处；
3. `UserController#options` 改调 `userService.options(reqVO)`。
   前端**一行都不用改**（它本来就是按 List 消费的）。

### 26.5 为什么没被发现

**种子数据恰好掩盖了它。** 种子里 `sales01` ~ `sales05` 都在第 3–8 位，
全在前 10 之内 —— 所以**演示时一切正常，怎么点都能选到自己**。

只有当一个**界面上新建的账号**（雪花 ID，排在种子数据之后）需要当负责人时，
bug 才暴露出来。这正是用户的场景。

> ⚠️ 还有一层：BUG-018 修复时验证的是「下拉**空了**」→「下拉**有值了**」，
> 而没有验证「**值的数量对不对**」。**静默截断和完全加载失败，症状完全不同，
> 但都会表现成「选不了某个人」。**

### 26.6 防范建议

1. **「选择器 / 下拉」类端点的语义是全量**，用分页 API 实现时**必须显式不分页** ——
   或在端点名上体现（`options` vs `page`）。
2. **`PageParam` 的 `pageSize` 默认 10 是个陷阱**：调用方不传参数时不会报错，
   只会**悄悄少给你数据**。凡是「不传分页参数」的调用，都要问一句「默认值是多少」。
3. **验证列表类接口时，除了「有没有数据」还要看「条数对不对」** ——
   尤其是那些人少的演示库里：**本地数据量小，恰恰是截断类 bug 最容易藏身的地方**。
   一条 `SELECT COUNT(*)` 对比接口返回的 `length` 就能发现。

---

## BUG-027 销售选负责人时弹权限报错：选择器左树的部门接口也要管理员权限

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **前后端**（权限点归属错配，**BUG-018 的同款，另一条边**） |
| 文件 | `DeptController.java`（新增 `/options`）、`api/system.js`、`EntitySelectorDialog.vue` |
| 严重程度 | 中 —— **功能其实没坏，但弹权限报错**，用户以为选不了 |
| 状态 | ⚠️ **前端已部署，但【交互未实测】** |

> 📌 **2026-09-15 晚更正**：本文原写「待上线」，实际前端资源 `index-CeeRwu07.js`
> **两台都已部署**（148 与 121 实测一致）。所以「代码上线」这一半成立，状态改了。
>
> ⚠️ **但另一半不能算数**：本条的现象是「销售点负责人下拉时弹权限报错」——
> 那是**浏览器里的行为**。本轮所有验证都是 API 层的（用 token 直接打接口），
> **没有任何一个真实浏览器点开过这个表单**。
> 「接口返回 0」证明不了「销售点下去不报错」。
> 要结案得有人**用 sales01 登录、真的去新增商机、真的点开负责人下拉**看一眼。

### 27.1 现象

用户报：「使用销售人员选择负责人时会爆出权限报错」。

### 27.2 根因

`selectorRegistry.js` 的 `userSelector` 配了 **`withDeptTree: true`** ——
6 处人员选择器（负责人 / 直属上级 / 部门负责人 / 客户签约人 / 公司签约人 / 角色成员）
**每次打开弹窗**都会加载左侧的组织架构树：

```
EntitySelectorDialog#handleOpen  →  loadDeptTree()  →  GET /system/dept/tree
                                                          ↑
                                              @SaCheckPermission("system:dept:list")
```

而 `system:dept:list` 在种子里**只授予了超级管理员**：

| role_id | role_key | 有 `system:dept:list` |
| :--- | :--- | :--- |
| 1 | `admin` | ✅ |
| 2 | `sales_director` | ❌ |
| 3 | `sales` | ❌ |
| 4 | `finance` | ❌ |

⇒ 任何非管理员打开选择器，左树一加载就是 **403**。

**症状特别有迷惑性**：`loadDeptTree` 自己写了 `try/catch` 降级
（注释还写着「树挂了选择器本身还能用」），所以**右侧人员列表照常加载、负责人其实选得了**；
但 `request.js` 会先弹一个权限报错。用户看到的是「选不了负责人」，
而功能是好的 —— **报错和实际能力对不上**。

### 27.3 为什么是 BUG-018 的同款

BUG-018 修的是选择器弹窗**右侧**的数据源：把 `/system/user/simple-list`
（要求 `system:user:list`）换成新开的仅需登录的 `/system/user/options`。

**但左侧的组织架构树没跟着改。** 同一个弹窗、同一个"选择器"语义、
同一种"权限点归属错了"的病因 —— 只修了一条边。

> 又一次印证了 BUG-018 记下的那个指纹：**「端点自己的注释写着『选择器专用』，
> 却要求一个只有管理员才有的权限」**。这次连"选择器"三个字都换了个位置出现 ——
> `withDeptTree` 是选择器的配置项，却去调一个管理端的接口。

### 27.4 修复

1. `DeptController` 新增 `GET /system/dept/options` —— **仅需登录，不校验权限点**，
   返回与 `/tree` 完全相同的数据；
2. 前端 `api/system.js` 新增 `getDeptOptions()`，`EntitySelectorDialog` 改指向它；
3. **`/tree` 保持原样**：它还服务于组织架构页、人员/角色表单的「上级部门 /
   自定义数据范围」选择器，那些是管理员场景，权限边界应当保留
   （`deptTree` 字段目前只出现在 `system/dept`、`system/role`、`system/user` 三个管理员页）。
4. `docs/05-接口文档.md` 3.3 节补该接口，并写明 `/tree` 与 `/options` 的分工。

### 27.5 防范建议

1. **修一个「某端点权限过严」的问题时，要问一句「同一个场景还有没有别的端点」。**
   选择一个负责人要打两个接口（人员列表 + 部门树），BUG-018 只修了一个就收工了。
   判断方法很简单：**打开一次那个弹窗，看网络面板里有几个请求，逐个对权限。**
2. **前端 try/catch 降级要连同「不要弹那个错」一起考虑。**
   这里的降级是对的（选择器确实还能用），但错误提示照弹，反而制造了
   「功能坏了」的错觉。要么不弹、要么给一句「部门筛选不可用，不影响选择」。
3. **「选择器」这类通用组件用到的接口，权限上都应当按「仅需登录」设计** ——
   它们出现在**所有角色**的表单里，任何管理端权限挂上去都会误伤。
   目前已有三处是这个模式：`/system/user/options`、`/system/dept/options`、
   `/system/dict-data/batch`，新增时照这个来。

---

## BUG-028 选择器按部门筛选时看不到子部门的员工：接口漏返回 `deptId`

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **前后端**（接口漏字段 → 前端筛选静默降级） |
| 文件 | `UserSimpleRespVO.java`（补 `deptId`）、`EntitySelectorDialog.vue#displayList` |
| 严重程度 | 高 —— 点父部门看不到子部门的人，**超管也一样中招**（与权限无关） |
| 状态 | 🟡 **后端一半已上线并实测；前端一半未实测** |

> 📌 **2026-09-15 晚更正**：本文原写「待上线」，那是错的 —— 两半都已发布。
> 但**只有后端那一半能被证实**：
>
> | 半边 | 证据 | 结论 |
> |---|---|---|
> | 后端补 `deptId` | `GET /system/user/options` 实测每条都带 `deptId`（样本：`deptId: 1, deptName: 零捌集团`）| ✅ 已上线 |
> | 前端 `EntitySelectorDialog#displayList` 的父子部门筛选 | **无** | ⚠️ 未验证 |
>
> ⚠️ **这一半恰恰是 bug 的「主犯」**：现象是「点父部门只看到
> 1 人而不是 15 人」，那是**浏览器里点出来的**。接口带了 `deptId` 只说明
> 「原料给到位了」，说明不了前端**用对了** —— 而 28.x 的根因分析里，
> 前端那段筛选逻辑本身就是第二处错。
> 要结案得有人**登录后点开人员选择器、点「华科技集团」根节点，数一数是不是 15 人**。

### 28.1 现象

用户报：「查看组织架构时，不能看到所有员工」，并确认**用的是超管账号**。

在人员选择器里点左侧的「组织架构」树：

| 点的节点 | 应该看到 | 实际看到 |
| :--- | :--- | :--- |
| **华科技集团（根节点）** | **15 人**（全公司） | **1 人** |
| 销售部 | 7 人（直属 3 + 一组 2 + 二组 1 + 三组 1） | **3 人** |
| 采购部 | 2 人 | **1 人** |

（12 个部门、15 个员工，数据已连生产库核实，部门树本身**没有漏**——12 个全是正常状态。）

### 28.2 根因

前端 `EntitySelectorDialog#displayList` 的部门筛选：

```js
const ids = collectDeptIds(selectedDept.value)   // 该节点 + 全部子孙的 id
rows = rows.filter(
  (r) => ids.has(Number(r.deptId)) || r.deptName === selectedDept.value.name
)
```

它靠 **`r.deptId`** 做**子树**匹配。而真后端返回的 `UserSimpleRespVO` 只有
`id / name / username / phone / deptName` —— **没有 `deptId`**。于是：

```
r.deptId          → undefined
Number(undefined) → NaN
ids.has(NaN)      → 恒为 false
```

**`||` 左边永远不成立**，整个过滤退化成右边那句 `deptName === 部门名` ——
**只能精确匹配部门名，不含子部门**。点根节点自然只剩直属那 1 个人。

> 节点本身的注释写着「deptName 由 Service 补」，说明 `toSimpleVO` 是被逐字段审过的；
> 但**没人注意到 `deptId` 还被筛选逻辑用着** ——
> 类注释也只列了「展示用」的四个字段。**注释只覆盖了用途的一半。**

### 28.3 为什么没被发现：mock 有、真后端没有

```js
// src/mock/db.js:563 —— mock 的人【有】deptId
deptId: [2, 3, 4, 5, 6, 7][i % 6],
```

所以 mock 模式下子树过滤**完全正常**，一切看着都对；只有连真后端才犯。
**这是本项目第三次踩同一个坑**（前两次见 BUG-018 的注释、`useTable` 分页那条）。

### 28.4 修复

`UserSimpleRespVO` 补 `deptId` 字段即可，**前端一行不用改**。

`UserConvert` 是 MapStruct，`SysUserDO` 本来就有 `deptId`，加字段后自动映射 ——
**已反查生成代码确认**：

```java
userSimpleRespVO.setDeptId( user.getDeptId() );   // UserConvertImpl#toSimpleVO
```

> 补字段是**向后兼容**的（前端只多拿到一个字段），所以 `/simple-list` 与
> `/options` 两条端点一起受益，不用分别改。

### 28.5 防范建议

1. **给「选择器」类接口定字段时，要把「展示」和「筛选/联动」两类用途分开想。**
   只按 `USER_COLUMNS`（表格列）核对字段，就会漏掉筛选用的 `deptId`。
2. **前端对接口字段做兜底判断时（`a || b`），要确认 `a` 是不是恒为假。**
   这里的 `ids.has(Number(r.deptId))` 恒为 false，但因为右边还能兜住，
   **没有报错、只是结果变少** —— 属于最隐蔽的一类降级。
   写法上可以在开发期断言一次：字段缺失时 `console.warn`，别静默降级。
3. **mock 与真后端的字段集必须逐字对齐。** 本项目已经因为这一点连栽三次，
   建议给 `check:mock` 加一条：**mock 数据里出现的字段，真后端 VO 里也必须有**。


---

## BUG-029 同一个坑第二次踩：`PoolClaimLimiter` 漏 mock，公海领取上限从没被测过

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **后端**（`crm-module-crm`） |
| 文件 | `src/test/java/com/aurum/crm/module/crm/service/CustomerPoolServiceImplTest.java`、`CustomerServiceImplTest.java` |
| 严重程度 | **高** —— 不只是测试红，是一条生产路径（每日领取上限）**从未被验证过** |
| 状态 | ✅ **已修复**（2026-09-15，`mvn test` 实测转绿，见 29.4） |

> ⚠️ **这是 BUG-010 的复发**。BUG-010 记的正是「给 Service 加 `@Resource` 依赖、
> 单测没同步加 `@Mock`」，并且已经写下了防范建议（10.5）。
> 建议写了、还是又犯了一次 —— 说明「顺手加个 mock」这种靠自觉的约束不成立，
> 下面 29.5 换了个能自动挡住的写法。

### 29.1 现象

`mvn test -pl crm-module-crm,crm-module-system,crm-framework/crm-common,crm-framework/crm-security`
在 HEAD 上就是红的，两个类：

```
Tests run: 7,  Failures: 1, Errors: 1  -- CustomerPoolServiceImplTest
Tests run: 13, Failures: 0, Errors: 11 -- CustomerServiceImplTest
```

**此前没被发现，是因为只单独跑过一两个类**（上一个提交只跑了
`-Dtest=GlobalExceptionHandlerTest`），从没跑过整个 `crm-module-crm` 的套件。

### 29.2 根因 A —— 漏 mock（和 BUG-010 同一类）

`CustomerPoolServiceImpl` 后来加了两个 `@Resource` 依赖，测试没跟上：

```java
@Resource private OssStorageService ossStorageService;
@Resource private PoolClaimLimiter  poolClaimLimiter;   // ← 测试里没声明
```

而 `claim()` 的**第一行**就调它：

```java
public void claim(Long id) {
    LoginUser loginUser = LoginUserHolder.getRequired();
    poolClaimLimiter.checkBeforeClaim(2, 2, loginUser.getUserId());   // ← NPE 在这
```

`@InjectMocks` 只注入测试里声明过的 `@Mock`，没声明的就是 `null`。

> ⚠️ **这个漏法的报错最具误导性**：`claim_alreadyOwned_throws2103` 断言的是
> 「抛 2103（客户已被认领）」，实际抛的是 NPE。终端只说
> 「期望 `ServiceException` 实际 `NullPointerException`」——
> **完全指不到「少声明了一个 mock」这个真因**，照着报错去查业务代码只会白费时间。

### 29.3 根因 B —— `setUp` 里的多余打桩

`CustomerServiceImplTest` 的 13 个用例里，只有 2 个真正走到
`businessNumberService.next("CUSTOMER")`，但打桩写在 `setUp` 里。
Mockito 默认 **strict stubs**，`afterEach` 对没用到的桩抛 `UnnecessaryStubbingException`。

```java
// setUp 第 97 行 —— 这行本身没错，错的是它被 11 个用不到的用例共享
when(businessNumberService.next("CUSTOMER")).thenReturn("KH20260914000001");
```

报错指向 `setUp` 那一行，**而真正的问题在那个没用到它的用例**，方向同样被带偏。

> 顺带确认一处顺序：`create_explicitOwner_missing_throws2105` **不需要**这个桩 ——
> `ownerDeptId(999L)` 先抛 2105，根本走不到取单号那一步。

### 29.4 修复

1. `CustomerServiceImplTest`：把单号桩**下沉到真正需要它的两个用例**
   （`create_defaultsCurrentUser*` / `create_explicitOwner_usesOwnerDept`）。
   **没有用 `lenient()`** —— 那只是把提示静音，桩和用例的对应关系依旧看不出来。
2. `CustomerPoolServiceImplTest`：补 `@Mock PoolClaimLimiter` 与 `@Mock OssStorageService`。
3. **补上这条从未被覆盖的生产路径**，两条新用例：
   - `claim_checksLimitBeforeTouchingAnyData` —— 上限校验必须在**碰任何数据之前**
     （含 `inOrder` 顺序断言：若有人把校验挪到查询之后，已超限的人仍会先把客户读出来，
     结果看起来一样，只有顺序断言挡得住）；
   - `claim_limitReached_throws2601AndWritesNothing` —— 超限抛 2601 时
     **一个写操作都不能发生**（不读客户、不改 owner、不留流转日志）。

### 29.5 验证（2026-09-15，终端原样复制）

```
$ mvn -q -pl crm-module-crm -am -Dtest=CustomerServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.239 s
    -- in com.aurum.crm.module.crm.service.CustomerServiceImplTest

$ mvn -q -pl crm-module-crm -am -Dtest=CustomerPoolServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.832 s
    -- in com.aurum.crm.module.crm.service.CustomerPoolServiceImplTest

$ mvn test -pl crm-framework/crm-common,crm-framework/crm-security,crm-module-crm,crm-module-system
[INFO] Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

8 个类合计 66 项全绿：
`GlobalExceptionHandlerTest 8` / `SaTokenConfigTest 7` / `SeedPasswordTest 3` /
`AiToolRegistryTest 14` / `ContractServiceImplTest 6` / `CustomerPoolServiceImplTest 9` /
`CustomerServiceImplTest 13` / `EmailCodeServiceImplTest 6`。

> ⚠️ **没跑 `crm-server` 模块**：它的 `PersistenceLayerTest` 会往**真实库**插数据
> （`dictTypeMapper.insert`）。全量 `mvn test` 之前先看 `docs/09-生产部署.md`。

### 29.6 防范建议

1. **别再靠「顺手加个 mock」的自自觉** —— BUG-010 的建议没挡住 BUG-029。
   可考虑在 CI / 本地门禁里固定跑一次 `mvn test -pl crm-module-crm,...`，
   让红的当场暴露，而不是等到几个月后偶然发现。
2. **`UnnecessaryStubbingException` 的修法是「下沉桩」，不是 `lenient()`。**
   加 `lenient()` 只是让报错消失，桩与用例的对应关系反而更模糊了。
3. **给 Service 加依赖时，搜索该 Service 的所有测试类**，而不是只看「它的单测」——
   `CustomerPoolServiceImpl` 的依赖变更同样影响任何 `@InjectMocks` 它的地方。

---

## BUG-031 一个 `docs:` 提交悄悄删掉了权限注解，`/system/user/simple-list` 至今没有权限校验

| 项 | 内容 |
| :--- | :--- |
| 所属模块 | **后端**（`UserController#simpleList`） |
| 文件 | `crm-module-system/.../controller/UserController.java` |
| 严重程度 | **中** —— 权限边界与文档不符；但现实影响有限（见 31.4）|
| 状态 | ✅ **已修复** —— 注解已恢复，并加了自动门禁防止再犯（见 31.5）<br>⚠️ **修复尚未部署**：线上跑的还是宽松版，发布后才生效 |

### 31.1 现象

2026-09-15 晚核对部署一致性时顺带发现：

```
sales01 是否持有 system:user:list : False

GET /system/user?pageNum=1&pageSize=5   →  code=403   ← 权限拦截正常
GET /system/user/simple-list            →  code=0, 返回 10 条
   样本：{"id":1,"name":"超级管理员","username":"admin",
          "phone":"13800000001","deptId":1,"deptName":"零捌集团"}
```

一个**不持有** `system:user:list` 的销售账号，可以拿到人员的姓名、账号、
手机号、部门。

### 31.2 根因：一个标着 `docs:` 的提交删掉了授权检查

```bash
git show d3bcd8e -- .../UserController.java
#  1 file changed, 1 deletion(-)
# -    @SaCheckPermission("system:user:list")
```

`d3bcd8e` 的提交信息是 **`docs:修复部分合同 产品bug`**，但它实际改了
**42 个文件、1024 行插入**（`NumberingRuleServiceImpl`、`sql/crm.sql` 等），
对 `UserController.java` 的唯一改动就是**删掉这一行**。

> ⚠️ **两个独立的问题叠在一起**：
> ① 提交信息标 `docs:`，实际是功能改动 —— 谁能想到一个「文档提交」会动权限？
> ② 删除本身**没有在提交信息里提一个字**，也没有任何地方记录「为什么删」。

### 31.3 代码与自己的文档互相矛盾

`/options` 的 javadoc 原话是「**为什么不直接放宽 `/simple-list`**：那会让
『人员管理』这个模块边界失效 …… **这里保留原端点的严格性**，另开一个语义明确的
『选择器端点』」。

也就是说：**声明的意图是「保留 strict」，实际动作是「删掉 strict」**，
两者方向相反，而且过了整整一天没人发现（因为没人会去核对一条注释和一行注解）。

### 31.4 现实影响：有限，但不为零

必须说清楚**不要夸大**：

| | |
|---|---|
| **不构成新的越权面** | `GET /system/user/options` 是**有意**设计成「仅需登录、不校验权限点」的（它的 javadoc 论证过「对内部 CRM 来说，让任何在职员工查到同事的姓名/电话/部门是可接受的」），而它返回**全部 17 人**。`simple-list` 只返回**前 10 人** —— 严格是它的**子集**。 |
| **所以泄露面没有扩大** | 销售本来就能通过 `/options` 拿到更多。 |
| **但确实是个问题** | ① 权限边界靠「另一条路也能走」来兜底，不是设计，是巧合；② 一旦哪天有人收紧 `/options`，`simple-list` 就会成为一个**没人记得的后门**；③ 代码与注释矛盾，下一个人会照着注释做出错误判断。 |

### 31.5 处置：恢复注解 + 加自动门禁

**处置决定：恢复 `@SaCheckPermission("system:user:list")`。**
依据：`/options` 的 javadoc 白纸黑字写着「这里保留原端点的严格性」，
恢复才是回到既定设计。前端 `api/system.js#getUserSimpleList` **已无任何调用方**，
恢复零风险。

**但光恢复注解不够** —— 这个 bug 的可怕之处在于它**不会以任何方式暴露自己**：

| 环节 | 删掉注解后会怎样 |
|---|---|
| 编译 | 不报错 |
| 单测 | **不红**。本项目单测全是纯 Mockito 单测，不加载 Spring 上下文，
压根不经过 `SaInterceptor` —— 注解在与不在跑得一模一样 |
| 接口 | 不报错。少一道校验只会让请求**更顺畅**地通过 |

唯一暴露方式是「拿低权限账号真的去调一次」—— 要人工、要记得做。
所以补了 `UserControllerPermissionTest`，把这件事变成一次 `mvn test`：

1. **兜底规则**：`UserController` 里每个 `@XxxMapping` 方法都必须带
   `@SaCheckPermission`，除非在 `LOGIN_ONLY` 白名单里（当前只有 `options`，1 条）。
   顺带挡住「新增端点忘了加权限」—— 那种更危险，因为从一开始就没有校验，
   更没人会注意到「少了什么」。
2. **定点回归**：`simpleList` 必须要求 `system:user:list`，连**权限码本身**都钉死
   （把注解换成别的权限码，第 1 条是看不出来的）。

> **这个测试验证过「它在 bug 存在时确实会红」**：临时删掉注解后跑，
> 两条用例都失败，报错信息直接点名 BUG-031 和该怎么处理；恢复后转绿。
> 不做这一步的话，「新加的测试」和「没有测试」在体感上没区别 ——
> 本项目已经吃过一次「文档写了修复、实际没落地」的亏（见 10.6）。

> ⚠️ 同理，**别让「代码说的」和「注释说的」继续打架** ——
> 这次就是因为信了注释（`/options` 说 `/simple-list` 要权限）才发现对不上，
> 下一个人若反过来信代码，就会得出错误结论。

### 31.6 教训

1. **提交信息的前缀要当真**。`docs:` 的提交不该改行为；真改了就该在信息里写清楚。
   `git log --oneline` 是排查时的第一入口，前缀错了等于指错方向。
2. **删除一行注解是「看不见的改动」**。它不触发编译错误、不影响任何测试
   （本项目的单测全是 Mockito 单测，不加载 Spring 上下文，压根走不到拦截器），
   也不会让接口报错 —— **唯一的暴露方式是拿低权限账号真的去调一次**。
   这正是 2026-09-15 那轮「权限矩阵」测试的价值。
3. **别把「另一条路也能拿到」当作安全依据**。那是巧合，不是边界。
