/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.client;


import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.spi.properties.GroupProperty;
import com.hazelcast.test.AssertTask;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class ClientDisconnectTest extends HazelcastTestSupport {

    private final TestHazelcastFactory hazelcastFactory = new TestHazelcastFactory();

    @After
    public void cleanup() {
        hazelcastFactory.terminateAll();
    }


    @Test
    public void testClientOperationCancelled_whenDisconnected() throws Exception {
        Config config = new Config();
        config.setProperty(GroupProperty.CLIENT_ENDPOINT_REMOVE_DELAY_SECONDS.getName(), String.valueOf(Integer.MAX_VALUE));
        HazelcastInstance hazelcastInstance = hazelcastFactory.newHazelcastInstance();
        final String queueName = "q";

        final HazelcastInstance client = hazelcastFactory.newHazelcastClient();
        new Thread(new Runnable() {
            @Override
            public void run() {
                IQueue<Integer> queue = client.getQueue(queueName);
                try {
                    queue.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        SECONDS.sleep(2);

        client.shutdown();

        final IQueue<Integer> queue = hazelcastInstance.getQueue(queueName);
        queue.add(1);
        //dead client should not be able to consume item from queue
        assertTrueAllTheTime(new AssertTask() {
            @Override
            public void run() throws Exception {
                assertEquals(queue.size(), 1);
            }
        }, 3);
    }

    @Test
    public void testClientOperationCancelled_whenDisconnected_lock() throws Exception {
        Config config = new Config();
        config.setProperty(GroupProperty.CLIENT_ENDPOINT_REMOVE_DELAY_SECONDS.getName(), String.valueOf(Integer.MAX_VALUE));
        HazelcastInstance hazelcastInstance = hazelcastFactory.newHazelcastInstance();
        final String name = "m";

        final IMap<Object, Object> map = hazelcastInstance.getMap(name);
        final String key = "key";
        map.lock(key);

        final HazelcastInstance client = hazelcastFactory.newHazelcastClient();
        new Thread(new Runnable() {
            @Override
            public void run() {
                IMap<Object, Object> clientMap = client.getMap(name);
                clientMap.lock(key);
            }
        }).start();

        SECONDS.sleep(2);

        client.shutdown();

        map.unlock(key);
        //dead client should not be able to acquire the lock.
        assertTrueAllTheTime(new AssertTask() {
            @Override
            public void run() throws Exception {
                assertFalse(map.isLocked(key));
            }
        }, 3);
    }

}
