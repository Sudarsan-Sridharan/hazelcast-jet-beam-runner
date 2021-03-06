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

package com.hazelcast.jet.beam.processors;

import com.hazelcast.jet.core.AbstractProcessor;
import com.hazelcast.jet.core.Processor;
import com.hazelcast.jet.function.SupplierEx;

import javax.annotation.Nonnull;

public class FlattenP extends AbstractProcessor {

    @SuppressWarnings("FieldCanBeLocal") //do not remove, useful for debugging
    private final String ownerId;

    private FlattenP(String ownerId) {
        this.ownerId = ownerId;
        //System.out.println(FlattenP.class.getSimpleName() + " CREATE, ownerId = " + ownerId); //useful for debugging
    }

    @Override
    protected boolean tryProcess(int ordinal, @Nonnull Object item) {
        //System.out.println(FlattenP.class.getSimpleName() + " UPDATE ownerId = " + ownerId + ", item = " + item); //useful for debugging
        return tryEmit(item);
    }

    public static SupplierEx<Processor> supplier(String ownerId) {
        return () -> new FlattenP(ownerId);
    }
}
