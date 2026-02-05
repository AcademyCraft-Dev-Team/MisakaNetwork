package org.misaka.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;

@AutoService(Processor.class)
@SupportedAnnotationTypes({
        "org.misaka.api.common.network.annotation.SubscribePacket",
        "org.misaka.api.common.network.future.annotation.HandleFuture"
})
public final class MisakaPacketProcessor extends AbstractProcessor {
    private final Set<String> processedSourceClassNames = new HashSet<>();
    private Filer filer;
    private Messager messager;
    private Elements elementUtils;
    private Types typeUtils;

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        Map<TypeElement, List<Element>> classToMethods = new HashMap<>();

        TypeElement packetAnnotation = elementUtils.getTypeElement("org.misaka.api.common.network.annotation.SubscribePacket");
        if (packetAnnotation != null) {
            for (Element element : roundEnv.getElementsAnnotatedWith(packetAnnotation)) {
                if (validateSubscribePacketMethod(element)) {
                    TypeElement enclosingClass = (TypeElement) element.getEnclosingElement();
                    classToMethods.computeIfAbsent(enclosingClass, k -> new ArrayList<>()).add(element);
                }
            }
        }

        TypeElement futureAnnotation = elementUtils.getTypeElement("org.misaka.api.common.network.future.annotation.HandleFuture");
        if (futureAnnotation != null) {
            for (Element element : roundEnv.getElementsAnnotatedWith(futureAnnotation)) {
                if (validateHandleFutureMethod(element)) {
                    TypeElement enclosingClass = (TypeElement) element.getEnclosingElement();
                    classToMethods.computeIfAbsent(enclosingClass, k -> new ArrayList<>()).add(element);
                }
            }
        }

        for (Map.Entry<TypeElement, List<Element>> entry : classToMethods.entrySet()) {
            TypeElement sourceClass = entry.getKey();
            List<Element> methods = entry.getValue();
            List<RegistrationInfo> registrations = new ArrayList<>();

            if (!processedSourceClassNames.add(sourceClass.getQualifiedName().toString())) {
                continue;
            }

            try {
                for (Element method : methods) {
                    if (hasAnnotation(method, packetAnnotation)) {
                        ClassName generatedClass = generateListenerClass((ExecutableElement) method);
                        boolean isStatic = method.getModifiers().contains(Modifier.STATIC);
                        registrations.add(new RegistrationInfo(generatedClass, RegistrationType.LISTENER, isStatic));
                    } else {
                        ClassName generatedClass = generateInvokerClass((ExecutableElement) method);
                        boolean isStatic = method.getModifiers().contains(Modifier.STATIC);
                        registrations.add(new RegistrationInfo(generatedClass, RegistrationType.INVOKER, isStatic));
                    }
                }
                generateModuleProvider(sourceClass, registrations);
            } catch (Exception e) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate code for " + sourceClass.getQualifiedName() + ": " + e, sourceClass);
                processedSourceClassNames.remove(sourceClass.getQualifiedName().toString());
            }
        }

        return true;
    }

    private void tryWriteFile(JavaFile javaFile) throws IOException {
        try {
            javaFile.writeTo(filer);
        } catch (FilerException e) {
            if (!e.getMessage().contains("Attempt to recreate a file")) {
                throw e;
            }
        }
    }

    private boolean hasAnnotation(Element element, TypeElement annotation) {
        if (annotation == null) return false;
        TypeMirror annotationType = annotation.asType();
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (typeUtils.isSameType(mirror.getAnnotationType(), annotationType)) {
                return true;
            }
        }
        return false;
    }

    private boolean validateSubscribePacketMethod(Element element) {
        if (element.getKind() != ElementKind.METHOD) {
            messager.printMessage(Diagnostic.Kind.ERROR, "@SubscribePacket can only be applied to methods.", element);
            return false;
        }
        ExecutableElement method = (ExecutableElement) element;
        if (!method.getModifiers().contains(Modifier.PUBLIC)) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Method annotated with @SubscribePacket must be public.", method);
            return false;
        }
        if (method.getParameters().size() != 1) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Method must have exactly one parameter.", method);
            return false;
        }
        return true;
    }

    private boolean validateHandleFutureMethod(Element element) {
        if (element.getKind() != ElementKind.METHOD) {
            messager.printMessage(Diagnostic.Kind.ERROR, "@HandleFuture can only be applied to methods.", element);
            return false;
        }
        ExecutableElement method = (ExecutableElement) element;
        if (!method.getModifiers().contains(Modifier.PUBLIC)) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Method annotated with @HandleFuture must be public.", method);
            return false;
        }
        if (method.getParameters().size() != 1) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Method must have exactly one parameter.", method);
            return false;
        }
        if (typeUtils.isSameType(method.getReturnType(), typeUtils.getNoType(TypeKind.VOID))) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Method must have a return value.", method);
            return false;
        }
        return true;
    }

    private ClassName generateListenerClass(ExecutableElement method) throws IOException {
        boolean isStatic = method.getModifiers().contains(Modifier.STATIC);
        TypeElement enclosingClass = (TypeElement) method.getEnclosingElement();
        VariableElement parameter = method.getParameters().getFirst();
        String packageName = elementUtils.getPackageOf(enclosingClass).getQualifiedName().toString();
        String generatedSimpleClassName = buildGeneratedClassName(enclosingClass, method, parameter, "_Listener");
        ClassName generatedClassName = ClassName.get(packageName, generatedSimpleClassName);
        ClassName superclass = ClassName.get("org.misaka.api.common.network.listener", isStatic ? "StaticPacketListener" : "InstancePacketListener");
        ClassName packetSuperclassName = ClassName.get("org.misaka.api.common.network.packet", "Packet");
        TypeName enclosingClassName = TypeName.get(enclosingClass.asType());
        TypeName packetClassName = TypeName.get(parameter.asType());
        TypeName rawPacketClassName = packetClassName instanceof ParameterizedTypeName ? ((ParameterizedTypeName) packetClassName).rawType : packetClassName;

        MethodSpec getPacketClassMethod = MethodSpec.methodBuilder("getPacketClass")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(packetSuperclassName)))
                .addStatement("return $T.class", rawPacketClassName)
                .build();

        MethodSpec.Builder handlePacketMethodBuilder = MethodSpec.methodBuilder("handlePacket")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(packetSuperclassName, "packet");

        if (isStatic) {
            handlePacketMethodBuilder.addStatement("$T.$L(($T) packet)", enclosingClassName, method.getSimpleName(), rawPacketClassName);
        } else {
            handlePacketMethodBuilder.addStatement("(($T) this.instance).$L(($T) packet)", enclosingClassName, method.getSimpleName(), rawPacketClassName);
        }

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(generatedClassName)
                .addOriginatingElement(enclosingClass)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(superclass)
                .addMethod(getPacketClassMethod)
                .addMethod(handlePacketMethodBuilder.build());

        if (!isStatic) {
            classBuilder.addMethod(MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(Object.class, "instance")
                    .addStatement("super(instance)")
                    .build());
        }

        tryWriteFile(JavaFile.builder(packageName, classBuilder.build()).indent("    ").build());
        return generatedClassName;
    }

    private ClassName generateInvokerClass(ExecutableElement method) throws IOException {
        boolean isStatic = method.getModifiers().contains(Modifier.STATIC);
        TypeElement enclosingClass = (TypeElement) method.getEnclosingElement();
        VariableElement parameter = method.getParameters().getFirst();
        String packageName = elementUtils.getPackageOf(enclosingClass).getQualifiedName().toString();
        String generatedSimpleClassName = buildGeneratedClassName(enclosingClass, method, parameter, "_Invoker");
        ClassName generatedClassName = ClassName.get(packageName, generatedSimpleClassName);
        ClassName superclass = ClassName.get("org.misaka.api.common.network.future.invoker", isStatic ? "StaticFutureHandlerInvoker" : "InstanceFutureHandlerInvoker");
        ClassName requestPacketName = ClassName.get("org.misaka.api.common.network.future.packet", "RequestPacket");
        ClassName responsePacketName = ClassName.get("org.misaka.api.common.network.future.packet", "ResponsePacket");
        TypeName enclosingClassName = TypeName.get(enclosingClass.asType());
        TypeName requestClassName = TypeName.get(parameter.asType());

        MethodSpec getRequestPacketClassMethod = MethodSpec.methodBuilder("getRequestPacketClass")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(requestPacketName)))
                .addStatement("return $T.class", requestClassName)
                .build();

        MethodSpec.Builder invokeMethodBuilder = MethodSpec.methodBuilder("invoke")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(requestPacketName, "requestPacket")
                .returns(responsePacketName);

        if (isStatic) {
            invokeMethodBuilder.addStatement("return $T.$L(($T) requestPacket)", enclosingClassName, method.getSimpleName(), requestClassName);
        } else {
            invokeMethodBuilder.addStatement("return (($T) this.instance).$L(($T) requestPacket)", enclosingClassName, method.getSimpleName(), requestClassName);
        }

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(generatedClassName)
                .addOriginatingElement(enclosingClass)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(superclass)
                .addMethod(getRequestPacketClassMethod)
                .addMethod(invokeMethodBuilder.build());

        if (!isStatic) {
            classBuilder.addMethod(MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(Object.class, "newInstance")
                    .addStatement("super(newInstance)")
                    .build());
        }

        tryWriteFile(JavaFile.builder(packageName, classBuilder.build()).indent("    ").build());
        return generatedClassName;
    }

    private void generateModuleProvider(TypeElement sourceClass, List<RegistrationInfo> registrations) throws IOException {
        String packageName = elementUtils.getPackageOf(sourceClass).getQualifiedName().toString();
        String simpleName = getUniqueClassName(sourceClass) + "_MisakaProvider";
        ClassName providerClassName = ClassName.get(packageName, simpleName);
        TypeName sourceClassName = TypeName.get(sourceClass.asType());
        ClassName serviceInterface = ClassName.get("org.misaka.internal", "MisakaHandlersProvider");
        ClassName autoService = ClassName.get("com.google.auto.service", "AutoService");

        MethodSpec.Builder registerMethod = MethodSpec.methodBuilder("register")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get("org.misaka.internal.MisakaHandlersProvider", "Registry"), "registry");

        for (int i = 0; i < registrations.size(); i++) {
            RegistrationInfo info = registrations.get(i);
            if (info.isStatic) {
                String varName = (info.type == RegistrationType.LISTENER ? "listener" : "invoker") + i;
                registerMethod.addStatement("var $L = new $T()", varName, info.className);
                if (info.type == RegistrationType.LISTENER) {
                    registerMethod.addStatement("registry.addStaticListener($T.class, $L)", sourceClassName, varName);
                } else {
                    registerMethod.addStatement("registry.addStaticInvoker($T.class, $L)", sourceClassName, varName);
                }
            } else {
                if (info.type == RegistrationType.LISTENER) {
                    registerMethod.addStatement("registry.addInstanceListenerFactory($T.class, obj -> new $T(($T) obj))", sourceClassName, info.className, sourceClassName);
                } else {
                    registerMethod.addStatement("registry.addInstanceInvokerFactory($T.class, obj -> new $T(($T) obj))", sourceClassName, info.className, sourceClassName);
                }
            }
        }

        TypeSpec providerClass = TypeSpec.classBuilder(providerClassName)
                .addOriginatingElement(sourceClass)
                .addAnnotation(AnnotationSpec.builder(autoService)
                        .addMember("value", "$T.class", serviceInterface)
                        .build())
                .addSuperinterface(serviceInterface)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(registerMethod.build())
                .build();

        tryWriteFile(JavaFile.builder(packageName, providerClass).indent("    ").build());
    }

    private String getUniqueClassName(TypeElement element) {
        Deque<String> nameParts = new ArrayDeque<>();
        Element current = element;
        while (current.getKind().isClass() || current.getKind().isInterface()) {
            nameParts.addFirst(current.getSimpleName().toString());
            current = current.getEnclosingElement();
        }
        return String.join("_", nameParts);
    }

    private String buildGeneratedClassName(TypeElement enclosingClass, ExecutableElement method, VariableElement parameter, String suffix) {
        String baseName = getUniqueClassName(enclosingClass);
        String paramFqcn = parameter.asType().toString();
        int genericStartIndex = paramFqcn.indexOf('<');
        if (genericStartIndex != -1) {
            paramFqcn = paramFqcn.substring(0, genericStartIndex);
        }
        String paramSimpleName = paramFqcn.substring(paramFqcn.lastIndexOf('.') + 1);
        return baseName + "_" + method.getSimpleName() + "_" + paramSimpleName + suffix;
    }

    private enum RegistrationType {
        LISTENER, INVOKER
    }

    private record RegistrationInfo(ClassName className, RegistrationType type, boolean isStatic) {
    }
}