package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.internal.utils.TraceUtil;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.mule.extension.http.api.HttpRequestAttributes;
import org.mule.extension.http.api.HttpResponseAttributes;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.*;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_HOST;


public class HttpProcessorComponent extends GenericProcessorComponent {

    static final String NAMESPACE_URI = "http://www.mulesoft.org/schema/mule/http";
    public static final String NAMESPACE = "http";
    public static final String LISTENER = "listener";
    public static final String REQUESTER = "request";

    @Override
    public boolean canHandle(ComponentIdentifier componentIdentifier) {
        return NAMESPACE.equalsIgnoreCase(componentIdentifier.getNamespace())
                && (LISTENER.equalsIgnoreCase(componentIdentifier.getName())
                    || REQUESTER.equalsIgnoreCase(componentIdentifier.getName()));
    }

    private boolean isRequester(ComponentIdentifier componentIdentifier) {
        return NAMESPACE.equalsIgnoreCase(componentIdentifier.getNamespace())
                && REQUESTER.equalsIgnoreCase(componentIdentifier.getName());
    }
    private boolean isHttpListener(ComponentIdentifier componentIdentifier) {
        return NAMESPACE.equalsIgnoreCase(componentIdentifier.getNamespace())
                && LISTENER.equalsIgnoreCase(componentIdentifier.getName());
    }
    private boolean isListenerFlowEvent(EnrichedServerNotification notification){
        return FLOW.equals(notification.getComponent().getIdentifier().getName())
                && isHttpListener(getSourceIdentifier(notification));
    }

    @Override
    public TraceComponent getEndTraceComponent(EnrichedServerNotification notification) {
        TraceComponent endTraceComponent = super.getEndTraceComponent(notification);

        //When an error is thrown by http:request, the response message will be on error object.
        Message responseMessage = notification.getEvent().getError()
                .map(Error::getErrorMessage)
                .orElse(notification.getEvent().getMessage());
        TypedValue<HttpResponseAttributes> httpResponseAttributesTypedValue = responseMessage.getAttributes();
        HttpResponseAttributes attributes = httpResponseAttributesTypedValue.getValue();
        Map<String, String> tags = new HashMap<>();
        tags.put(HTTP_STATUS_CODE.getKey(), Integer.toString(attributes.getStatusCode()));
        tags.put(HTTP_RESPONSE_CONTENT_LENGTH.getKey(), attributes.getHeaders().get("content-length"));
        if (endTraceComponent.getTags() != null) tags.putAll(endTraceComponent.getTags());
        return endTraceComponent.toBuilder().withTags(tags).build();
    }

    @Override
    public TraceComponent getStartTraceComponent(EnrichedServerNotification notification) {
        if(isListenerFlowEvent(notification)){
            return processHttpListener(notification);
        }

        TraceComponent traceComponent = super.getStartTraceComponent(notification);

        Map<String, String> requesterTags = getRequesterTags(notification);
        requesterTags.putAll(traceComponent.getTags());

        return TraceComponent.newBuilder(notification.getResourceIdentifier())
                .withTags(requesterTags)
                .withLocation(notification.getComponent().getLocation().getLocation())
                .withSpanName(requesterTags.get(HTTP_ROUTE.getKey()))
                .withTransactionId(traceComponent.getTransactionId())
                .withSpanKind(SpanKind.CLIENT)
                .build();
    }
    private Map<String, String> getRequesterTags(EnrichedServerNotification notification) {
        Map<String, String> tags = new HashMap<>();
        tags.put(HTTP_ROUTE.getKey(), getComponentParameter(notification,"path"));
        tags.put(HTTP_METHOD.getKey(), getComponentParameter(notification, "method"));
        tags.put("http.request.configRef", getComponentConfigRef(notification));
        return tags;
    }
    private TraceComponent processHttpListener(EnrichedServerNotification notification) {
        TypedValue<HttpRequestAttributes> attributesTypedValue = notification.getEvent().getMessage().getAttributes();
        HttpRequestAttributes attributes = attributesTypedValue.getValue();
        Map<String, String> tags = attributesToTags(attributes);
        return TraceComponent
                .newBuilder(getComponentParameterName(notification))
                .withTags(tags)
                .withTransactionId(getTransactionId(notification))
                .withSpanName(attributes.getListenerPath())
                .withContext(TraceUtil.getTraceContext(attributes, ContextMapGetter.INSTANCE))
                .build();
    }

    private Map<String, String> attributesToTags(HttpRequestAttributes attributes) {
        Map<String, String> tags = new HashMap<>();
        tags.put(HTTP_HOST.getKey(), attributes.getHeaders().get("host"));
        tags.put(HTTP_USER_AGENT.getKey(), attributes.getHeaders().get("user-agent"));
        tags.put(HTTP_METHOD.getKey(), attributes.getMethod());
        tags.put(HTTP_SCHEME.getKey(), attributes.getScheme());
        tags.put(HTTP_ROUTE.getKey(), attributes.getListenerPath());
        tags.put(HTTP_TARGET.getKey(), attributes.getRequestPath());
        tags.put(HTTP_FLAVOR.getKey(), attributes.getVersion().substring(attributes.getVersion().lastIndexOf("/") + 1));
        //TODO: Support additional request headers to be added in http.request.header.<key>=<header-value> attribute.
        return tags;
    }

    private enum ContextMapGetter implements  TextMapGetter<HttpRequestAttributes> {
        INSTANCE;

        @Override
        public Iterable<String> keys(HttpRequestAttributes httpRequestAttributes) {
            return httpRequestAttributes.getHeaders().keySet();
        }

        @Nullable
        @Override
        public String get(@Nullable HttpRequestAttributes httpRequestAttributes, String s) {
            return httpRequestAttributes == null ? null : httpRequestAttributes.getHeaders().get(s);
        }
    }


}