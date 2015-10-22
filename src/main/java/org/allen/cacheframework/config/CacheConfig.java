package org.allen.cacheframework.config;

/**
 * Created by Administrator on 2015/10/20.
 */
public class CacheConfig {
    private int expireSeconds;
    private int emptyObjExpireSeconds;

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
