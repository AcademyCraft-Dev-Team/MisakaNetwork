package org.misaka.api.common.network.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将方法订阅 Packet 的注释喵
 * <br>
 * 不确保处理顺序喵
 * <br>
 * 该注解只能应用于单参数方法喵，其中单参数是 Packet 的子类喵
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubscribePacket {
}