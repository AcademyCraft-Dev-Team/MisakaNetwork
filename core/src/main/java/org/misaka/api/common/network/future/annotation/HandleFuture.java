package org.misaka.api.common.network.future.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将方法标记为 Future 处理注解喵
 * <br>
 * 该注解只能标记在有一个参数并且有返回值的方法上喵, 参数类型要是 RequestPacket 的子类, 返回值要是 ResponsePacket 的子类喵
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HandleFuture {
}