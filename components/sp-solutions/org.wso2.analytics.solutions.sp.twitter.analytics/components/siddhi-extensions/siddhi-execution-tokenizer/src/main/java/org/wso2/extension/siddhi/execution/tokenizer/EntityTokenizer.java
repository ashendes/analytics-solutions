/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.extension.siddhi.execution.tokenizer;

import io.siddhi.annotation.Example;
import io.siddhi.annotation.Extension;
import io.siddhi.annotation.Parameter;
import io.siddhi.annotation.util.DataType;
import io.siddhi.core.config.SiddhiQueryContext;
import io.siddhi.core.event.ComplexEventChunk;
import io.siddhi.core.event.stream.MetaStreamEvent;
import io.siddhi.core.event.stream.StreamEvent;
import io.siddhi.core.event.stream.StreamEventCloner;
import io.siddhi.core.event.stream.holder.StreamEventClonerHolder;
import io.siddhi.core.event.stream.populater.ComplexEventPopulater;
import io.siddhi.core.exception.SiddhiAppCreationException;
import io.siddhi.core.executor.ExpressionExecutor;
import io.siddhi.core.query.processor.ProcessingMode;
import io.siddhi.core.query.processor.Processor;
import io.siddhi.core.query.processor.stream.StreamProcessor;
import io.siddhi.core.util.config.ConfigReader;
import io.siddhi.core.util.snapshot.state.State;
import io.siddhi.core.util.snapshot.state.StateFactory;
import io.siddhi.query.api.definition.AbstractDefinition;
import io.siddhi.query.api.definition.Attribute;

import java.util.ArrayList;
import java.util.List;

/**
 * splits a string into words
 */

@Extension(
        name = "tokenize",
        namespace = "entity",
        description = "This splits a string into words",
        parameters = {
                @Parameter(name = "entity.element",
                        description = "The input entity element which should be split.",
                        type = {DataType.STRING}),
        },
        examples = @Example(
                syntax = "define stream inputStream (hashtags string);\n" +
                        "@info(name = 'query1')\n" +
                        "from inputStream#entity:tokenize(hashtags)\n" +
                        "select hashtag\n" +
                        "insert into outputStream;",
                description = "This query performs tokenization for the given entity element.")
)

public class EntityTokenizer extends StreamProcessor<State> {
    private List<Attribute> attributeList = new ArrayList<>();

    @Override
    protected void process(ComplexEventChunk<StreamEvent> streamEventChunk, Processor nextProcessor,
                           StreamEventCloner streamEventCloner, ComplexEventPopulater complexEventPopulater,
                           State state) {
        while (streamEventChunk.hasNext()) {
            StreamEvent streamEvent = streamEventChunk.next();
                String event = (String) attributeExpressionExecutors[0].execute(streamEvent);
                //If the attribute is null, no need to split and handle the NullPointerException
            if (!event.equals("null")) {
                String[] words = event.split(",");
                for (String word : words) {
                    Object[] data = {word};
                    complexEventPopulater.populateComplexEvent(streamEvent, data);
                    nextProcessor.process(streamEventChunk);
                }
            }
        }
    }

    /**
     * The initialization method for {@link StreamProcessor}, which will be called before other methods and validate
     * the all configuration and getting the initial values.
     *
     * @param attributeExpressionExecutors are the executors of each attributes in the Function
     * @param configReader                 this hold the {@link StreamProcessor} extensions configuration reader.
     * @param siddhiQueryContext           The context of the Siddhi query
     */
    @Override
    protected StateFactory<State> init(MetaStreamEvent metaStreamEvent, AbstractDefinition inputDefinition,
                                ExpressionExecutor[] attributeExpressionExecutors, ConfigReader configReader,
                                StreamEventClonerHolder streamEventClonerHolder, boolean outputExpectsExpiredEvents,
                                boolean findToBeExecuted, SiddhiQueryContext siddhiQueryContext) {
        if (attributeExpressionExecutors.length == 1) {
            if (attributeExpressionExecutors[0].getReturnType() != Attribute.Type.STRING) {
                throw new SiddhiAppCreationException("Entity element should be of type string. But found "
                        + attributeExpressionExecutors[0].getReturnType());
            }
        } else {
            throw new IllegalArgumentException(
                    "Invalid no of arguments passed to text:tokenize() function, "
                            + "required 1, but found " + attributeExpressionExecutors.length);
        }
        List<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("token", Attribute.Type.STRING));
        this.attributeList = attributes;
        return null;
    }

    @Override
    public void start() {
        //Do Nothing
    }

    @Override
    public void stop() {
        //Do Nothing
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
