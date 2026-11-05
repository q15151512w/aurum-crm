package com.aurum.crm.module.system.core.log;

import cn.hutool.extra.servlet.JakartaServletUtil;
import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.module.system.dal.dataobject.SysDeptDO;
import com.aurum.crm.module.system.dal.dataobject.SysOperationLogDO;
import com.aurum.crm.module.system.dal.mysql.SysDeptMapper;
import com.aurum.crm.module.system.model.OperationBusinessType;
import com.aurum.crm.module.system.model.OperationResult;
import com.aurum.crm.module.system.service.OperationLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 操作日志切面：自动记录 controller 包下所有<b>写接口</b>（POST / PUT / DELETE）的调用。
 *
 * <p><b>切点为什么按 HTTP 动词而不是按注解</b>：要的是「全覆盖」。按动词切是零遗漏的；
 * 而要求每个方法手写 {@code @Log}，漏标不会报错、只会静默少一条日志 —— 那才是不可靠的。
 * {@code @Log} 保留下来只用于修正自动推导不准的地方（如 POST 但语义是「审核」）。
 *
 * <p><b>三条硬约束</b>：
 * <ol>
 *     <li><b>绝不影响业务</b>：整个记录过程包在 try/catch 里，记日志失败只打 warn。
 *         用户的操作不能因为「日志表写不进去」而失败。</li>
 *     <li><b>必须脱敏</b>：{@code /auth/login}、{@code PUT /auth/password} 的参数里有明文密码。
 *         见 {@link #desensitize}。</li>
 *     <li><b>不记返回值</b>：成功时不写 {@code json_result}（体积 + 泄露风险）。
 *         失败时把异常信息写进 {@code error_msg} 就够了。</li>
 * </ol>
 *
 * <p><b>不会递归</b>：切点只匹配 {@code controller} 包，而写库走的是
 * {@code OperationLogService}（Service 层），不在切点范围内。
 *
 * <p><b>同步写库</b>：一次 INSERT，在 {@code finally} 里执行。没有上异步 ——
 * {@code @Async} 需要额外的线程池配置，而且异步线程里拿不到 {@code LoginUserHolder}
 * 与 {@code RequestContextHolder}（都是 ThreadLocal），得先把上下文捞出来传过去。
 * 将来真成为瓶颈再说。
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    /** 操作类别：1 后台用户（本项目只有 PC 后台，没有手机端） */
    private static final int OPERATOR_TYPE_ADMIN = 1;

    /** 脱敏后的占位符 */
    private static final String MASK = "****";

    /**
     * 需要脱敏的参数名（<b>比较时统一转小写</b>，所以这里都写小写）。
     *
     * <p>覆盖了项目里所有含凭证的请求字段：登录密码、改密的新旧密码、邮箱验证码、算术验证码。
     * 新增这类字段时记得回来补 —— 漏一个就是把明文密码写进日志表。
     *
     * <p>⚠️ 验证码字段尤其不能漏：{@code sys_operation_log} 是管理员在界面上看得到的，
     * 而验证码在 5 分钟内就是一张通行证。
     */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "oldpassword", "newpassword", "confirmpassword", "emailcode", "captchacode");

    /** 各文本列的截断长度 */
    private static final int LEN_TITLE = 64;
    private static final int LEN_METHOD = 255;
    private static final int LEN_URL = 255;
    private static final int LEN_IP = 64;
    private static final int LEN_NAME = 64;
    private static final int LEN_PARAM = 2000;
    private static final int LEN_ERROR = 2000;

    @Resource
    private OperationLogService operationLogService;
    @Resource
    private SysDeptMapper sysDeptMapper;
    @Resource
    private ObjectMapper objectMapper;

    @Around("execution(* com.aurum.crm.module..controller.*.*(..)) "
            + "&& (@annotation(org.springframework.web.bind.annotation.PostMapping) "
            + "|| @annotation(org.springframework.web.bind.annotation.PutMapping) "
            + "|| @annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable error = null;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                save(pjp, error, System.currentTimeMillis() - start);
            } catch (Exception e) {
                // 约束 ①：记日志失败绝不能让业务失败
                log.warn("[操作日志] 记录失败，已忽略。method={}", pjp.getSignature().toShortString(), e);
            }
        }
    }

    private void save(ProceedingJoinPoint pjp, Throwable error, long costMs) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();

        SysOperationLogDO logDO = new SysOperationLogDO();
        logDO.setMethod(truncate(
                method.getDeclaringClass().getSimpleName() + "." + method.getName() + "()", LEN_METHOD));
        logDO.setTitle(resolveTitle(method));
        logDO.setBusinessType(resolveBusinessType(method).getCode());
        logDO.setOperatorType(OPERATOR_TYPE_ADMIN);
        logDO.setOperTime(LocalDateTime.now());
        logDO.setCostTime(costMs);
        logDO.setOperParam(serializeArgs(pjp));

        HttpServletRequest request = currentRequest();
        if (request != null) {
            logDO.setRequestMethod(truncate(request.getMethod(), 16));
            logDO.setOperUrl(truncate(request.getRequestURI(), LEN_URL));
            logDO.setOperIp(truncate(JakartaServletUtil.getClientIP(request), LEN_IP));
        }

        fillOperator(logDO);

        if (error != null) {
            logDO.setStatus(OperationResult.FAILURE);
            logDO.setErrorMsg(truncate(error.getMessage(), LEN_ERROR));
            // json_result 有意留空：失败信息与 error_msg 重复，成功时又不需要记返回值
        } else {
            logDO.setStatus(OperationResult.SUCCESS);
        }

        operationLogService.record(logDO);
    }

    // ========================================================================
    //  标题与业务类型
    // ========================================================================

    /** 模块标题：{@code @Log.title()} > Controller 上的 {@code @Tag(name)} > 类名 */
    private String resolveTitle(Method method) {
        Log annotation = method.getAnnotation(Log.class);
        if (annotation != null && StringUtils.hasText(annotation.title())) {
            return truncate(annotation.title(), LEN_TITLE);
        }
        Tag tag = method.getDeclaringClass().getAnnotation(Tag.class);
        if (tag != null && StringUtils.hasText(tag.name())) {
            return truncate(tag.name(), LEN_TITLE);
        }
        return truncate(method.getDeclaringClass().getSimpleName(), LEN_TITLE);
    }

    /**
     * 业务类型：{@code @Log.businessType()} 显式指定 > 按 HTTP 动词推导。
     *
     * <p>「POST 就是新增」这个推导在多数接口上成立，但也有 POST 表示
     * 「审核 / 提交 / 领取 / 放入公海」的 —— 那些用 {@code @Log} 覆盖即可。
     */
    private OperationBusinessType resolveBusinessType(Method method) {
        Log annotation = method.getAnnotation(Log.class);
        if (annotation != null && annotation.businessType() != OperationBusinessType.OTHER) {
            return annotation.businessType();
        }
        if (method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)) {
            return OperationBusinessType.CREATE;
        }
        if (method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)) {
            return OperationBusinessType.UPDATE;
        }
        if (method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)) {
            return OperationBusinessType.DELETE;
        }
        return OperationBusinessType.OTHER;
    }

    // ========================================================================
    //  操作人
    // ========================================================================

    /**
     * 补操作人信息。
     *
     * <p>⚠️ {@code LoginUserHolder.get()} 可能为 <b>null</b> —— 登录接口本身
     * （{@code POST /auth/login}）执行时还没有登录上下文，这时操作人各列留空是正常的，
     * 不要用 {@code getRequired()}，那会抛异常（虽然后面有 try/catch 兜住，但会白打一条 warn）。
     */
    private void fillOperator(SysOperationLogDO logDO) {
        LoginUser me = LoginUserHolder.get();
        if (me == null) {
            return;
        }
        logDO.setOperatorId(me.getUserId());
        String name = StringUtils.hasText(me.getRealName()) ? me.getRealName() : me.getUsername();
        logDO.setOperName(truncate(name, LEN_NAME));

        // 登录上下文里只有 deptId，没有部门名，只能回查一次。
        // 一次主键查询，可接受；真要省可以加缓存，但会引入「改了部门名日志里还是旧名」的问题。
        if (me.getDeptId() != null) {
            SysDeptDO dept = sysDeptMapper.selectById(me.getDeptId());
            if (dept != null) {
                logDO.setDeptName(truncate(dept.getDeptName(), LEN_NAME));
            }
        }
    }

    // ========================================================================
    //  请求参数序列化 + 脱敏
    // ========================================================================

    /**
     * 把方法入参序列化成 JSON 字符串（已脱敏）。
     *
     * <p>形状：只有一个有效参数时直接序列化它本身，得到 {@code {"name":"x"}}；
     * 多个参数时按参数名包一层。
     *
     * <p>⚠️ 本项目编译<b>没有开 {@code -parameters}</b>，所以
     * {@code getParameterNames()} 拿到的多半是 {@code arg0/arg1} 这种合成名 ——
     * 这就是为什么要给「单参数」做特殊处理：绝大多数写接口只有一个 {@code @RequestBody}，
     * 那样输出形状才是干净的业务对象。
     */
    private String serializeArgs(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }

        String[] names = ((MethodSignature) pjp.getSignature()).getParameterNames();
        Map<String, Object> payload = new LinkedHashMap<>();

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            String name = names != null && i < names.length ? names[i] : ("arg" + i);

            if (isServletType(arg) || arg instanceof BindingResult) {
                continue;
            }
            // 文件只记「文件名 + 大小」，内容不序列化（会炸，也没意义）
            if (arg instanceof MultipartFile file) {
                payload.put(name, fileSummary(file));
                continue;
            }
            if (arg instanceof MultipartFile[] files) {
                List<String> summaries = new ArrayList<>();
                for (MultipartFile f : files) {
                    summaries.add(fileSummary(f));
                }
                payload.put(name, summaries);
                continue;
            }
            payload.put(name, arg);
        }

        if (payload.isEmpty()) {
            return null;
        }
        Object toWrite = payload.size() == 1 ? payload.values().iterator().next() : payload;
        return truncate(toJson(toWrite), LEN_PARAM);
    }

    private boolean isServletType(Object arg) {
        return arg instanceof HttpServletRequest
                || arg instanceof HttpServletResponse;
    }

    private String fileSummary(MultipartFile file) {
        if (file == null) {
            return null;
        }
        return file.getOriginalFilename() + "（" + file.getSize() + " 字节）";
    }

    /** 序列化失败（循环引用、无法序列化的类型）时返回一句说明而不是抛异常 */
    private String toJson(Object value) {
        try {
            JsonNode node = objectMapper.valueToTree(value);
            desensitize(node);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("[操作日志] 请求参数序列化失败，该次记录不写参数", e);
            return null;
        }
    }

    /**
     * 递归把敏感字段替换成 {@code ****}。
     *
     * <p>⚠️ <b>这里漏一个字段，就是把明文密码写进日志表</b>。
     * 字段名比较是<b>忽略大小写</b>的（{@code Password} / {@code oldPassword} 都能命中）。
     */
    private void desensitize(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> fieldNames = new ArrayList<>();
            object.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                if (SENSITIVE_KEYS.contains(fieldName.toLowerCase())) {
                    object.put(fieldName, MASK);
                } else {
                    desensitize(object.get(fieldName));
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (JsonNode child : array) {
                desensitize(child);
            }
        }
    }

    // ========================================================================
    //  杂项
    // ========================================================================

    /** 取当前请求。拿不到（非 web 线程调用）时返回 null，调用处按需判空 */
    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    /** 截断到列长度，防止超长把整条日志写失败 */
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
