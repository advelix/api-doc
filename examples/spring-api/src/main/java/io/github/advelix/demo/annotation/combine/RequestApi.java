package io.github.advelix.demo.annotation.combine;

import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 整合 {@link RequestMapping} 的通用注解，HTTP 方法由 {@link #method()} 在使用处指定。
 *
 * @author jiangwh
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RequestMapping
public @interface RequestApi {

    /**
     * Alias for {@link RequestMapping#value()}
     */
    @AliasFor(annotation = RequestMapping.class)
    String[] value() default {};

    /**
     * Alias for {@link RequestMapping#params()}
     */
    @AliasFor(annotation = RequestMapping.class)
    String[] params() default {};

    /**
     * 请求方法，支持单值与多值（例如 {@code method = {GET, PUT}}）。
     */
    RequestMethod[] method() default {};

    /**
     * Alias for {@link RequestMapping#consumes()}
     */
    @AliasFor(annotation = RequestMapping.class)
    String[] consumes() default {};

    /**
     * Alias for {@link RequestMapping#produces()}
     */
    @AliasFor(annotation = RequestMapping.class)
    String[] produces() default {};
}
