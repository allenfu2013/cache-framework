package org.allen.cacheframework.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
/**
 * Created by Administrator on 2015/10/20.
 */
@Documented
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface KeyParam {
    /**
     * 动态key，将方法参数值作为cache key的一部分
     * 使用Spring Expression Language指定
     * 例如: Account getAccount(@KeyParam(param = "#account.id", connector = ":")Account account)
     * 比如key为"ACCOUNT", 参数account的id=5, 则cache key为"ACCOUNT:5"
     * @return
     */
    String param() default "";

    /**
     * cache key的连接符
     * 例如: Account getAccount(@KeyParam(connector = ":")Long id)
     * 比如key为"ACCOUNT", 参数为id=5, connector=":", 则cache key为"ACCOUNT:5"
     * @return
     */
    String connector() default "";

    /**
     * 是否为多个key
     * 例如: List<Account> getAccounts(@KeyParam(multiKey = true)List<Long> ids)
     * @return
     */
    boolean multiKey() default false;
}
