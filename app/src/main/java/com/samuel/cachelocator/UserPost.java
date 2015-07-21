package com.samuel.cachelocator;

import com.parse.ParseClassName;
import com.parse.ParseGeoPoint;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

/**
 * Created by Samuel on 7/10/2015.
 */

@ParseClassName("Posts")
public class UserPost extends ParseObject {
    public String getText(){
        return getString("text");
    }

    public void setText(String value){
        put("text", value);
    }

    public ParseUser getUser(){
        return getParseUser("user");
    }

    public void setUser(ParseUser value){
        put("user", value);
    }

    public ParseGeoPoint getLocation(){
        return getParseGeoPoint("location");
    }

    public void setLocation(ParseGeoPoint value){
        put ("location", value);
    }

    public static ParseQuery<UserPost> getQuery(){
        return ParseQuery.getQuery(UserPost.class);
    }
}
