/*
 * Copyright (c) 2008-2019, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.jet.beam;

import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.Validation;

public interface JetPipelineOptions extends PipelineOptions {

    @Description("Name of Jet group")
    @Validation.Required
    String getJetGroupName();
    void setJetGroupName(String jetGroupName);

    @Description("Local parallelism of Jet nodes")
    @Validation.Required
    @Default.Integer(-1)
    Integer getJetLocalParallelism();
    void setJetLocalParallelism(Integer localParallelism);

}
