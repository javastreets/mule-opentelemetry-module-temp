package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.internal.processor.TraceComponent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.event.Event;

/** Transaction store for managing service transactions. */
public interface TransactionStore {

  String TRACE_TRANSACTION_ID = "TRACE_TRANSACTION_ID";
  String TRACE_CONTEXT_MAP_KEY = "OTEL_TRACE_CONTEXT";
  String TRACE_PREV_CONTEXT_MAP_KEY = "OTEL_PREV_TRACE_CONTEXT";
  String TRACE_ID = "traceId";
  String SPAN_ID = "spanId";

  /**
   * A default implementation to get a mule correlation id as a local transaction
   * id.
   *
   * @param muleEvent
   *            {@link Event}
   * @return {@link String} transaction id
   */
  default String transactionIdFor(Event muleEvent) {
    return muleEvent.getCorrelationId();
  }

  /**
   * Start a new transaction. This usually happens when a new source flow starts.
   * If the
   * transaction with `transactionId` already exists but for different
   * rootFlowName, then it is
   * possible that new rootFlowSpan is a child flow invocation. In that case, span
   * may be added to
   * an existing transaction as a span.
   *
   * @param traceComponent
   *            {@link TraceComponent} with tracing information
   * @param rootFlowName
   *            Name of the flow requesting to start transaction.
   * @param rootFlowSpan
   *            {@link SpanBuilder} for building the root span.
   */
  void startTransaction(TraceComponent traceComponent, String rootFlowName, SpanBuilder rootFlowSpan);

  /**
   * Add custom tags to an existing transaction.
   *
   * @param transactionId
   *            A unique transaction id within the context of an application. Eg.
   *            Correlation id.
   * @param tagPrefix
   * @param tags
   *            {@link Map} of {@link String} Keys and {@link String} Values
   *            containing the tags.
   */
  void addTransactionTags(String transactionId, String tagPrefix, Map<String, String> tags);

  /**
   * Get the {@link Context} of the initiating flow span. This may be required to
   * propagate
   * context for a given transaction.
   *
   * @param transactionId
   *            A unique transaction id within the context of an application. Eg.
   *            Correlation id.
   * @param componentLocation
   *            {@link ComponentLocation}
   * @return {@link Context}
   */
  TransactionContext getTransactionContext(String transactionId, ComponentLocation componentLocation);

  /**
   * Get the Trace Id associated the transaction
   *
   * @param transactionId
   *            A unique transaction id within the context of an application. Eg.
   *            Correlation id.
   * @return traceId
   */
  public String getTraceIdForTransaction(String transactionId);

  /**
   * End a transaction represented by provided transaction id and rootFlowName, if
   * exists.
   *
   * @param transactionId
   *            A unique transaction id within the context of an application. Eg.
   *            Correlation id.
   * @param rootFlowName
   *            Name of the flow requesting to start transaction.
   */
  default void endTransaction(final String transactionId, final String rootFlowName) {
    endTransaction(transactionId, rootFlowName, null);
  }

  /**
   * End a transaction represented by provided transaction id and rootFlowName, if
   * exists. {@link
   * Consumer} parameter allows updating the Span before ending. This is
   * useful in scenarios
   * like setting processing status code to error.
   *
   * <p>
   * Here is an example of setting Error when processor execution fails. <code>
   *     transactionStore.endTransaction(traceComponent.getTransactionId(), traceComponent.getName(), rootSpan -> {
   *                 if(notification.getException() != null) {
   *                     rootSpan.setStatus(StatusCode.ERROR, notification.getException().getMessage());
   *                     rootSpan.recordException(notification.getException());
   *                 }
   *             });
   * </code>
   *
   * @param transactionId
   *            A unique transaction id within the context of an application. Eg.
   *            Correlation id.
   * @param rootFlowName
   *            Name of the flow requesting to start transaction.
   * @param spanUpdater
   *            {@link Consumer} to allow updating Span before ending.
   */
  default void endTransaction(
      String transactionId, String rootFlowName, Consumer<Span> spanUpdater) {
    endTransaction(transactionId, rootFlowName, spanUpdater, null);
  }

  /**
   * End a transaction at given end time. See
   * {@link #endTransaction(String, String, Consumer)}
   *
   * @param transactionId
   * @param rootFlowName
   * @param spanUpdater
   * @param endTime
   */
  TransactionMeta endTransaction(
      String transactionId, String rootFlowName, Consumer<Span> spanUpdater, Instant endTime);

  /**
   * Add a new processor span under an existing transaction.
   *
   * @param containerName
   *            {@link String} such as Flow name that contains requested location
   * @param traceComponent
   * @param spanBuilder
   *            {@link SpanBuilder}
   */
  void addProcessorSpan(String containerName, TraceComponent traceComponent, SpanBuilder spanBuilder);

  /**
   * End an existing span under an existing transaction.
   *
   * @param transactionId
   *            {@link String} to add end span for
   * @param location
   *            {@link String} of the processor
   */
  default void endProcessorSpan(String transactionId, String location) {
    endProcessorSpan(transactionId, location, span -> {
    });
  }

  /**
   * End an existing span under an existing transaction. {@link Consumer}
   * parameter allows
   * updating the Span before ending. This is useful in scenarios like setting
   * processing status
   * code to error.
   *
   * <p>
   * Here is an example of setting Error when processor execution fails. <code>
   *     transactionStore.endProcessorSpan(traceComponent.getTransactionId(), traceComponent.getLocation(), s -> {
   *                 if(notification.getEvent().getError().isPresent()) {
   *                     Error error = notification.getEvent().getError().get();
   *                     s.setStatus(StatusCode.ERROR, error.getDescription());
   *                     s.recordException(error.getCause());
   *                 }
   *             });
   *
   * </code>
   *
   * @param transactionId
   * @param location
   * @param spanUpdater
   *            {@link Consumer} to allow updating Span before ending.
   */
  default SpanMeta endProcessorSpan(
      String transactionId, String location, Consumer<ProcessorSpan> spanUpdater) {
    return endProcessorSpan(transactionId, location, spanUpdater, null);
  }

  /**
   * This overloading allows to end a span at given time. See
   * {@link #endProcessorSpan(String,
   * String, Consumer)}.
   *
   * @param transactionId
   * @param location
   * @param spanUpdater
   * @param endTime
   * @return SpanMeta
   */
  SpanMeta endProcessorSpan(
      String transactionId, String location, Consumer<ProcessorSpan> spanUpdater, Instant endTime);
}
