package com.jal.retrofitcach.api;


/**
 * @author aljiang
 * @date 2020/8/18
 * @desc cache handler
 */
public interface ICacheHandler {
    /**
     * get cache date
     *
     * @return date
     */
    String getCache(String methodName, Object... obj);

    /**
     * save cache data
     */
    void saveCache(Object data, String methodName, Object... obj);
}
