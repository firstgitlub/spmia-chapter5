package com.thoughtmechanix.licenses.utils;


import org.springframework.util.Assert;

public class UserContextHolder {
    // userContext 存储在一个静态的ThreadLocal 变量中
    private static final ThreadLocal<UserContext> userContext = new ThreadLocal<UserContext>();

    // getContext方法 将检索UserContext以供使用
    public static final UserContext getContext(){
        UserContext context = userContext.get();

        if (context == null) {
            context = createEmptyContext();
            userContext.set(context);

        }
        return userContext.get();
    }

    public static final void setContext(UserContext context) {
        Assert.notNull(context, "Only non-null UserContext instances are permitted");
        userContext.set(context);
    }

    public static final UserContext createEmptyContext(){
        return new UserContext();
    }
}
