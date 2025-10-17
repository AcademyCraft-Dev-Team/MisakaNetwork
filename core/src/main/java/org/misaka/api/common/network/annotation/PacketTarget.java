package org.misaka.api.common.network.annotation;

import org.misaka.api.common.network.ThreadType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 如果不添加注释则默认不检查当前线程喵, 如 FutureRequestPacket 与 FutureResponsePacket 喵
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PacketTarget {
    ThreadType value();
}