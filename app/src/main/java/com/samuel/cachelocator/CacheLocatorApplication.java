package com.samuel.cachelocator;

import android.app.Application;

import com.parse.Parse;
import com.parse.ParseObject;


public class CacheLocatorApplication extends Application {

    @Override
    public void onCreate(){
        super.onCreate();

        ParseObject.registerSubclass(UserPost.class);
        Parse.enableLocalDatastore(this);
        Parse.initialize(this, "ihzeSfWy6g9ZeyDdWFjIo1m7YzCwMhO2MQZR4zzt", "dHhoFhi1dVMhhPudjn6GY6vEaoF8Y1ueq0yAWTb5");
    }
}
