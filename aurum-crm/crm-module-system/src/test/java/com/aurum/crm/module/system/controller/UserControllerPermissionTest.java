package com.aurum.crm.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.module.system.model.dto.UserPageReqVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserController} 的权限注解门禁。
 *
 * <p><b>为什么要专门为一个类的注解写测试（BUG-031）</b>：2026-09-14 的提交
 * {@code d3bcd8e}（提交信息是 {@code docs:修复部分合同 产品bug}）删掉了
 * {@code simpleList} 上的 {@code @SaCheckPermission}，整整一天没人发现 ——
 * 因为这类改动是「隐形」的：
 *
 * <ul>
 *     <li>编译不报错；</li>
 *     <li>单测不红 —— 本项目的单测全是纯 Mockito 单测，<b>不加载 Spring 上下文</b>，
 *         压根不经过 {@code SaInterceptor}，注解在与不在它都跑得一样；</li>
 *     <li>接口不报错 —— 少一道校验只会让请求<b>更顺畅</b>地通过，
 *         没有任何异常、日志或返回值能反映出来。</li>
 * </ul>
 *
 * <p>唯一的暴露方式是「拿低权限账号真的去调用一次」（2026-09-15 就是这么查出来的：
 * sales01 不持有 {@code system:user:list}，调 {@code /system/user} 得 403，
 * 调 {@code /system/user/simple-list} 却得 200）。那条路要人工、要记得做。
 * 这个测试把同一件事变成一次 {@code mvn test}。
 *
 * <p><b>它挡两类改动</b>：删掉已有的权限点；以及新增一个忘了加权限点的端点。
 * 后者其实更危险 —— 新端点从一开始就没有校验，更没人会注意到「少了什么」。
 */
class UserControllerPermissionTest {

    /**
     * <b>有意</b>做成「仅需登录、不带权限点」的端点。
     *
     * <p>目前只有 {@code options}：它的存在理由见其 javadoc ——
     * 销售角色要用「负责人/签约人」下拉，却不可能拥有 {@code system:user:list}，
     * 所以单开了一个只要求登录的端点。
     *
     * <p>⚠️ 往这个名单里加名字，等于放宽一条权限边界，必须是有意识的决定，
     * 而不是为了让测试变绿。
     */
    private static final Set<String> LOGIN_ONLY = Set.of("options");

    @Test
    void everyEndpointCarriesPermissionExceptDocumentedLoginOnlyOnes() {
        Set<String> scanned = new LinkedHashSet<>();

        for (Method m : UserController.class.getDeclaredMethods()) {
            if (!isEndpoint(m)) {
                continue;
            }
            scanned.add(m.getName());
            boolean annotated = m.getAnnotation(SaCheckPermission.class) != null;

            if (LOGIN_ONLY.contains(m.getName())) {
                assertThat(annotated)
                        .as("%s 被列为「仅需登录」，就不该再带权限点 —— "
                                + "若确实该收紧，把它从 LOGIN_ONLY 里移出去", m.getName())
                        .isFalse();
            } else {
                assertThat(annotated)
                        .as("%s 缺少 @SaCheckPermission。BUG-031 就是这么漏的："
                                + "权限注解被删掉后，编译、单测、接口全都不报错。"
                                + "若该端点确实只需登录，请把它加入 LOGIN_ONLY 并说明理由",
                                m.getName())
                        .isTrue();
            }
        }

        // 兜底：万一判据失效导致一个方法都没扫到，别让本测试「空跑通过」
        assertThat(scanned)
                .as("一个端点都没扫到，说明 isEndpoint 的判据失效了，本测试等于没跑")
                .isNotEmpty();
    }

    /**
     * BUG-031 的定点回归：{@code /simple-list} 必须要求 {@code system:user:list}。
     *
     * <p>上面那条通用规则也能挡住「注解被删」，但这条额外钉死了<b>权限码本身</b> ——
     * 把注解换成另一个权限码（比如手滑写成 {@code system:user:create}）
     * 是通用规则看不出来的。
     */
    @Test
    void simpleList_requiresUserListPermission() throws Exception {
        Method m = UserController.class.getDeclaredMethod("simpleList", UserPageReqVO.class);

        SaCheckPermission p = m.getAnnotation(SaCheckPermission.class);

        assertThat(p).as("simpleList 必须有权限注解").isNotNull();
        assertThat(p.value()).as("权限码不能换").containsExactly("system:user:list");
    }

    /** 是不是一个 Spring MVC 端点：带任一 {@code @XxxMapping} 注解的方法 */
    private static boolean isEndpoint(Method m) {
        return Arrays.stream(m.getAnnotations())
                .map(a -> a.annotationType().getSimpleName())
                .anyMatch(n -> n.equals("RequestMapping") || n.endsWith("Mapping"));
    }
}
