package com.jal.retrofitcache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author aljiang
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Cache {

}