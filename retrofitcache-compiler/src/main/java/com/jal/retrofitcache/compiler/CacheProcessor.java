package com.jal.retrofitcache.compiler;

import com.google.auto.service.AutoService;
import com.jal.retrofitcache.annotation.Cache;
import com.jal.retrofitcache.annotation.CacheApi;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import static javax.lang.model.element.Modifier.PUBLIC;

@AutoService(Processor.class)
@SupportedAnnotationTypes({"com.jal.retrofitcache.annotation.Cache", "com.jal.retrofitcache.annotation.CacheApi"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class CacheProcessor extends BaseProcessor {
    private final static String OBSERVABLE = "io.reactivex.Observable";
    private final static String HANDLER = "com.jal.retrofitcach.api.ICacheHandler";

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        logger.info("------------init-----------");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (CollectionUtils.isNotEmpty(annotations)) {
            Set<? extends Element> routeElements = roundEnv.getElementsAnnotatedWith(CacheApi.class);
            try {
                logger.info(">>> Found routes, start... <<<");
                this.parseCache(routeElements);

            } catch (Exception e) {
                logger.error(e);
            }
            return true;
        }

        return false;
    }

    private Map<TypeMirror, Set<CacheMeta>> groupMap = new HashMap<>();

    private void parseCache(Set<? extends Element> routeElements) throws IOException, ClassNotFoundException {
        if (CollectionUtils.isEmpty(routeElements)) {
            return;
        }
        TypeMirror type_Handler = elementUtils.getTypeElement(HANDLER).asType();
        groupMap.clear();
        logger.info(">>> Found cache, size is " + routeElements.size() + " <<<");
        for (Element element : routeElements) {
            Set<CacheMeta> cacheSet;
            if (element.getKind().isInterface()) {
                List<? extends Element> parentMethod = element.getEnclosedElements();
                TypeMirror typeMirror = element.asType();
                if (groupMap.containsKey(typeMirror)) {
                    logger.info(">>> containsKey <<<");
                    cacheSet = groupMap.get(typeMirror);
                } else {
                    logger.info(">>> not containsKey <<<");
                    cacheSet = new HashSet<>();
                }
                for (Element methodElement : parentMethod) {
                    if (methodElement instanceof ExecutableElement) {
                        cacheSet.add(new CacheMeta((ExecutableElement) methodElement, methodElement.getAnnotation(Cache.class)));
                    }
                }
                groupMap.put(typeMirror, cacheSet);
            }
        }
        for (Map.Entry<TypeMirror, Set<CacheMeta>> entry : groupMap.entrySet()) {
            TypeMirror typeMirror = entry.getKey();
            String sampleName = typeMirror.toString();
            logger.info(">>> sampleName = " + sampleName + sampleName.lastIndexOf(".") + " <<<");
            sampleName = sampleName.substring(sampleName.lastIndexOf(".") + 1);
            logger.info(">>> name = " + sampleName + "Impl" + " <<<");
            Set<CacheMeta> cacheMetas = groupMap.get(typeMirror);
            //create api proxy
            FieldSpec mApi = FieldSpec.builder(TypeVariableName.get(typeMirror), "mApi", Modifier.PRIVATE).build();
            TypeSpec.Builder builder = TypeSpec.classBuilder(sampleName + "Impl")
                    .addSuperinterface(ClassName.get(typeMirror))
                    .addModifiers(PUBLIC)
                    .addField(mApi);
            // set api proxy
            builder.addMethod(MethodSpec.methodBuilder("setApi")
                    .addParameter(ParameterSpec.builder(ClassName.get(typeMirror), "api").build())
                    .addStatement("this.mApi=api").build());
            //create cache proxy
            TypeName handler = ClassName.get(type_Handler);
            FieldSpec cacheHandler = FieldSpec.builder(handler, "cacheHandler", Modifier.PRIVATE).build();
            // set cache proxy
            builder.addField(cacheHandler);
            builder.addMethod(MethodSpec.methodBuilder("setCacheHandler")
                    .addParameter(ParameterSpec.builder(handler, "handler").build())
                    .addStatement("this.cacheHandler=handler").build());
            //create method
            for (CacheMeta meta : cacheMetas) {
                addProxyMethod(builder, meta, false);
                if (meta.cache != null) {
                    addProxyMethod(builder, meta, true);
                }
            }
            JavaFile.builder(PACKAGE_OF_GENERATE_FILE,
                    builder.build()).build().writeTo(mFiler);
        }
    }

    private void addProxyMethod(TypeSpec.Builder builder, CacheMeta meta, boolean cache) {
        List<? extends VariableElement> params = meta.methodElement.getParameters();
        String methodName = meta.methodElement.getSimpleName().toString();
        List<? extends VariableElement> methodParam = meta.methodElement.getParameters();
        StringBuilder paramNames = new StringBuilder();
        for (int i = 0; i < methodParam.size(); i++) {
            if (i > 0) {
                paramNames.append(",");
            }
            paramNames.append(methodParam.get(i).getSimpleName().toString());
        }
        StringBuilder format = new StringBuilder();
        format.append("return mApi.");
        format.append(meta.methodElement.getSimpleName());
        if (CollectionUtils.isEmpty(params)) {
            format.append("()");
        } else {
            format.append("(");
            int i = 0;
            for (VariableElement param : params) {
                if (i > 0) {
                    format.append(",");
                }
                i++;
                format.append(param.getSimpleName());
            }
            format.append(")");
        }
        if (cache) {
            //filter not return Observable
            if (!meta.methodElement.getReturnType().toString().startsWith(OBSERVABLE)) {
                return;
            }
            MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(meta.methodElement.getSimpleName().toString());
            if (!CollectionUtils.isEmpty(params)) {
                for (VariableElement param : params) {
                    methodBuilder.addParameter(ParameterSpec.builder(ClassName.get(param.asType()), param.getSimpleName().toString()).addModifiers(Modifier.FINAL).build());
                }
            }
            methodBuilder.addParameter(ParameterSpec.builder(boolean.class, "cache").build());
            methodBuilder.returns(ClassName.get(meta.methodElement.getReturnType()));
            StringBuilder cacheReturn = new StringBuilder("$T.concat(");
            List<String> genericParameterTypes = getGenericParameterTypes(meta.methodElement.getReturnType().toString());
            logger.info(">>> item.size=" + genericParameterTypes.size() + " <<<");
            StringBuilder genericFormat = new StringBuilder();
            for (int i = 1; i < genericParameterTypes.size(); i++) {
                if (i > 1) {
                    genericFormat.append("<");
                }
                genericFormat.append("$T");
            }
            for (int i = 2; i < genericParameterTypes.size(); i++) {
                genericFormat.append(">");
            }
            cacheReturn.append("$T.create(new $T<" + genericFormat.toString() + ">() {\n" +
                    "            @Override\n" +
                    "            public void subscribe($T<" + genericFormat.toString() + "> emitter) {\n" +
                    "\n" +
                    "                " + genericFormat.toString() + " cacheData = null;\n" +
                    "                try {\n" +
                    "                    String json = cacheHandler.getCache(\"" + methodName + "\"," + paramNames + ");\n" +
                    "                    if (json != null) {\n" +
                    "                        cacheData = new $T().fromJson(json,\n" +
                    "                                new $T<" + genericFormat.toString() + ">() {\n" +
                    "                                }.getType());\n" +
                    "                    }\n" +
                    "                } catch (Exception e) {\n" +
                    "                    e.printStackTrace();\n" +
                    "                }\n" +
                    "                if (cacheData != null) {\n" +
                    "                    emitter.onNext(cacheData);\n" +
                    "                }\n" +
                    "                emitter.onComplete(); \n" +
                    "            }\n" +
                    "        })");
            cacheReturn.append(",");
            cacheReturn.append(format.toString().replace("return", ""));
            cacheReturn.append(".map(new $T<" + genericFormat.toString() + ", " + genericFormat.toString() + ">() {\n" +
                    "                    @Override\n" +
                    "                    public " + genericFormat.toString() + " apply(" + genericFormat.toString() + " result) {\n" +
                    "                        cacheHandler.saveCache(result,\"" + methodName + "\"," + paramNames + ");\n" +
                    "                        return result;\n" +
                    "                    }\n" +
                    "                })");
            cacheReturn.append(")");

            String statementBuilder = "return cache ? " + cacheReturn.toString() + " : " + format.toString().replace("return", "");
            List<Object> args = new ArrayList<>();
            args.add(ClassName.get("io.reactivex", "Observable"));
            args.add(ClassName.get("io.reactivex", "Observable"));
            args.add(ClassName.get("io.reactivex", "ObservableOnSubscribe"));
            addClass(args, genericParameterTypes);
            args.add(ClassName.get("io.reactivex", "ObservableEmitter"));
            addClass(args, genericParameterTypes);

            addClass(args, genericParameterTypes);
            args.add(ClassName.get("com.google.gson", "Gson"));
            args.add(ClassName.get("com.google.gson.reflect", "TypeToken"));
            addClass(args, genericParameterTypes);
            args.add(ClassName.get("io.reactivex.functions", "Function"));
            addClass(args, genericParameterTypes);
            addClass(args, genericParameterTypes);
            addClass(args, genericParameterTypes);
            addClass(args, genericParameterTypes);
            methodBuilder.addStatement(statementBuilder, args.toArray(new Object[]{}));
            methodBuilder.addModifiers(Modifier.PUBLIC);
            builder.addMethod(methodBuilder.build());
        } else {
            builder.addMethod(MethodSpec.overriding(meta.methodElement)
                    .addStatement(format.toString()).build());
        }
    }

    private void addClass(List<Object> args, List<String> genericParameterTypes) {
        for (int i = 1; i < genericParameterTypes.size(); i++) {
            String item = genericParameterTypes.get(i);
            String pkg = genericParameterTypes.get(i).substring(0, item.lastIndexOf("."));
            String name = genericParameterTypes.get(i).substring(item.lastIndexOf(".") + 1);
            args.add(ClassName.get(pkg, name));
        }
    }

    private final static String PACKAGE_OF_GENERATE_FILE = "com.jal.http.cache";

    private List<String> getGenericParameterTypes(String info) {
        logger.info(">>> info " + info + " <<<");
        List<String> genericList = new ArrayList<>();
        if (!info.contains("<")) {
            genericList.add(info);
            return genericList;
        }
        String sub = info;
        while (sub.contains("<")) {
            String cls = sub.substring(0, sub.indexOf("<"));
            logger.info(">>> cls " + cls + " <<<");
            genericList.add(cls);
            sub = sub.substring(sub.indexOf("<") + 1, sub.lastIndexOf(">"));
        }
        if (!"".equals(sub)) {
            logger.info(">>> cls " + sub + " <<<");
            genericList.add(sub);
        }
        return genericList;
    }
}
