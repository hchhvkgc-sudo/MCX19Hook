package org.lsposed.lspd;

public class HookParam {
    private final Object applicationContext;
    private final ClassLoader classLoader;

    public HookParam(Object applicationContext, ClassLoader classLoader) {
        this.applicationContext = applicationContext;
        this.classLoader = classLoader;
    }

    public Object getApplicationContext() {
        return applicationContext;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }
}
