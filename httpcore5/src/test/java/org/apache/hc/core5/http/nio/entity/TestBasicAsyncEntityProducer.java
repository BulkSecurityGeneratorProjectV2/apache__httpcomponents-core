/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.hc.core5.http.nio.entity;

import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.WritableByteChannelMock;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.BasicDataStreamChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestBasicAsyncEntityProducer {

    @Test
    public void testBinaryContent() throws Exception {

        final AsyncEntityProducer producer = new BasicAsyncEntityProducer(
                new byte[] { 'a', 'b', 'c' }, ContentType.DEFAULT_BINARY);

        Assertions.assertEquals(3, producer.getContentLength());
        Assertions.assertEquals(ContentType.DEFAULT_BINARY.toString(), producer.getContentType());
        Assertions.assertNull(producer.getContentEncoding());

        final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
        final DataStreamChannel streamChannel = new BasicDataStreamChannel(byteChannel);

        producer.produce(streamChannel);

        Assertions.assertFalse(byteChannel.isOpen());
        Assertions.assertEquals("abc", byteChannel.dump(StandardCharsets.US_ASCII));
    }

    @Test
    public void testTextContent() throws Exception {

        final AsyncEntityProducer producer = new BasicAsyncEntityProducer(
                "abc", ContentType.TEXT_PLAIN);

        Assertions.assertEquals(3, producer.getContentLength());
        Assertions.assertEquals(ContentType.TEXT_PLAIN.toString(), producer.getContentType());
        Assertions.assertNull(producer.getContentEncoding());

        final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
        final DataStreamChannel streamChannel = new BasicDataStreamChannel(byteChannel);

        producer.produce(streamChannel);

        Assertions.assertFalse(byteChannel.isOpen());
        Assertions.assertEquals("abc", byteChannel.dump(StandardCharsets.US_ASCII));
    }

    @Test
    public void testTextContentRepeatable() throws Exception {
        final AsyncEntityProducer producer = new BasicAsyncEntityProducer(
                "abc", ContentType.TEXT_PLAIN);

        Assertions.assertEquals(3, producer.getContentLength());
        Assertions.assertEquals(ContentType.TEXT_PLAIN.toString(), producer.getContentType());
        Assertions.assertNull(producer.getContentEncoding());

        for (int i = 0; i < 3; i++) {
            final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
            final DataStreamChannel streamChannel = new BasicDataStreamChannel(byteChannel);

            producer.produce(streamChannel);

            Assertions.assertFalse(byteChannel.isOpen());
            Assertions.assertEquals("abc", byteChannel.dump(StandardCharsets.US_ASCII));

            producer.releaseResources();
        }
    }

}
