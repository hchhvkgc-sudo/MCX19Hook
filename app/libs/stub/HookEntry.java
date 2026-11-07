package org.lsposed.lspd;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HookEntry {
    String packageName();
}
