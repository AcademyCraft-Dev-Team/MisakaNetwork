package org.misaka.processor.spec;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

public record HandlerInfo(
        String generatedClassName,
        HandlerType handlerType,
        Scope scope,
        TypeElement sourceClass,
        ExecutableElement sourceMethod
) {
    public enum HandlerType {
        LISTENER,
        INVOKER
    }

    public enum Scope {
        STATIC,
        INSTANCE
    }
}