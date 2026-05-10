package com.template;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TemplateApplicationTests — P0 冒烟测试
 *
 * 验证 Spring 上下文正常加载、DataSource Bean 可用、应用启动成功。
 * 使用 H2 内存数据库（application-test.yml），无需外部 MySQL。
 */
@SpringBootTest
@ActiveProfiles("test")
class TemplateApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TemplateApplication application;

    @Test
    void contextLoads() {
        // 验证 Spring 上下文加载成功
        assertThat(application).isNotNull();
    }

    @Test
    void dataSourceBeanExists() {
        // 验证 DataSource Bean 已注入且可用
        assertThat(dataSource).isNotNull();
    }

    @Test
    void applicationStartsSuccessfully() {
        // 验证 TemplateApplication 启动不抛出异常
        // CGLIB 代理类名含 $$SpringCGLIB$$，使用 contains 匹配
        assertThat(application.getClass().getName()).contains("TemplateApplication");
    }
}
