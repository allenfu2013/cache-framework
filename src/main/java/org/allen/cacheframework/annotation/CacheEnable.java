package org.allen.cacheframework.annotation;

import org.allen.cacheframework.OperationType;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by Administrator on 2015/10/20.
 */
@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface CacheEnable {

    /**
     * 缓存操作类型
     *
     * @return
     * @see org.allen.cacheframework.OperationType
     */
    OperationType opsType();

    /**
     * 缓存的key
     *
     * @return
     */
    String key();

    /**
     * 方法的返回类型
     * 如果是集合类型，则为集合中对象的类型
     *
     * @return
     */
    Class returnType() default void.class;

    /**
     * 缓存的失效时间，单位: 秒
     * 默认失效时间为CacheAdvisor的defaultExpireSeconds
     *
     * @return
     * @see com.lufax.utility.cache.CacheAdvisor
     */
    int expireSeconds() default -1;

    /**
     * 当计算结果为null, 是否缓存一个空对象，以避免缓存穿透
     * 默认true
     *
     * @return
     */
    boolean putEmptyObjectWhenNull() default true;

    /**
     * 空对象的缓存失效时间, 单位: 秒
     * 默认失效时间为CacheAdvisor的defaultEmptyObjExpireSeconds
     *
     * @return
     * @see com.lufax.utility.cache.CacheAdvisor
     */
    int emptyObjExpireSeconds() default -1;

    /**
     * 是否在执行更新缓存前删除缓存
     * 默认true
     *
     * @return
     */
    boolean removeBeforeUpdate() default false;

}
