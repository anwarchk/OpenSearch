/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.update;

import org.opensearch.action.update.UpdateRequestBuilder;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.test.OpenSearchIntegTestCase;
import org.junit.Before;

import java.io.IOException;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests for noop updates.
 */
public class UpdateNoopIT extends OpenSearchIntegTestCase {
    public void testSingleField() throws Exception {
        updateAndCheckSource(0, 1, fields("bar", "baz"));
        updateAndCheckSource(0, 1, fields("bar", "baz"));
        updateAndCheckSource(1, 2, fields("bar", "bir"));
        updateAndCheckSource(1, 2, fields("bar", "bir"));
        updateAndCheckSource(2, 3, fields("bar", "foo"));
        updateAndCheckSource(3, 4, fields("bar", null));
        updateAndCheckSource(3, 4, fields("bar", null));
        updateAndCheckSource(4, 5, fields("bar", "foo"));
        // detect_noop defaults to true
        updateAndCheckSource(4, 5, null, fields("bar", "foo"));

        assertEquals(4, totalNoopUpdates());
    }

    public void testTwoFields() throws Exception {
        // Use random keys so we get random iteration order.
        String key1 = 1 + randomAlphaOfLength(3);
        String key2 = 2 + randomAlphaOfLength(3);
        String key3 = 3 + randomAlphaOfLength(3);
        updateAndCheckSource(0, 1, fields(key1, "foo", key2, "baz"));
        updateAndCheckSource(0, 1, fields(key1, "foo", key2, "baz"));
        updateAndCheckSource(1, 2, fields(key1, "foo", key2, "bir"));
        updateAndCheckSource(1, 2, fields(key1, "foo", key2, "bir"));
        updateAndCheckSource(2, 3, fields(key1, "foo", key2, "foo"));
        updateAndCheckSource(3, 4, fields(key1, "foo", key2, null));
        updateAndCheckSource(3, 4, fields(key1, "foo", key2, null));
        updateAndCheckSource(4, 5, fields(key1, "foo", key2, "foo"));
        updateAndCheckSource(5, 6, fields(key1, null, key2, "foo"));
        updateAndCheckSource(5, 6, fields(key1, null, key2, "foo"));
        updateAndCheckSource(6, 7, fields(key1, null, key2, null));
        updateAndCheckSource(6, 7, fields(key1, null, key2, null));
        updateAndCheckSource(7, 8, fields(key1, null, key2, null, key3, null));

        assertEquals(5, totalNoopUpdates());
    }

    public void testArrayField() throws Exception {
        updateAndCheckSource(0, 1, fields("bar", "baz"));
        updateAndCheckSource(1, 2, fields("bar", new String[] {"baz", "bort"}));
        updateAndCheckSource(1, 2, fields("bar", new String[] {"baz", "bort"}));
        updateAndCheckSource(2, 3, fields("bar", "bir"));
        updateAndCheckSource(2, 3, fields("bar", "bir"));
        updateAndCheckSource(3, 4, fields("bar", new String[] {"baz", "bort"}));
        updateAndCheckSource(3, 4, fields("bar", new String[] {"baz", "bort"}));
        updateAndCheckSource(4, 5, fields("bar", new String[] {"bir", "bort"}));
        updateAndCheckSource(4, 5, fields("bar", new String[] {"bir", "bort"}));
        updateAndCheckSource(5, 6, fields("bar", new String[] {"bir", "for"}));
        updateAndCheckSource(5, 6, fields("bar", new String[] {"bir", "for"}));
        updateAndCheckSource(6, 7, fields("bar", new String[] {"bir", "for", "far"}));

        assertEquals(5, totalNoopUpdates());
    }

    public void testMap() throws Exception {
        // Use random keys so we get variable iteration order.
        String key1 = 1 + randomAlphaOfLength(3);
        String key2 = 2 + randomAlphaOfLength(3);
        String key3 = 3 + randomAlphaOfLength(3);
        updateAndCheckSource(0, 1, XContentFactory.jsonBuilder().startObject()
                .startObject("test")
                    .field(key1, "foo")
                    .field(key2, "baz")
                .endObject().endObject());
        updateAndCheckSource(0, 1, XContentFactory.jsonBuilder().startObject()
                .startObject("test")
                    .field(key1, "foo")
                    .field(key2, "baz")
                .endObject().endObject());
        updateAndCheckSource(1, 2, XContentFactory.jsonBuilder().startObject()
                .startObject("test")
                    .field(key1, "foo")
                    .field(key2, "bir")
                .endObject().endObject());
        updateAndCheckSource(1, 2, XContentFactory.jsonBuilder().startObject()
                .startObject("test")
                    .field(key1, "foo")
                    .field(key2, "bir")
                .endObject().endObject());
        updateAndCheckSource(2, 3, XContentFactory.jsonBuilder().startObject()
                .startObject("test")
                    .field(key1, "foo")
                    .field(key2, "foo")
                .endObject().endObject());
        updateAndCheckSource(3, 4, XContentFactory.jsonBuilder().startObject()
                .startObject("test")
                    .field(key1, "foo")
                    .field(key2, (Object) null)
                .endObject().endObject());
        updateAndCheckSource(3, 4, XContentFactory.jsonBuilder().startObject()
                .startObject("test")
                    .field(key1, "foo")
                    .field(key2, (Object) null)
                .endObject().endObject());
        updateAndCheckSource(4, 5, XContentFactory.jsonBuilder().startObject()
                .startObject("test")
                    .field(key1, "foo")
                    .field(key2, (Object) null)
                    .field(key3, (Object) null)
                .endObject().endObject());

        assertEquals(3, totalNoopUpdates());
    }

    public void testMapAndField() throws Exception {
        updateAndCheckSource(0, 1, XContentFactory.jsonBuilder().startObject()
                .field("f", "foo")
                .startObject("m")
                    .field("mf1", "foo")
                    .field("mf2", "baz")
                .endObject()
                .endObject());
        updateAndCheckSource(0, 1, XContentFactory.jsonBuilder().startObject()
                .field("f", "foo")
                .startObject("m")
                    .field("mf1", "foo")
                    .field("mf2", "baz")
                .endObject()
                .endObject());
        updateAndCheckSource(1, 2, XContentFactory.jsonBuilder().startObject()
                .field("f", "foo")
                .startObject("m")
                    .field("mf1", "foo")
                    .field("mf2", "bir")
                .endObject()
                .endObject());
        updateAndCheckSource(1, 2, XContentFactory.jsonBuilder().startObject()
                .field("f", "foo")
                .startObject("m")
                    .field("mf1", "foo")
                    .field("mf2", "bir")
                .endObject()
                .endObject());
        updateAndCheckSource(2, 3, XContentFactory.jsonBuilder().startObject()
                .field("f", "foo")
                .startObject("m")
                    .field("mf1", "foo")
                    .field("mf2", "foo")
                .endObject()
                .endObject());
        updateAndCheckSource(3, 4, XContentFactory.jsonBuilder().startObject()
                .field("f", "bar")
                .startObject("m")
                    .field("mf1", "foo")
                    .field("mf2", "foo")
                .endObject()
                .endObject());
        updateAndCheckSource(3, 4, XContentFactory.jsonBuilder().startObject()
                .field("f", "bar")
                .startObject("m")
                    .field("mf1", "foo")
                    .field("mf2", "foo")
                .endObject()
                .endObject());
        updateAndCheckSource(4, 5, XContentFactory.jsonBuilder().startObject()
                .field("f", "baz")
                .startObject("m")
                    .field("mf1", "foo")
                    .field("mf2", "foo")
                .endObject()
                .endObject());
        updateAndCheckSource(5, 6, XContentFactory.jsonBuilder().startObject()
                .field("f", "bop")
                .startObject("m")
                    .field("mf1", "foo")
                    .field("mf2", "foo")
                .endObject()
                .endObject());

        assertEquals(3, totalNoopUpdates());
    }

    /**
     * Totally empty requests are noop if and only if detect noops is true and
     * its true by default.
     */
    public void testTotallyEmpty() throws Exception {
        updateAndCheckSource(0, 1, XContentFactory.jsonBuilder().startObject()
                .field("f", "foo")
                .startObject("m")
                    .field("mf1", "foo")
                    .field("mf2", "baz")
                .endObject()
                .endObject());
        update(true, 0, 1, XContentFactory.jsonBuilder().startObject().endObject());
        update(false, 1, 2, XContentFactory.jsonBuilder().startObject().endObject());
        update(null, 1, 2, XContentFactory.jsonBuilder().startObject().endObject());
    }

    private XContentBuilder fields(Object... fields) throws IOException {
        assertEquals("Fields must field1, value1, field2, value2, etc", 0, fields.length % 2);

        XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
        for (int i = 0; i < fields.length; i += 2) {
            builder.field((String) fields[i], fields[i + 1]);
        }
        builder.endObject();
        return builder;
    }

    private void updateAndCheckSource(long expectedSeqNo, long expectedVersion, XContentBuilder xContentBuilder) {
        updateAndCheckSource(expectedSeqNo, expectedVersion, true, xContentBuilder);
    }

    private void updateAndCheckSource(long expectedSeqNo, long expectedVersion, Boolean detectNoop, XContentBuilder xContentBuilder) {
        UpdateResponse updateResponse = update(detectNoop, expectedSeqNo, expectedVersion, xContentBuilder);
        assertEquals(updateResponse.getGetResult().sourceRef().utf8ToString(), BytesReference.bytes(xContentBuilder).utf8ToString());
    }

    private UpdateResponse update(Boolean detectNoop, long expectedSeqNo, long expectedVersion, XContentBuilder xContentBuilder) {
        UpdateRequestBuilder updateRequest = client().prepareUpdate("test", "type1", "1")
                .setDoc(xContentBuilder)
                .setDocAsUpsert(true)
                .setFetchSource(true);
        if (detectNoop != null) {
            updateRequest.setDetectNoop(detectNoop);
        }
        UpdateResponse updateResponse = updateRequest.get();
        assertThat(updateResponse.getGetResult(), notNullValue());
        assertThat(updateResponse.getSeqNo(), equalTo(expectedSeqNo));
        assertThat(updateResponse.getVersion(), equalTo(expectedVersion));
        return updateResponse;
    }

    private long totalNoopUpdates() {
        return client().admin().indices().prepareStats("test").setIndexing(true).get().getIndex("test").getTotal().getIndexing().getTotal()
                .getNoopUpdateCount();
    }

    @Before
    public void setup() {
        createIndex("test");
        ensureGreen();
    }
}
