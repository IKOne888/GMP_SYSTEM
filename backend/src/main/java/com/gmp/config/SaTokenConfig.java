package com.gmp.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token配置类
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 跨域允许的来源白名单（逗号分隔），生产环境必须通过环境变量配置
     */
    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:8080}")
    private String[] allowedOrigins;

    /**
     * 注册Sa-Token拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            // 登录校验
            SaRouter.match("/**")
                    .notMatch("/auth/login")
                    // 图形验证码接口需在登录前访问，必须放行，否则会因未登录返回401
                    .notMatch("/auth/captcha")
                    .notMatch("/doc.html")
                    .notMatch("/swagger-resources/**")
                    .notMatch("/v3/**")
                    .notMatch("/webjars/**")
                    // 视频播放/下载由浏览器原生 <video>/<a> 直接发起，无法携带请求头 Token，故放行
                    // （视频内容本身仍通过 MinIO 存储，访问需知晓视频ID）
                    .notMatch("/training/videos/*/stream")
                    .notMatch("/training/videos/*/download")
                    .notMatch("/training/video/*/stream")
                    .notMatch("/training/video/*/download")
                    // 学员端视频播放同样由浏览器原生 <video> 发起，放行（内容在 MinIO，需知晓视频ID）
                    .notMatch("/study/videos/*/stream")
                    .check(r -> StpUtil.checkLogin());

            // 学员端接口（/study/**）仅需登录，不做管理角色限制；
            // 具体数据归属（仅能操作本人任务/记录）在 StudyService 层强制校验，
            // 故此处无需额外 addRole 规则，由上方全局 checkLogin 兜底即可。

            // 角色校验 - PC后台接口（满足任一角色即可）
            SaRouter.match("/system/**", r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN", "DEPT_LEADER"));

            // 通知公告（/system/notices）：发布与历史查询不对部门负责人开放，
            // 必须在上面 /system/** 通用规则之后追加更严格的规则；Sa-Token 会依次执行两条规则，
            // 部门负责人会在本条规则上被拒绝（与 /system/department 写操作的处理方式一致）
            SaRouter.match("/system/notices", "/system/notices/**")
                    .check(r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN"));

            // 部门/岗位的"增删改"仅限系统管理员（SUPER_ADMIN）；查询接口保持登录即可，
            // 以便课程、用户等表单可以正常读取部门/岗位下拉数据
            SaRouter.match("/system/department", "/system/department/**",
                            "/system/departments", "/system/departments/**")
                    .matchMethod("POST", "PUT", "DELETE")
                    .check(r -> StpUtil.checkRoleOr("SUPER_ADMIN"));
            SaRouter.match("/system/position", "/system/position/**",
                            "/system/positions", "/system/positions/**")
                    .matchMethod("POST", "PUT", "DELETE")
                    .check(r -> StpUtil.checkRoleOr("SUPER_ADMIN"));

            // 角色管理（/system/roles/**）所有操作均仅限系统管理员（SUPER_ADMIN）
            // 本规则必须在上方 /system/** 通用规则之后追加，收紧规则覆盖前者
            SaRouter.match("/system/roles", "/system/roles/**")
                    .check(r -> StpUtil.checkRole("SUPER_ADMIN"));

            // 菜单树查询（GET /system/menus/tree）对所有登录用户开放，
            // 以便管理端各角色渲染侧边栏结构；其他 /system/menus 路径暂未使用
            // 角色菜单绑定接口（/system/roles/{roleId}/bind-menus）仅限系统管理员
            // 虽已被上面的 /system/roles/** 规则覆盖，但单独列出保持规则清晰
            SaRouter.match("/system/roles/*/bind-menus")
                    .check(r -> StpUtil.checkRole("SUPER_ADMIN"));

            // 角色校验 - 培训管理（同时兼容单数历史路径与前端复数 RESTful 路径）
            SaRouter.match("/training/plan/**", r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN"));
            SaRouter.match("/training/task/**", r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN"));
            // 培训计划（复数）：增删改、提交审批、审批通过/驳回均限管理员角色
            SaRouter.match("/training/plans", "/training/plans/**")
                    .check(r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN"));
            // 培训任务 - 两级审核专属接口：额外放行部门负责人（DEPT_LEADER）参与一级审核；
            // 具体的阶段与部门归属校验（主管只能审本部门、QA 只能复核）在 Service 层强制执行。
            // 注意：该规则必须先于下面的通用规则判断，故下面的规则统一 notMatch 审核路径。
            SaRouter.match("/training/tasks/review/**",
                            "/training/tasks/*/leader-review",
                            "/training/tasks/*/qa-review")
                    .check(r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN", "DEPT_LEADER"));
            // 培训任务（复数）查询：管理员 + 部门负责人（部门负责人仅能看到本部门数据，由 Service 层隔离）
            SaRouter.match("/training/tasks", "/training/tasks/**")
                    .notMatch("/training/tasks/review/**",
                            "/training/tasks/*/leader-review",
                            "/training/tasks/*/qa-review")
                    .matchMethod("GET")
                    .check(r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN", "DEPT_LEADER"));
            // 培训任务（复数）写操作：分配任务、免考确认完成、催办通知均限管理员角色
            SaRouter.match("/training/tasks", "/training/tasks/**")
                    .notMatch("/training/tasks/review/**",
                            "/training/tasks/*/leader-review",
                            "/training/tasks/*/qa-review")
                    .matchMethod("POST", "PUT", "DELETE")
                    .check(r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN"));
            // 课程管理 - 增删改仅限管理员；查询仅需登录即可（各角色可见课程列表）
            SaRouter.match("/training/courses", r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN"));
            SaRouter.match("/training/courses/*", r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN"));
            // 课程多级子路径（如资料附件 /training/courses/{id}/files）：写操作仅限管理员，查询由全局登录校验兜底
            SaRouter.match("/training/courses/**")
                    .matchMethod("POST", "PUT", "DELETE")
                    .check(r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN"));
            // 视频模块（单数/复数兼容）：上传/删除等写操作限管理员角色；
            // 查询（GET 列表）由全局登录校验兜底，播放/下载已在白名单放行
            SaRouter.match("/training/video", "/training/video/**", "/training/videos", "/training/videos/**")
                    .matchMethod("POST", "PUT", "DELETE")
                    .check(r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN"));

            // 培训需求管理（/training/demands）：登记、处理（采纳/拒绝）、删除均限管理员角色
            SaRouter.match("/training/demands", "/training/demands/**")
                    .check(r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN"));
            // 线下培训登记与电子签到（/training/offline/**）：场次登记、签到码管理、
            // 签到明细查询与导出、补签均限管理员角色；学员端签到走 /study/offline/**，
            // 仅需登录，数据归属由 Service 层按登录态强制隔离
            SaRouter.match("/training/offline", "/training/offline/**")
                    .check(r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN"));
            // PDF 课件管理（/training/course-pdfs）：上传、预览、启停、删除均限管理员角色
            SaRouter.match("/training/course-pdfs", "/training/course-pdfs/**")
                    .check(r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN"));

            // 课程分类管理：GET（下拉选项 + 分页查询）对所有登录用户开放，
            // POST/PUT/DELETE 写操作仅限管理员角色维护
            SaRouter.match("/training/course-categories", "/training/course-categories/**")
                    .matchMethod("POST", "PUT", "DELETE")
                    .check(r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN"));

            // 员工培训档案（管理员视角）：四类管理员均可查看
            SaRouter.match("/system/archive", "/system/archive/**")
                    .check(r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN", "DEPT_LEADER"));

            // 考试管理 - 题库 / 试卷，仅管理员角色可维护与管理
            SaRouter.match("/exam/**", r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER", "TRAINING_ADMIN"));

            // 角色校验 - 审计日志（只读）
            SaRouter.match("/system/auditLog/**", r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER"));
            // 审计日志（前端实际路径 /audit/logs）：仅系统管理员与质量管理员可查看/导出
            SaRouter.match("/audit/**", r -> StpUtil.checkRoleOr("SUPER_ADMIN", "QA_MANAGER"));

        })).addPathPatterns("/**");
    }

    /**
     * 跨域配置
     * 生产环境必须配置明确的前端来源白名单，禁止使用 * 通配
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 允许的来源白名单（生产环境必须通过环境变量 cors.allowed-origins 配置）
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
