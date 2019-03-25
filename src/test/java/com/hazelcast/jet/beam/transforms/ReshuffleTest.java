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

package com.hazelcast.jet.beam.transforms;

import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.coders.VarIntCoder;
import org.apache.beam.sdk.coders.VarLongCoder;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestStream;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.Reify;
import org.apache.beam.sdk.transforms.Reshuffle;
import org.apache.beam.sdk.transforms.SerializableFunction;
import org.apache.beam.sdk.transforms.Values;
import org.apache.beam.sdk.transforms.WithKeys;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.GlobalWindow;
import org.apache.beam.sdk.transforms.windowing.Sessions;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TimestampedValue;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.apache.beam.sdk.values.WindowingStrategy;
import org.apache.beam.vendor.guava.v20_0.com.google.common.collect.ImmutableList;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.junit.Ignore;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static org.apache.beam.sdk.TestUtils.KvMatcher.isKv;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/* "Inspired" by org.apache.beam.sdk.transforms.ReshuffleTest */
public class ReshuffleTest extends AbstractTransformTest {

    private static final ImmutableList<KV<String, Integer>> ARBITRARY_KVS =
            ImmutableList.of(
                    KV.of("k1", 3),
                    KV.of("k5", Integer.MAX_VALUE),
                    KV.of("k5", Integer.MIN_VALUE),
                    KV.of("k2", 66),
                    KV.of("k1", 4),
                    KV.of("k2", -33),
                    KV.of("k3", 0));

    private static final ImmutableList<KV<String, Integer>> GBK_TESTABLE_KVS =
            ImmutableList.of(KV.of("k1", 3), KV.of("k2", 4));

    @Test
    public void testJustReshuffle() {

        PCollection<KV<String, Integer>> input =
                pipeline.apply(
                        Create.of(ARBITRARY_KVS).withCoder(KvCoder.of(StringUtf8Coder.of(), VarIntCoder.of())));

        PCollection<KV<String, Integer>> output = input.apply(Reshuffle.of());

        PAssert.that(output).containsInAnyOrder(ARBITRARY_KVS);

        assertEquals(input.getWindowingStrategy(), output.getWindowingStrategy());

        pipeline.run();
    }

    /**
     * Tests that timestamps are preserved after applying a {@link Reshuffle} with the default {@link
     * WindowingStrategy}.
     */
    @Test
    public void testReshufflePreservesTimestamps() {
        PCollection<KV<String, TimestampedValue<String>>> input =
                pipeline
                        .apply(
                                Create.timestamped(
                                        TimestampedValue.of("foo", BoundedWindow.TIMESTAMP_MIN_VALUE),
                                        TimestampedValue.of("foo", new Instant(0)),
                                        TimestampedValue.of("bar", new Instant(33)),
                                        TimestampedValue.of("bar", GlobalWindow.INSTANCE.maxTimestamp()))
                                        .withCoder(StringUtf8Coder.of()))
                        .apply(
                                WithKeys.<String, String>of(input12 -> input12)
                                        .withKeyType(TypeDescriptors.strings()))
                        .apply("ReifyOriginalTimestamps", Reify.timestampsInValue());

        // The outer TimestampedValue is the reified timestamp post-reshuffle. The inner
        // TimestampedValue is the pre-reshuffle timestamp.
        PCollection<TimestampedValue<TimestampedValue<String>>> output =
                input
                        .apply(Reshuffle.of())
                        .apply("ReifyReshuffledTimestamps", Reify.timestampsInValue())
                        .apply(Values.create());

        PAssert.that(output)
                .satisfies(
                        input1 -> {
                            for (TimestampedValue<TimestampedValue<String>> elem : input1) {
                                Instant originalTimestamp = elem.getValue().getTimestamp();
                                Instant afterReshuffleTimestamp = elem.getTimestamp();
                                assertThat(
                                        "Reshuffle must preserve element timestamps",
                                        afterReshuffleTimestamp,
                                        equalTo(originalTimestamp));
                            }
                            return null;
                        });

        pipeline.run();
    }

    @Test
    public void testReshuffleAfterSessionsAndGroupByKey() {

        PCollection<KV<String, Iterable<Integer>>> input =
                pipeline
                        .apply(
                                Create.of(GBK_TESTABLE_KVS)
                                        .withCoder(KvCoder.of(StringUtf8Coder.of(), VarIntCoder.of())))
                        .apply(Window.into(Sessions.withGapDuration(Duration.standardMinutes(10))))
                        .apply(GroupByKey.create());

        PCollection<KV<String, Iterable<Integer>>> output = input.apply(Reshuffle.of());

        PAssert.that(output).satisfies(new AssertThatHasExpectedContents());

        assertEquals(input.getWindowingStrategy(), output.getWindowingStrategy());

        pipeline.run();
    }

    @Test
    public void testReshuffleAfterFixedWindowsAndGroupByKey() {

        PCollection<KV<String, Iterable<Integer>>> input =
                pipeline
                        .apply(
                                Create.of(GBK_TESTABLE_KVS)
                                        .withCoder(KvCoder.of(StringUtf8Coder.of(), VarIntCoder.of())))
                        .apply(Window.into(FixedWindows.of(Duration.standardMinutes(10L))))
                        .apply(GroupByKey.create());

        PCollection<KV<String, Iterable<Integer>>> output = input.apply(Reshuffle.of());

        PAssert.that(output).satisfies(new AssertThatHasExpectedContents());

        assertEquals(input.getWindowingStrategy(), output.getWindowingStrategy());

        pipeline.run();
    }

    @Test
    public void testReshuffleAfterSlidingWindowsAndGroupByKey() {

        PCollection<KV<String, Iterable<Integer>>> input =
                pipeline
                        .apply(
                                Create.of(GBK_TESTABLE_KVS)
                                        .withCoder(KvCoder.of(StringUtf8Coder.of(), VarIntCoder.of())))
                        .apply(Window.into(FixedWindows.of(Duration.standardMinutes(10L))))
                        .apply(GroupByKey.create());

        PCollection<KV<String, Iterable<Integer>>> output = input.apply(Reshuffle.of());

        PAssert.that(output).satisfies(new AssertThatHasExpectedContents());

        assertEquals(input.getWindowingStrategy(), output.getWindowingStrategy());

        pipeline.run();
    }

    @Test
    public void testReshuffleAfterFixedWindows() {

        PCollection<KV<String, Integer>> input =
                pipeline
                        .apply(
                                Create.of(ARBITRARY_KVS)
                                        .withCoder(KvCoder.of(StringUtf8Coder.of(), VarIntCoder.of())))
                        .apply(Window.into(FixedWindows.of(Duration.standardMinutes(10L))));

        PCollection<KV<String, Integer>> output = input.apply(Reshuffle.of());

        PAssert.that(output).containsInAnyOrder(ARBITRARY_KVS);

        assertEquals(input.getWindowingStrategy(), output.getWindowingStrategy());

        pipeline.run();
    }

    @Test
    public void testReshuffleAfterSlidingWindows() {

        PCollection<KV<String, Integer>> input =
                pipeline
                        .apply(
                                Create.of(ARBITRARY_KVS)
                                        .withCoder(KvCoder.of(StringUtf8Coder.of(), VarIntCoder.of())))
                        .apply(Window.into(FixedWindows.of(Duration.standardMinutes(10L))));

        PCollection<KV<String, Integer>> output = input.apply(Reshuffle.of());

        PAssert.that(output).containsInAnyOrder(ARBITRARY_KVS);

        assertEquals(input.getWindowingStrategy(), output.getWindowingStrategy());

        pipeline.run();
    }

    @Test
    @Ignore //todo: TestStream not handled (yet?)
    public void testReshuffleWithTimestampsStreaming() {
        TestStream<Long> stream =
                TestStream.create(VarLongCoder.of())
                        .advanceWatermarkTo(new Instant(0L).plus(Duration.standardDays(48L)))
                        .addElements(
                                TimestampedValue.of(0L, new Instant(0L)),
                                TimestampedValue.of(1L, new Instant(0L).plus(Duration.standardDays(48L))),
                                TimestampedValue.of(
                                        2L, BoundedWindow.TIMESTAMP_MAX_VALUE.minus(Duration.standardDays(48L))))
                        .advanceWatermarkToInfinity();
        PCollection<KV<String, Long>> input =
                pipeline
                        .apply(stream)
                        .apply(WithKeys.of(""))
                        .apply(Window.into(FixedWindows.of(Duration.standardMinutes(10L))));

        PCollection<KV<String, Long>> reshuffled = input.apply(Reshuffle.of());
        PAssert.that(reshuffled.apply(Values.create())).containsInAnyOrder(0L, 1L, 2L);

        pipeline.run();
    }

    private static class AssertThatHasExpectedContents
            implements SerializableFunction<Iterable<KV<String, Iterable<Integer>>>, Void> {
        @Override
        public Void apply(Iterable<KV<String, Iterable<Integer>>> actual) {
            assertThat(
                    actual,
                    containsInAnyOrder(
                            isKv(is("k1"), containsInAnyOrder(3)), isKv(is("k2"), containsInAnyOrder(4))));
            return null;
        }
    }

}