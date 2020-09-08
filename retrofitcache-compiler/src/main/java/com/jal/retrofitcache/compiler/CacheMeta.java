package com.jal.retrofitcache.compiler;

import com.jal.retrofitcache.annotation.Cache;


import javax.lang.model.element.ExecutableElement;

/**
 * It contains basic route information.
 *
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 16/8/24 09:45
 */
public class CacheMeta {
    public ExecutableElement methodElement;
    public Cache cache;

    public CacheMeta() {
    }

    public CacheMeta(ExecutableElement methodElement, Cache cache) {
        this.methodElement = methodElement;
        this.cache = cache;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CacheMeta meta = (CacheMeta) o;

        if (methodElement != null ? !methodElement.equals(meta.methodElement) : meta.methodElement != null)
            return false;
        return cache != null ? cache.equals(meta.cache) : meta.cache == null;
    }

    @Override
    public int hashCode() {
        int result = methodElement != null ? methodElement.hashCode() : 0;
        result = 31 * result + (cache != null ? cache.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CacheMeta{" +
                "methodElement=" + methodElement +
                ", cache=" + cache +
                '}';
    }
}