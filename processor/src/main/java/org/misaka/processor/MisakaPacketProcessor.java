package org.misaka.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import org.misaka.processor.spec.HandlerInfo;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

@AutoService(Processor.class)
@SupportedAnnotationTypes({
        "org.misaka.api.common.network.annotation.SubscribePacket",
        "org.misaka.api.common.network.future.annotation.HandleFuture"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class MisakaPacketProcessor extends AbstractProcessor {
    private Filer filer;
    private Messager messager;
    private Elements elementUtils;
    private Types typeUtils;
    private String customProviderFqcn;

    private final Map<TypeElement, List<HandlerInfo>> handlerInfoMap = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();

        customProviderFqcn = processingEnv.getOptions().get("misaka.provider.fqcn");
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(
                "misaka.project.id",
                "misaka.provider.fqcn"
        );
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            messager.printMessage(Diagnostic.Kind.NOTE, "MisakaProcessor: Running processing round.");
        }

        try {
            processSubscribePacket(roundEnv);
            processHandleFuture(roundEnv);
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Unhandled error in MisakaPacketProcessor: " + e);
            return false;
        }

        if (roundEnv.processingOver()) {
            if (!handlerInfoMap.isEmpty()) {
                messager.printMessage(Diagnostic.Kind.NOTE, "MisakaProcessor: Final round, generating provider...");
                try {
                    generateProviderImplementation();
                } catch (IOException e) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate MisakaHandlersProvider: " + e);
                }
            } else {
                messager.printMessage(Diagnostic.Kind.NOTE, "MisakaProcessor: Final round, but no handlers were collected.");
            }
        }

        return true;
    }

    private void processSubscribePacket(RoundEnvironment roundEnv) throws IOException {
        var annotationElement = elementUtils.getTypeElement("org.misaka.api.common.network.annotation.SubscribePacket");
        if (annotationElement == null) return;
        var packetElement = elementUtils.getTypeElement("org.misaka.api.common.network.packet.Packet");
        if (packetElement == null) return;

        for (var element : roundEnv.getElementsAnnotatedWith(annotationElement)) {
            if (validateSubscribePacketMethod(element, packetElement.asType())) {
                generateListenerClass((ExecutableElement) element);
            }
        }
    }

    private boolean validateSubscribePacketMethod(Element element, TypeMirror packetType) {
        if (element.getKind() != ElementKind.METHOD) {
            messager.printMessage(Diagnostic.Kind.ERROR, "@SubscribePacket can only be applied to methods.", element);
            return false;
        }
        var method = (ExecutableElement) element;
        if (!method.getModifiers().contains(Modifier.PUBLIC)) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Method annotated with @SubscribePacket must be public.", method);
            return false;
        }
        if (method.getParameters().size() != 1) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Method must have exactly one parameter.", method);
            return false;
        }
        var parameter = method.getParameters().getFirst();
        if (!typeUtils.isSubtype(
                typeUtils.erasure(parameter.asType()),
                typeUtils.erasure(packetType)
        )) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Parameter must be a subtype of Packet.", parameter);
            return false;
        }
        return true;
    }

    private void generateListenerClass(ExecutableElement method) throws IOException {
        var isStatic = method.getModifiers().contains(Modifier.STATIC);
        var enclosingClass = (TypeElement) method.getEnclosingElement();
        var parameter = method.getParameters().getFirst();
        var packageName = elementUtils.getPackageOf(enclosingClass).getQualifiedName().toString();
        var generatedSimpleClassName = buildGeneratedClassName(enclosingClass, method, parameter, "_MNListener");
        var generatedClassName = ClassName.get(packageName, generatedSimpleClassName);

        var superclass = ClassName.get("org.misaka.api.common.network.listener", isStatic ? "StaticPacketListener" : "InstancePacketListener");
        var packetSuperclassName = ClassName.get("org.misaka.api.common.network.packet", "Packet");
        var enclosingClassName = TypeName.get(enclosingClass.asType());
        var packetClassName = TypeName.get(parameter.asType());

        var rawPacketClassName = packetClassName;
        if (rawPacketClassName instanceof ParameterizedTypeName parameterizedTypeName) {
            rawPacketClassName = parameterizedTypeName.rawType;
        }

        var getPacketClassMethod = MethodSpec.methodBuilder("getPacketClass")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(packetSuperclassName)))
                .addStatement("return $T.class", rawPacketClassName)
                .build();

        var handlePacketMethod = MethodSpec.methodBuilder("handlePacket")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(packetSuperclassName, "packet");

        if (isStatic) {
            handlePacketMethod.addStatement("$T.$L(($T) packet)", enclosingClassName, method.getSimpleName(), packetClassName);
        } else {
            handlePacketMethod.addStatement("(($T) this.instance).$L(($T) packet)", enclosingClassName, method.getSimpleName(), packetClassName);
        }

        var classBuilder = TypeSpec.classBuilder(generatedClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(superclass)
                .addMethod(getPacketClassMethod)
                .addMethod(handlePacketMethod.build());

        if (!isStatic) {
            classBuilder.addMethod(MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(Object.class, "instance")
                    .addStatement("super(instance)")
                    .build());
        }

        JavaFile.builder(packageName, classBuilder.build()).indent("    ").build().writeTo(filer);

        var scope = isStatic ? HandlerInfo.Scope.STATIC : HandlerInfo.Scope.INSTANCE;
        var info = new HandlerInfo(generatedClassName.canonicalName(), HandlerInfo.HandlerType.LISTENER, scope, enclosingClass, method);
        handlerInfoMap.computeIfAbsent(enclosingClass, k -> new ArrayList<>()).add(info);
    }

    private void processHandleFuture(RoundEnvironment roundEnv) throws IOException {
        var annotationElement = elementUtils.getTypeElement("org.misaka.api.common.network.future.annotation.HandleFuture");
        if (annotationElement == null) return;
        var requestElement = elementUtils.getTypeElement("org.misaka.api.common.network.future.packet.RequestPacket");
        var responseElement = elementUtils.getTypeElement("org.misaka.api.common.network.future.packet.ResponsePacket");
        if (requestElement == null || responseElement == null) return;

        for (var element : roundEnv.getElementsAnnotatedWith(annotationElement)) {
            if (validateHandleFutureMethod(element, requestElement.asType(), responseElement.asType())) {
                generateInvokerClass((ExecutableElement) element);
            }
        }
    }

    private boolean validateHandleFutureMethod(Element element, TypeMirror requestType, TypeMirror responseType) {
        if (element.getKind() != ElementKind.METHOD) {
            messager.printMessage(Diagnostic.Kind.ERROR, "@HandleFuture can only be applied to methods.", element);
            return false;
        }
        var method = (ExecutableElement) element;
        if (!method.getModifiers().contains(Modifier.PUBLIC)) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Method annotated with @HandleFuture must be public.", method);
            return false;
        }
        if (method.getParameters().size() != 1) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Method must have exactly one parameter.", method);
            return false;
        }
        var parameter = method.getParameters().getFirst();
        if (!typeUtils.isSubtype(typeUtils.erasure(parameter.asType()), typeUtils.erasure(requestType))) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Parameter must be a subtype of RequestPacket.", parameter);
            return false;
        }
        if (typeUtils.isSameType(method.getReturnType(), typeUtils.getNoType(TypeKind.VOID))) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Method must have a return value.", method);
            return false;
        }
        if (!typeUtils.isSubtype(typeUtils.erasure(method.getReturnType()), typeUtils.erasure(responseType))) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Return type must be a subtype of ResponsePacket.", method);
            return false;
        }
        return true;
    }

    private void generateInvokerClass(ExecutableElement method) throws IOException {
        var isStatic = method.getModifiers().contains(Modifier.STATIC);
        var enclosingClass = (TypeElement) method.getEnclosingElement();
        var parameter = method.getParameters().getFirst();
        var packageName = elementUtils.getPackageOf(enclosingClass).getQualifiedName().toString();
        var generatedSimpleClassName = buildGeneratedClassName(enclosingClass, method, parameter, "_MNInvoker");
        var generatedClassName = ClassName.get(packageName, generatedSimpleClassName);

        var superclass = ClassName.get("org.misaka.api.common.network.future.invoker", isStatic ? "StaticFutureHandlerInvoker" : "InstanceFutureHandlerInvoker");
        var requestPacketName = ClassName.get("org.misaka.api.common.network.future.packet", "RequestPacket");
        var responsePacketName = ClassName.get("org.misaka.api.common.network.future.packet", "ResponsePacket");
        var enclosingClassName = TypeName.get(enclosingClass.asType());
        var requestClassName = TypeName.get(parameter.asType());

        var getRequestPacketClassMethod = MethodSpec.methodBuilder("getRequestPacketClass")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(requestPacketName)))
                .addStatement("return $T.class", requestClassName)
                .build();

        var invokeMethod = MethodSpec.methodBuilder("invoke")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(requestPacketName, "requestPacket")
                .returns(responsePacketName);

        if (isStatic) {
            invokeMethod.addStatement("return $T.$L(($T) requestPacket)", enclosingClassName, method.getSimpleName(), requestClassName);
        } else {
            invokeMethod.addStatement("return (($T) this.instance).$L(($T) requestPacket)", enclosingClassName, method.getSimpleName(), requestClassName);
        }

        var classBuilder = TypeSpec.classBuilder(generatedClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(superclass)
                .addMethod(getRequestPacketClassMethod)
                .addMethod(invokeMethod.build());

        if (!isStatic) {
            classBuilder.addMethod(MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(Object.class, "newInstance")
                    .addStatement("super(newInstance)")
                    .build());
        }

        JavaFile.builder(packageName, classBuilder.build()).indent("    ").build().writeTo(filer);

        var scope = isStatic ? HandlerInfo.Scope.STATIC : HandlerInfo.Scope.INSTANCE;
        var info = new HandlerInfo(generatedClassName.canonicalName(), HandlerInfo.HandlerType.INVOKER, scope, enclosingClass, method);
        handlerInfoMap.computeIfAbsent(enclosingClass, k -> new ArrayList<>()).add(info);
    }

    private void generateProviderImplementation() throws IOException {
        var providerInterface = ClassName.get("org.misaka.internal", "MisakaHandlersProvider");

        String providerPackageName;
        String simpleClassName;

        if (customProviderFqcn != null && !customProviderFqcn.isEmpty()) {
            var lastDot = customProviderFqcn.lastIndexOf('.');
            if (lastDot > 0) {
                providerPackageName = customProviderFqcn.substring(0, lastDot);
                simpleClassName = customProviderFqcn.substring(lastDot + 1);
            } else {
                providerPackageName = "";
                simpleClassName = customProviderFqcn;
            }
        } else {
            var randomId = "Gen" + Long.toHexString(System.currentTimeMillis());
            providerPackageName = randomId + ".misaka.generated";
            simpleClassName = randomId + "_MisakaHandlersProviderImpl";
        }

        var generatedProviderClassName = ClassName.get(providerPackageName, simpleClassName);

        var ipacketListener = ClassName.get("org.misaka.api.common.network.listener", "IPacketListener");
        var ifutureInvoker = ClassName.get("org.misaka.api.common.network.future.invoker", "IFutureHandlerInvoker");
        var classWildcard = ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(TypeName.OBJECT));

        var staticListenersMapType = ParameterizedTypeName.get(ClassName.get(Map.class), classWildcard, ParameterizedTypeName.get(ClassName.get(List.class), ipacketListener));
        var instanceListenerFactoriesMapType = ParameterizedTypeName.get(ClassName.get(Map.class), classWildcard, ParameterizedTypeName.get(ClassName.get(List.class), ParameterizedTypeName.get(ClassName.get(Function.class), TypeName.OBJECT, ipacketListener)));
        var staticInvokersMapType = ParameterizedTypeName.get(ClassName.get(Map.class), classWildcard, ParameterizedTypeName.get(ClassName.get(List.class), ifutureInvoker));
        var instanceInvokerFactoriesMapType = ParameterizedTypeName.get(ClassName.get(Map.class), classWildcard, ParameterizedTypeName.get(ClassName.get(List.class), ParameterizedTypeName.get(ClassName.get(Function.class), TypeName.OBJECT, ifutureInvoker)));
        var staticListenersField = FieldSpec.builder(staticListenersMapType, "STATIC_LISTENERS", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).build();
        var instanceListenerFactoriesField = FieldSpec.builder(instanceListenerFactoriesMapType, "INSTANCE_LISTENER_FACTORIES", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).build();
        var staticInvokersField = FieldSpec.builder(staticInvokersMapType, "STATIC_INVOKERS", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).build();
        var instanceInvokerFactoriesField = FieldSpec.builder(instanceInvokerFactoriesMapType, "INSTANCE_INVOKER_FACTORIES", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).build();
        var staticInitializer = CodeBlock.builder()
                .addStatement("var staticListeners = new $T()", ParameterizedTypeName.get(ClassName.get(HashMap.class), classWildcard, ParameterizedTypeName.get(ClassName.get(List.class), ipacketListener)))
                .addStatement("var instanceListenerFactories = new $T()", ParameterizedTypeName.get(ClassName.get(HashMap.class), classWildcard, ParameterizedTypeName.get(ClassName.get(List.class), ParameterizedTypeName.get(ClassName.get(Function.class), TypeName.OBJECT, ipacketListener))))
                .addStatement("var staticInvokers = new $T()", ParameterizedTypeName.get(ClassName.get(HashMap.class), classWildcard, ParameterizedTypeName.get(ClassName.get(List.class), ifutureInvoker)))
                .addStatement("var instanceInvokerFactories = new $T()", ParameterizedTypeName.get(ClassName.get(HashMap.class), classWildcard, ParameterizedTypeName.get(ClassName.get(List.class), ParameterizedTypeName.get(ClassName.get(Function.class), TypeName.OBJECT, ifutureInvoker))));
        handlerInfoMap.forEach((sourceClass, infos) -> {
            var staticListeners = infos.stream().filter(i -> i.scope() == HandlerInfo.Scope.STATIC && i.handlerType() == HandlerInfo.HandlerType.LISTENER).toList();
            var instanceListeners = infos.stream().filter(i -> i.scope() == HandlerInfo.Scope.INSTANCE && i.handlerType() == HandlerInfo.HandlerType.LISTENER).toList();
            var staticInvokers = infos.stream().filter(i -> i.scope() == HandlerInfo.Scope.STATIC && i.handlerType() == HandlerInfo.HandlerType.INVOKER).toList();
            var instanceInvokers = infos.stream().filter(i -> i.scope() == HandlerInfo.Scope.INSTANCE && i.handlerType() == HandlerInfo.HandlerType.INVOKER).toList();

            if (!staticListeners.isEmpty()) {
                var newInstances = staticListeners.stream().map(info -> CodeBlock.of("new $L()", info.generatedClassName())).collect(CodeBlock.joining(", "));
                staticInitializer.addStatement("staticListeners.put($T.class, $T.of($L))", sourceClass.asType(), List.class, newInstances);
            }
            if (!instanceListeners.isEmpty()) {
                var lambdas = instanceListeners.stream().map(info -> CodeBlock.of("instance -> new $L(instance)", info.generatedClassName())).collect(CodeBlock.joining(", "));
                staticInitializer.addStatement("instanceListenerFactories.put($T.class, $T.of($L))", sourceClass.asType(), List.class, lambdas);
            }
            if (!staticInvokers.isEmpty()) {
                var newInstances = staticInvokers.stream().map(info -> CodeBlock.of("new $L()", info.generatedClassName())).collect(CodeBlock.joining(", "));
                staticInitializer.addStatement("staticInvokers.put($T.class, $T.of($L))", sourceClass.asType(), List.class, newInstances);
            }
            if (!instanceInvokers.isEmpty()) {
                var lambdas = instanceInvokers.stream().map(info -> CodeBlock.of("instance -> new $L(instance)", info.generatedClassName())).collect(CodeBlock.joining(", "));
                staticInitializer.addStatement("instanceInvokerFactories.put($T.class, $T.of($L))", sourceClass.asType(), List.class, lambdas);
            }
        });
        staticInitializer
                .addStatement("$N = $T.unmodifiableMap(staticListeners)", staticListenersField, Collections.class)
                .addStatement("$N = $T.unmodifiableMap(instanceListenerFactories)", instanceListenerFactoriesField, Collections.class)
                .addStatement("$N = $T.unmodifiableMap(staticInvokers)", staticInvokersField, Collections.class)
                .addStatement("$N = $T.unmodifiableMap(instanceInvokerFactories)", instanceInvokerFactoriesField, Collections.class);

        var getStaticListeners = MethodSpec.methodBuilder("getStaticListeners").addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(staticListenersMapType).addStatement("return $N", staticListenersField).build();
        var getInstanceFactories = MethodSpec.methodBuilder("getInstanceListenerFactories").addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(instanceListenerFactoriesMapType).addStatement("return $N", instanceListenerFactoriesField).build();
        var getStaticInvokers = MethodSpec.methodBuilder("getStaticInvokers").addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(staticInvokersMapType).addStatement("return $N", staticInvokersField).build();
        var getInstanceInvokerFactories = MethodSpec.methodBuilder("getInstanceInvokerFactories").addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(instanceInvokerFactoriesMapType).addStatement("return $N", instanceInvokerFactoriesField).build();

        var providerClass = TypeSpec.classBuilder(generatedProviderClassName).addModifiers(Modifier.PUBLIC, Modifier.FINAL).addSuperinterface(providerInterface).addField(staticListenersField).addField(instanceListenerFactoriesField).addField(staticInvokersField).addField(instanceInvokerFactoriesField).addStaticBlock(staticInitializer.build()).addMethod(getStaticListeners).addMethod(getInstanceFactories).addMethod(getStaticInvokers).addMethod(getInstanceInvokerFactories).build();
        JavaFile.builder(providerPackageName, providerClass).indent("    ").build().writeTo(filer);
        var serviceFile = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + providerInterface.canonicalName());
        try (var writer = serviceFile.openWriter()) {
            writer.write(generatedProviderClassName.canonicalName());
        }
    }

    private String buildGeneratedClassName(TypeElement enclosingClass,
                                           ExecutableElement method,
                                           VariableElement parameter,
                                           String suffix) {
        var className = new StringBuilder();
        var classStack = new ArrayDeque<Element>();
        var current = (Element) enclosingClass;

        while (current.getKind().isClass() || current.getKind().isInterface()) {
            classStack.addFirst(current);
            current = current.getEnclosingElement();
        }

        while (!classStack.isEmpty()) {
            className.append(classStack.pollFirst().getSimpleName());
            if (!classStack.isEmpty()) {
                className.append("_");
            }
        }

        var paramTypeElement = (TypeElement) typeUtils.asElement(parameter.asType());
        var paramSimpleName = paramTypeElement.getSimpleName().toString();
        var paramQualifiedName = paramTypeElement.getQualifiedName().toString();

        var hash = paramQualifiedName.hashCode();
        if (hash == Integer.MIN_VALUE) hash = 0;
        hash = Math.abs(hash);
        var shortHash = Integer.toHexString(hash);
        if (shortHash.length() > 6) {
            shortHash = shortHash.substring(0, 6);
        }

        className.append("_")
                .append(method.getSimpleName())
                .append("_")
                .append(paramSimpleName)
                .append("_")
                .append(shortHash)
                .append(suffix);

        return className.toString();
    }
}