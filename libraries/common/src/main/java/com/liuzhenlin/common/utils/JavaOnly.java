/*
 * Created on 2025-1-6 5:27:05 AM.
 * Copyright © 2025 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies that this class, function, field, etc. is only designed for Java. Other Interoperable
 * programming language like Kotlin should not access.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface JavaOnly {
}
