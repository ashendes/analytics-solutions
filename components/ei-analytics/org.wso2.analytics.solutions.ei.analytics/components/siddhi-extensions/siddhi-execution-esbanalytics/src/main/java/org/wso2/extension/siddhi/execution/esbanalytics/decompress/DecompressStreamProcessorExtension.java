/*
 *  Copyright (c)  2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.extension.siddhi.execution.esbanalytics.decompress;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import io.siddhi.annotation.Example;
import io.siddhi.annotation.Extension;
import io.siddhi.annotation.Parameter;
import io.siddhi.annotation.ReturnAttribute;
import io.siddhi.annotation.util.DataType;
import io.siddhi.core.config.SiddhiQueryContext;
import io.siddhi.core.event.ComplexEventChunk;
import io.siddhi.core.event.stream.MetaStreamEvent;
import io.siddhi.core.event.stream.StreamEvent;
import io.siddhi.core.event.stream.StreamEventCloner;
import io.siddhi.core.event.stream.holder.StreamEventClonerHolder;
import io.siddhi.core.event.stream.populater.ComplexEventPopulater;
import io.siddhi.core.exception.SiddhiAppCreationException;
import io.siddhi.core.exception.SiddhiAppRuntimeException;
import io.siddhi.core.executor.ExpressionExecutor;
import io.siddhi.core.executor.VariableExpressionExecutor;
import io.siddhi.core.query.processor.ProcessingMode;
import io.siddhi.core.query.processor.Processor;
import io.siddhi.core.query.processor.stream.StreamProcessor;
import io.siddhi.core.util.config.ConfigReader;
import io.siddhi.core.util.snapshot.state.State;
import io.siddhi.core.util.snapshot.state.StateFactory;
import io.siddhi.query.api.definition.AbstractDefinition;
import io.siddhi.query.api.definition.Attribute;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.wso2.carbon.analytics.spark.core.util.AnalyticsConstants;
import org.wso2.carbon.analytics.spark.core.util.CompressedEventAnalyticsUtils;
import org.wso2.carbon.analytics.spark.core.util.PublishingPayload;
import org.wso2.extension.siddhi.execution.esbanalytics.decompress.util.CompressedEventUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.bind.DatatypeConverter;

import static org.wso2.extension.siddhi.execution.esbanalytics.decompress.util.ESBAnalyticsConstants.TYPE_BOOL;
import static org.wso2.extension.siddhi.execution.esbanalytics.decompress.util.ESBAnalyticsConstants.TYPE_DOUBLE;
import static org.wso2.extension.siddhi.execution.esbanalytics.decompress.util.ESBAnalyticsConstants.TYPE_FLOAT;
import static org.wso2.extension.siddhi.execution.esbanalytics.decompress.util.ESBAnalyticsConstants.TYPE_INTEGER;
import static org.wso2.extension.siddhi.execution.esbanalytics.decompress.util.ESBAnalyticsConstants.TYPE_LONG;
import static org.wso2.extension.siddhi.execution.esbanalytics.decompress.util.ESBAnalyticsConstants.TYPE_STRING;

/**
 * Decompress streaming analytics events coming from the WSO2 Enterprise Integrator
 */
@Extension(
        name = "decompress",
        namespace = "esbAnalytics",
        description = "This extension decompress any compressed analytics events coming from WSO2 Enterprise" +
                " Integrator",
        parameters = {
                @Parameter(name = "meta.compressed",
                        description = "Compressed state of the message",
                        type = {DataType.BOOL}),
                @Parameter(name = "meta.tenant.id",
                        description = "Tenant id",
                        type = {DataType.INT}),
                @Parameter(name = "message.id",
                        description = "Message id",
                        type = {DataType.STRING}),
                @Parameter(name = "flow.data",
                        description = "Compressed stream events chunk",
                        type = {DataType.STRING})
        },
        returnAttributes = {
                @ReturnAttribute(name = "messageFlowId",
                        description = "Statistic tracing id for the message flow",
                        type = {DataType.STRING}),
                @ReturnAttribute(name = "host",
                        description = "Name of the host",
                        type = {DataType.STRING}),
                @ReturnAttribute(name = "hashCode",
                        description = "HashCode of the reporting component",
                        type = {DataType.STRING}),
                @ReturnAttribute(name = "componentName",
                        description = "Name of the component",
                        type = {DataType.STRING}),
                @ReturnAttribute(name = "componentType",
                        description = "Component type of the component",
                        type = {DataType.STRING}),
                @ReturnAttribute(name = "componentIndex",
                        description = "Index of the component",
                        type = {DataType.INT}),
                @ReturnAttribute(name = "componentId",
                        description = "Unique Id of the reporting component",
                        type = {DataType.STRING}),
                @ReturnAttribute(name = "startTime",
                        description = "Start time of the event-",
                        type = {DataType.LONG}),
                @ReturnAttribute(name = "endTime",
                        description = "EndTime of the Event",
                        type = {DataType.LONG}),
                @ReturnAttribute(name = "duration",
                        description = "Event duration",
                        type = {DataType.LONG}),
                @ReturnAttribute(name = "beforePayload",
                        description = "Payload before mediation by the component",
                        type = {DataType.STRING}),
                @ReturnAttribute(name = "afterPayLoad",
                        description = "Payload after mediation by the component",
                        type = {DataType.STRING}),
                @ReturnAttribute(name = "contextPropertyMap",
                        description = "Message context properties for the component",
                        type = {DataType.STRING}),
                @ReturnAttribute(name = "transportPropertyMap",
                        description = "Transport properties for the component",
                        type = {DataType.STRING}),
                @ReturnAttribute(name = "children",
                        description = "Children List for the component",
                        type = {DataType.STRING}),
                @ReturnAttribute(name = "entryPoint",
                        description = "Entry point for the flow",
                        type = {DataType.STRING}),
                @ReturnAttribute(name = "entryPointHashcode",
                        description = "Hashcode for the entry point",
                        type = {DataType.STRING}),
                @ReturnAttribute(name = "faultCount",
                        description = "Number of faults",
                        type = {DataType.INT}),
                @ReturnAttribute(name = "metaTenantId",
                        description = "Id value of the meta tenant",
                        type = {DataType.INT}),
                @ReturnAttribute(name = "timestamp",
                        description = "Event timestamp",
                        type = {DataType.LONG})
        },
        examples = {
                @Example(
                        syntax = "define stream inputStream(meta_compressed bool, meta_tenantId int," +
                                " messageId string, flowData string); " + "@info( name = 'query') from " +
                                "inputStream#esbAnalytics:decompress(meta_compressed, meta_tenantId, " +
                                "messageId, flowData) insert all events into outputStream;",
                        description = "This query uses the incoming esb analytics message to produce decompressed " +
                                "esb analytics events."
                )
        }
)
public class DecompressStreamProcessorExtension extends StreamProcessor<State> {

    private static final ThreadLocal<Kryo> kryoTL = ThreadLocal.withInitial(() -> {

        Kryo kryo = new Kryo();
        /* Class registering precedence matters. Hence intentionally giving a registration ID */
        kryo.register(HashMap.class, 111);
        kryo.register(ArrayList.class, 222);
        kryo.register(PublishingPayload.class, 333);
        return kryo;
    });

    private String siddhiAppName;
    private Map<String, String> fields = new LinkedHashMap<>();
    private List<String> columns;
    private Map<String, VariableExpressionExecutor> compressedEventAttributes;
    private List<Attribute> attributeList = new ArrayList<>();

    /**
     * Get the definitions of the output fields in the decompressed event
     *
     * @return Name and type of decompressed fields as a Map
     */
    private static Map<String, String> getOutputFields(String siddhiAppName) {

        Map<String, String> fields = new LinkedHashMap<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            String[] lines = IOUtils.toString(classLoader.getResourceAsStream("decompressedEventDefinition"))
                    .split("\n");
            for (String line : lines) {
                if (!StringUtils.startsWithIgnoreCase(line, "#") && StringUtils.isNotEmpty(line)) {
                    String[] fieldDef = StringUtils.deleteWhitespace(line).split(":");
                    if (fieldDef.length == 2) {
                        fields.put(fieldDef[0], fieldDef[1]);
                    }
                }
            }
        } catch (IOException e) {
            throw new SiddhiAppCreationException("Unable to read decompressed event definitions " +
                    "in " + siddhiAppName + ": " + e.getMessage(), e);
        }
        return fields;
    }

    /**
     * Decompress incoming streaming event chunk and hand it over to the next processor
     *
     * @param streamEventChunk      Incoming compressed events chunk
     * @param nextProcessor         Next event processor to hand over uncompressed event chunk
     * @param streamEventCloner     Event cloner to copy the compressed event
     * @param complexEventPopulater Event populator to add uncompressed fields to the uncompressed event
     */
    @Override
    protected void process(ComplexEventChunk<StreamEvent> streamEventChunk, Processor nextProcessor,
                           StreamEventCloner streamEventCloner, ComplexEventPopulater complexEventPopulater,
                           State state) {

        ComplexEventChunk<StreamEvent> decompressedStreamEventChunk = new ComplexEventChunk<>(false);
        while (streamEventChunk.hasNext()) {
            StreamEvent compressedEvent = streamEventChunk.next();
            String eventString = (String) this.compressedEventAttributes.get(AnalyticsConstants.DATA_COLUMN)
                    .execute(compressedEvent);
            if (!eventString.isEmpty()) {
                ByteArrayInputStream unzippedByteArray;
                Boolean isCompressed = (Boolean) this.compressedEventAttributes.
                        get(AnalyticsConstants.META_FIELD_COMPRESSED).execute(compressedEvent);
                if (isCompressed) {
                    unzippedByteArray = CompressedEventAnalyticsUtils.decompress(eventString);
                } else {
                    unzippedByteArray = new ByteArrayInputStream(DatatypeConverter.parseBase64Binary(eventString));
                }
                Input input = new Input(unzippedByteArray);

                // Suppress checking for obvious uncompressed event string
                @SuppressWarnings("unchecked")
                Map<String, Object> aggregatedEvent = kryoTL.get().readObjectOrNull(input, HashMap.class);
                @SuppressWarnings("unchecked")
                ArrayList<List<Object>> eventsList = (ArrayList<List<Object>>) aggregatedEvent.get(
                        AnalyticsConstants.EVENTS_ATTRIBUTE);
                @SuppressWarnings("unchecked")
                ArrayList<PublishingPayload> payloadsList = (ArrayList<PublishingPayload>) aggregatedEvent.get(
                        AnalyticsConstants.PAYLOADS_ATTRIBUTE);

                String host = (String) aggregatedEvent.get(AnalyticsConstants.HOST_ATTRIBUTE);
                int metaTenantId = (int) this.compressedEventAttributes.get(AnalyticsConstants.META_FIELD_TENANT_ID)
                        .execute(compressedEvent);
                // Iterate over the array of events
                for (int i = 0; i < eventsList.size(); i++) {
                    StreamEvent decompressedEvent = streamEventCloner.copyStreamEvent(compressedEvent);
                    // Create a new event with the decompressed fields
                    Object[] decompressedFields = CompressedEventUtils.getFieldValues(
                            columns, eventsList.get(i), payloadsList, i,
                            compressedEvent.getTimestamp(), metaTenantId, host);
                    complexEventPopulater.populateComplexEvent(decompressedEvent, decompressedFields);
                    decompressedStreamEventChunk.add(decompressedEvent);
                }
            } else {
                throw new SiddhiAppRuntimeException("Empty message flow data event in " + this.siddhiAppName);
            }
        }
        nextProcessor.process(decompressedStreamEventChunk);
    }

    /**
     * Get attributes which are to be to be populated in the uncompressed message
     *
     * @param attributeExpressionExecutors Executors of each attributes in the Function
     * @param configReader                 this hold the {@link StreamProcessor} extensions configuration reader.
     * @param siddhiQueryContext           The context of the Siddhi query
     */
    @Override
    protected StateFactory<State> init(MetaStreamEvent metaStreamEvent, AbstractDefinition inputDefinition,
                                       ExpressionExecutor[] attributeExpressionExecutors, ConfigReader configReader,
                                       StreamEventClonerHolder streamEventClonerHolder,
                                       boolean outputExpectsExpiredEvents, boolean findToBeExecuted,
                                       SiddhiQueryContext siddhiQueryContext) {

        this.siddhiAppName = siddhiQueryContext.getSiddhiAppContext().getName();
        // Get attributes from the compressed event
        this.compressedEventAttributes = new HashMap<>();
        for (ExpressionExecutor expressionExecutor : attributeExpressionExecutors) {
            if (expressionExecutor instanceof VariableExpressionExecutor) {
                VariableExpressionExecutor variable = (VariableExpressionExecutor) expressionExecutor;
                String variableName = variable.getAttribute().getName();
                switch (variableName) {
                    case AnalyticsConstants.DATA_COLUMN:
                        this.compressedEventAttributes.put(AnalyticsConstants.DATA_COLUMN, variable);
                        break;
                    case AnalyticsConstants.META_FIELD_COMPRESSED:
                        this.compressedEventAttributes.put(AnalyticsConstants.META_FIELD_COMPRESSED, variable);
                        break;
                    case AnalyticsConstants.META_FIELD_TENANT_ID:
                        this.compressedEventAttributes.put(AnalyticsConstants.META_FIELD_TENANT_ID, variable);
                        break;
                    default:
                        break;
                }
            }
        }
        if (this.compressedEventAttributes.get(AnalyticsConstants.DATA_COLUMN) == null
                || this.compressedEventAttributes.get(AnalyticsConstants.META_FIELD_COMPRESSED) == null
                || this.compressedEventAttributes.get(AnalyticsConstants.META_FIELD_TENANT_ID) == null) {

            throw new SiddhiAppCreationException("Cannot find required attributes in " + this.siddhiAppName + ". " +
                    "Please provide flowData, meta_compressed, meta_tenantId attributes in exact names");
        }

        // Set uncompressed event attributes
        this.fields = getOutputFields(this.siddhiAppName);
        List<Attribute> outputAttributes = new ArrayList<>();
        for (Map.Entry<String, String> entry : this.fields.entrySet()) {
            String fieldName = entry.getKey();
            String fieldType = entry.getValue();
            Attribute.Type type = null;
            switch (fieldType.toLowerCase(Locale.ENGLISH)) {
                case TYPE_DOUBLE:
                    type = Attribute.Type.DOUBLE;
                    break;
                case TYPE_FLOAT:
                    type = Attribute.Type.FLOAT;
                    break;
                case TYPE_INTEGER:
                    type = Attribute.Type.INT;
                    break;
                case TYPE_LONG:
                    type = Attribute.Type.LONG;
                    break;
                case TYPE_BOOL:
                    type = Attribute.Type.BOOL;
                    break;
                case TYPE_STRING:
                    type = Attribute.Type.STRING;
                    break;
                default:
                    break;
            }
            outputAttributes.add(new Attribute(fieldName, type));
        }
        this.columns = new ArrayList<>(this.fields.keySet());

        this.attributeList = outputAttributes;
        return null;
    }

    @Override
    public void start() {

    }

    /**
     * This will be called only once and this can be used to release
     * the acquired resources for processing.
     * This will be called before shutting down the system.
     */
    @Override
    public void stop() {

    }

    @Override
    public List<Attribute> getReturnAttributes() {
        return this.attributeList;
    }

    @Override
    public ProcessingMode getProcessingMode() {
        return ProcessingMode.SLIDE;
    }
}

