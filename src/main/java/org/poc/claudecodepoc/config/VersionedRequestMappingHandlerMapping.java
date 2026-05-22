package org.poc.claudecodepoc.config;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

public class VersionedRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    @Override
    protected ApiVersionCondition getCustomTypeCondition(Class<?> handlerType) {
        ApiVersion annotation = AnnotationUtils.findAnnotation(handlerType, ApiVersion.class);
        return annotation != null ? new ApiVersionCondition(annotation.from()) : null;
    }

    @Override
    protected ApiVersionCondition getCustomMethodCondition(Method method) {
        ApiVersion annotation = AnnotationUtils.findAnnotation(method, ApiVersion.class);
        return annotation != null ? new ApiVersionCondition(annotation.from()) : null;
    }
}