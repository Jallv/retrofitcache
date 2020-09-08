package com.jal.retrofitcache;

import com.jal.retrofitcache.annotation.Cache;
import com.jal.retrofitcache.annotation.CacheApi;

import io.reactivex.Observable;
import retrofit2.http.GET;
import retrofit2.http.Query;

@CacheApi
public interface MyApi {

    @Cache
    @GET("page/info")
    Observable<PageInfo> getPageInfo(@Query("id") String id);


    @GET("page2/info")
    Observable<PageInfo> getPage2Info(@Query("id") String id);
}