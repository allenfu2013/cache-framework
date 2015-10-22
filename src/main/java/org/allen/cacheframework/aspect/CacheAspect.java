package org.allen.cacheframework.aspect;

import com.google.gson.Gson;
import org.allen.cacheframework.CacheEnableException;
import org.allen.cacheframework.CacheService;
import org.allen.cacheframework.OperationType;
import org.allen.cacheframework.annotation.CacheEnable;
import org.allen.cacheframework.annotation.KeyParam;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Administrator on 2015/10/22.
 */
@Aspect
public class CacheAspect {

    @Autowired
    private CacheService cacheService;

    private int expireSeconds = 1800;
    private int emptyObjExpireSeconds = 300;

    private static final String EMPTY_KEY = ":EMPTY_KEY";
    private static final String EMPTY_OBJ = "EMPTY_OBJ";

    /**
     * match public method, all return types, all methods and all classes
     *
     * joincuts expression: http://docs.spring.io/spring/docs/3.0.x/spring-framework-reference/html/aop.html#aop-pointcuts
     *
     * @param joinPoint
     * @param cacheEnable
     * @return
     * @throws Throwable
     *
     * @see org.allen.cacheframework.annotation.CacheEnable
     */
    @Around(value = "execution(public * *(..)) && @annotation(cacheEnable)")
    public Object cachedAround(ProceedingJoinPoint joinPoint, CacheEnable cacheEnable) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();
        OperationType opsType = cacheEnable.opsType();
        String key = cacheEnable.key();
        int expireSecondsTmp = cacheEnable.expireSeconds();
        Class returnType = cacheEnable.returnType();
        boolean putEmptyObjectWhenNull = cacheEnable.putEmptyObjectWhenNull();
        int emptyObjExpireSecondsTmp = cacheEnable.emptyObjExpireSeconds();
        boolean removeBeforeUpdate = cacheEnable.removeBeforeUpdate();
//        Logger.info(this, String.format("cachedAround with class: %s, method: %s", joinPoint.getTarget().getClass().getName(), method.getName()));

        try {
            //使用Spring Expression Language处理动态key
            LocalVariableTableParameterNameDiscoverer parameterNameDiscoverer = new LocalVariableTableParameterNameDiscoverer();
            String[] paraNameArr = parameterNameDiscoverer.getParameterNames(method);
            ExpressionParser parser = new SpelExpressionParser();
            StandardEvaluationContext context = new StandardEvaluationContext();
            for (int i=0; i<paraNameArr.length; i++) {
                context.setVariable(paraNameArr[i], args[i]);
            }

            Annotation[][] annotations = method.getParameterAnnotations();
            List<String> keys = null;
            int multiKeyCount = 0;
            int multiKeyIndex = -1;
            for (int i = 0; i < annotations.length; i++) {
                for (Annotation annotation : annotations[i]) {
                    if (annotation instanceof KeyParam){
                        List<String> keyParams = getKeyParam(paraNameArr[i], (KeyParam)annotation, parser, context);
                        boolean multiKey = ((KeyParam) annotation).multiKey();
                        if (multiKey) {
                            multiKeyIndex = i;
                            multiKeyCount++;
                            if (multiKeyCount > 1) throw new CacheEnableException("multiKey count cannot be bigger than 1.");
                            for (int j = 0; j < keyParams.size(); j++) {
                                keyParams.set(j, key + keyParams.get(j));
                            }
                            keys = keyParams;
                        } else {
                            if (keys != null) {
                                for (int j = 0; j < keys.size(); j++) {
                                    keys.set(j, keys.get(j) + keyParams.get(0));
                                }
                            } else {
                                key += keyParams.get(0);
                            }
                        }
                    }
                }
            }

            if (StringUtils.isEmpty(key)) throw new CacheEnableException("cache key can not be empty.");
            if (keys == null) {
                keys = new ArrayList<String>();
                keys.add(key);
            }

            //TODO multiKey
            if (OperationType.GET == opsType) {
                return doGet(joinPoint, method, returnType, key, expireSecondsTmp, putEmptyObjectWhenNull, emptyObjExpireSecondsTmp);
            } else if (OperationType.PUT == opsType) {
                return doPut(joinPoint, key, expireSecondsTmp, removeBeforeUpdate, putEmptyObjectWhenNull, emptyObjExpireSecondsTmp);
            } else if (OperationType.REMOVE == opsType) {
                return doRemove(joinPoint, keys);
            }
        } catch (Throwable throwable) {
            // any exception in AOP process, call original method instead
//            Logger.error(this, String.format("cache aop process fail, error: %s", throwable.getMessage()), throwable);
            return joinPoint.proceed();
        }
        return null;
    }

    private Object doGet(ProceedingJoinPoint joinPoint, Method method, Class returnType, String key, int expireSecondsTmp, boolean putEmptyObjectWhenNull,int emptyObjExpireSecondsTmp) throws Throwable {
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = method.getName();


        if (returnType == void.class)
            throw new CacheEnableException(String.format("returnType can not be void for OperationType.GET, class: %s, method: %s", className, methodName));

        String cacheString = cacheService.get(key);
        if (cacheString != null) {
//            Logger.info(this, String.format("cache hits for class: %s, method: %s, key: %s, value: %s", className, methodName, key, cacheString));
            boolean isReturnAList = method.getReturnType().isAssignableFrom(List.class);
            if (!isReturnAList) {
                return new Gson().fromJson(cacheString, returnType);
            } else {
                return new Gson().fromJson(cacheString, listType(returnType));
            }
        } else {
//            Logger.info(this, String.format("cache misses for class: %s, method: %s, key: %s", className, methodName, key));
            Object obj = null;
            if (putEmptyObjectWhenNull) {
                String emptyKey = key + EMPTY_KEY;
                String emptyObjCache = cacheService.get(emptyKey);
                if (emptyObjCache == null) {
                    obj = joinPoint.proceed();
                    if (obj != null) {
                        cacheService.put(key, new Gson().toJson(obj), expireSecondsTmp == -1 ? expireSeconds : expireSecondsTmp);
                    } else {
                        cacheService.put(emptyKey, EMPTY_OBJ, emptyObjExpireSecondsTmp == -1 ? emptyObjExpireSeconds : emptyObjExpireSecondsTmp);
                    }
                }
            } else {
                obj = joinPoint.proceed();
                if (obj != null) {
                    cacheService.put(key, new Gson().toJson(obj), expireSecondsTmp == -1 ? expireSeconds : expireSecondsTmp);
                }
            }
            return obj;
        }
    }


    private Object doMultiGet(ProceedingJoinPoint joinPoint, Method method, Class returnType, List<String> keys, int multiKeyIndex, int expireSecondsTmp, boolean putEmptyObjectWhenNull,int emptyObjExpireSecondsTmp) throws Throwable {
        List<String> caches = cacheService.mget(keys);
        List<Integer> missIndexs = new ArrayList<Integer>();
        List<String> missKeys = new ArrayList<String>();
        for (int i = 0; i < caches.size(); i++) {
            if (caches.get(i) == null) {
                missIndexs.add(i);
                missKeys.add(keys.get(i));
            }
        }

        //TODO emptyObj check

        if (!missIndexs.isEmpty()) {
            Object[] args = joinPoint.getArgs();
            Object[] multiKeyArgs = (Object[]) args[multiKeyIndex];
            List<Object> missArgs = new ArrayList<Object>();
            for (int j = 0; j < missIndexs.size(); j++) {
                missArgs.add(multiKeyArgs[missIndexs.get(j)]);
            }
            args[multiKeyIndex] = missArgs;

            List values = (List) joinPoint.proceed(args);
            Map<String, String> keyValus = new HashMap<String, String>();
            for (int k = 0; k < missKeys.size(); k++) {
                Object value = values.get(k);
                if (value == null) {

                } else {
                    keyValus.put(missKeys.get(k), new Gson().toJson(value));
                }

            }
            cacheService.mput(expireSecondsTmp == -1 ? expireSeconds : expireSecondsTmp, keyValus);
        }
        return null;
    }

    private Object doRemove(ProceedingJoinPoint joinPoint, List<String> keys) throws Throwable {
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
//        Logger.info(this, String.format("remove from cache, class: %s, method: %s, keys: %s", className, methodName, keys));
        for (String key : keys) {
            cacheService.remove(key);
        }
        return joinPoint.proceed();
    }

    private Object doPut(ProceedingJoinPoint joinPoint, String key, int expireSecondsTmp, boolean removeBeforeUpdate, boolean putEmptyObjectWhenNull,int emptyObjExpireSecondsTmp) throws Throwable {
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
//        Logger.info(this, String.format("update cache, class: %s, method: %s, key: %s, expireSeconds: %s", className, methodName, key, expireSeconds));
        if (removeBeforeUpdate) {
            cacheService.remove(key);
        }

        Object obj = joinPoint.proceed();
        if (obj != null) {
            cacheService.put(key, new Gson().toJson(obj), expireSecondsTmp == -1 ? expireSeconds : expireSecondsTmp);
        } else {
            if (putEmptyObjectWhenNull) {
                cacheService.put(key + EMPTY_KEY, EMPTY_OBJ, emptyObjExpireSecondsTmp == -1 ? emptyObjExpireSeconds : emptyObjExpireSecondsTmp);
            }
        }
        return obj;
    }

    private List<String> getKeyParam(String paramName, KeyParam keyParam, ExpressionParser parser, StandardEvaluationContext context) {
        String param = keyParam.param();
        String connector = keyParam.connector();
        if (StringUtils.isEmpty(param)){
            param = "#" + paramName;
        }

        List keys = parser.parseExpression(param).getValue(context, List.class);
        if (!StringUtils.isEmpty(connector)) {
            for (int i = 0; i < keys.size(); i++) {
                keys.set(i, connector + String.valueOf(keys.get(i)));
            }
        }
        return keys;
    }

    private <T> Type listType(Class<T> clazz) {
        return new TypeToken<List<T>>() {}
                .where(new TypeParameter<T>() {
                }, clazz).getType();
    }

    public int getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(int expireSeconds) {
        this.expireSeconds = expireSeconds;
    }

    public int getEmptyObjExpireSeconds() {
        return emptyObjExpireSeconds;
    }

    public void setEmptyObjExpireSeconds(int emptyObjExpireSeconds) {
        this.emptyObjExpireSeconds = emptyObjExpireSeconds;
    }
}
