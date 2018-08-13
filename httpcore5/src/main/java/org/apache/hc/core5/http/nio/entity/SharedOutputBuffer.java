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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.nio.DataStreamChannel;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public final class SharedOutputBuffer extends AbstractSharedBuffer implements ContentOutputBuffer {

    private volatile DataStreamChannel dataStreamChannel;
    private volatile boolean hasCapacity;

    public SharedOutputBuffer(final ReentrantLock lock, final int initialBufferSize) {
        super(lock, initialBufferSize);
        this.hasCapacity = false;
    }

    public SharedOutputBuffer(final int bufferSize) {
        this(new ReentrantLock(), bufferSize);
    }

    public void flush(final DataStreamChannel channel) throws IOException {
        lock.lock();
        try {
            dataStreamChannel = channel;
            hasCapacity = true;
            setOutputMode();
            if (buffer().hasRemaining()) {
                dataStreamChannel.write(buffer());
            }
            if (!buffer().hasRemaining() && endStream) {
                dataStreamChannel.endStream();
            }
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void ensureNotAborted() throws InterruptedIOException {
        if (aborted) {
            throw new InterruptedIOException("Operation aborted");
        }
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        final ByteBuffer src = ByteBuffer.wrap(b, off, len);
        lock.lock();
        try {
            ensureNotAborted();
            setInputMode();
            while (src.hasRemaining()) {
                // always buffer small chunks
                if (src.remaining() < 1024 && buffer().remaining() > src.remaining()) {
                    buffer().put(src);
                } else {
                    if (buffer().position() > 0 || dataStreamChannel == null) {
                        waitFlush();
                    }
                    if (buffer().position() == 0 && dataStreamChannel != null) {
                        final int bytesWritten = dataStreamChannel.write(src);
                        if (bytesWritten == 0) {
                            hasCapacity = false;
                            waitFlush();
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void write(final int b) throws IOException {
        lock.lock();
        try {
            ensureNotAborted();
            setInputMode();
            if (!buffer().hasRemaining()) {
                waitFlush();
            }
            buffer().put((byte)b);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void writeCompleted() throws IOException {
        if (endStream) {
            return;
        }
        lock.lock();
        try {
            if (!endStream) {
                endStream = true;
                if (dataStreamChannel != null) {
                    setOutputMode();
                    if (buffer().hasRemaining()) {
                        dataStreamChannel.requestOutput();
                    } else {
                        dataStreamChannel.endStream();
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void waitFlush() throws InterruptedIOException {
        setOutputMode();
        if (dataStreamChannel != null) {
            dataStreamChannel.requestOutput();
        }
        ensureNotAborted();
        while (buffer().hasRemaining() || !hasCapacity) {
            try {
                condition.await();
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException(ex.getMessage());
            }
            ensureNotAborted();
        }
        setInputMode();
    }

}
