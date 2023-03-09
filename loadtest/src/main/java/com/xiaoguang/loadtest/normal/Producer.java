package com.xiaoguang.loadtest.normal;

import com.xiaoguang.loadtest.utils.AclClient;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class Producer {

    private static byte[] msgBody;
    private static final int MAX_LENGTH_ASYNC_QUEUE = 10000;
    private static final int SLEEP_FOR_A_WHILE = 100;

    public static void main(String[] args) throws MQClientException {
        final String namesrv = args[0];
        final String topic = args[1];
        final int messageSize = Integer.parseInt(args[2]);
        final boolean keyEnable = Boolean.parseBoolean(args[3]);
        final int propertySize = Integer.parseInt(args[4]);
        final int tagCount = Integer.parseInt(args[5]);
        final boolean msgTraceEnable = Boolean.parseBoolean(args[6]);
        final boolean aclEnable = Boolean.parseBoolean(args[7]);
        final long messageNum = Long.parseLong(args[8]);
        final boolean delayEnable = Boolean.parseBoolean(args[9]);
        final int delayLevel = Integer.parseInt(args[10]);
        final boolean asyncEnable = Boolean.parseBoolean(args[11]);
        final int threadCount = Integer.parseInt(args[12]);
        final String ak = args[13];
        final String sk = args[14];

        System.out.printf("topic: %s, threadCount: %d, messageSize: %d, keyEnable: %s, propertySize: %d, tagCount: %d, " +
                        "traceEnable: %s, aclEnable: %s, messageQuantity: %d, delayEnable: %s, delayLevel: %s, " +
                        "asyncEnable: %s%n",
                topic, threadCount, messageSize, keyEnable, propertySize, tagCount, msgTraceEnable, aclEnable, messageNum,
                delayEnable, delayLevel, asyncEnable);

        StringBuilder sb = new StringBuilder(messageSize);
        for (int i = 0; i < messageSize; i++) {
            sb.append(RandomStringUtils.randomAlphanumeric(1));
        }
        msgBody = sb.toString().getBytes(StandardCharsets.UTF_8);

        final ExecutorService sendThreadPool = Executors.newFixedThreadPool(threadCount);

        final StatsBenchmarkProducer statsBenchmark = new StatsBenchmarkProducer();

        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1,
                new BasicThreadFactory.Builder().namingPattern("BenchmarkTimerThread-%d").daemon(true).build());

        final LinkedList<Long[]> snapshotList = new LinkedList<>();

        final long[] msgNums = new long[threadCount];

        if (messageNum > 0) {
            Arrays.fill(msgNums, messageNum / threadCount);
            long mod = messageNum % threadCount;
            if (mod > 0) {
                msgNums[0] += mod;
            }
        }

        executorService.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                snapshotList.addLast(statsBenchmark.createSnapshot());
                if (snapshotList.size() > 10) {
                    snapshotList.removeFirst();
                }
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);

        executorService.scheduleAtFixedRate(new TimerTask() {
            private void printStats() {
                if (snapshotList.size() >= 10) {
                    doPrintStats(snapshotList,  statsBenchmark, false);
                }
            }

            @Override
            public void run() {
                try {
                    this.printStats();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 10000, 10000, TimeUnit.MILLISECONDS);

        RPCHook rpcHook = null;
        if (aclEnable) {
            rpcHook = AclClient.getAclRPCHook(ak, sk);
        }
        final DefaultMQProducer producer = new DefaultMQProducer("benchmark_producer", rpcHook, msgTraceEnable, null);
        //RocketMQ默认会使用ip@instanceName@unitName的方式来区分Producer实例，instanceName如果缺省就会被替换成PID，unitName缺省会为空，如果在一个JVM里启动多个Producer实例且缺省InstanceName值，那么clientId将会是一样,这时他们会使用同一个MQClientInstance,导致消息只会发送到一个Broker
        producer.setInstanceName(Long.toString(System.currentTimeMillis()));

        producer.setNamesrvAddr(namesrv);

        producer.setCompressMsgBodyOverHowmuch(Integer.MAX_VALUE);

        producer.start();

        for (int i = 0; i < threadCount; i++) {
            final long msgNumLimit = msgNums[i];
            if (messageNum > 0 && msgNumLimit == 0) {
                break;
            }
            sendThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    int num = 0;
                    while (true) {
                        try {
                            final Message msg = buildMessage(topic);
                            final long beginTimestamp = System.currentTimeMillis();
                            if (keyEnable) {
                                msg.setKeys(String.valueOf(beginTimestamp / 1000));
                            }
                            if (delayEnable) {
                                msg.setDelayTimeLevel(delayLevel);
                            }
                            if (tagCount > 0) {
                                msg.setTags(String.format("tag%d", System.currentTimeMillis() % tagCount));
                            }
                            if (propertySize > 0) {
                                if (msg.getProperties() != null) {
                                    msg.getProperties().clear();
                                }
                                int i = 0;
                                int startValue = (new Random(System.currentTimeMillis())).nextInt(100);
                                int size = 0;
                                while (true) {
                                    String prop1 = "prop" + i, prop1V = "hello" + startValue;
                                    String prop2 = "prop" + (i + 1), prop2V = String.valueOf(startValue);
                                    msg.putUserProperty(prop1, prop1V);
                                    msg.putUserProperty(prop2, prop2V);
                                    size += prop1.length() + prop2.length() + prop1V.length() + prop2V.length();
                                    if (size > propertySize) {
                                        break;
                                    }
                                    i += 2;
                                    startValue += 2;
                                }
                            }
                            if (asyncEnable) {
                                ThreadPoolExecutor e = (ThreadPoolExecutor) producer.getDefaultMQProducerImpl().getAsyncSenderExecutor();
                                // Flow control
                                while (e.getQueue().size() > MAX_LENGTH_ASYNC_QUEUE) {
                                    Thread.sleep(SLEEP_FOR_A_WHILE);
                                }
                                producer.send(msg, new SendCallback() {
                                    @Override
                                    public void onSuccess(SendResult sendResult) {
                                        updateStatsSuccess(statsBenchmark, beginTimestamp);
                                    }

                                    @Override
                                    public void onException(Throwable e) {
                                        statsBenchmark.getSendRequestFailedCount().increment();
                                    }
                                });
                            } else {
                                producer.send(msg);
                                updateStatsSuccess(statsBenchmark, beginTimestamp);
                            }
                        } catch (RemotingException e) {
                            statsBenchmark.getSendRequestFailedCount().increment();
                            System.out.printf("[BENCHMARK_PRODUCER] Send Exception", e);
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException ignored) {
                            }
                        } catch (InterruptedException e) {
                            statsBenchmark.getSendRequestFailedCount().increment();
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e1) {
                            }
                        } catch (MQClientException e) {
                            statsBenchmark.getSendRequestFailedCount().increment();
                            System.out.printf("[BENCHMARK_PRODUCER] Send Exception", e);
                        } catch (MQBrokerException e) {
                            statsBenchmark.getReceiveResponseFailedCount().increment();
                            System.out.printf("[BENCHMARK_PRODUCER] Send Exception", e);
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException ignored) {
                            }
                        }
                        if (messageNum > 0 && ++num >= msgNumLimit) {
                            break;
                        }
                    }
                }
            });
        }
        try {
            sendThreadPool.shutdown();
            sendThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            executorService.shutdown();
            try {
                executorService.awaitTermination(5000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }

            if (snapshotList.size() > 1) {
                doPrintStats(snapshotList, statsBenchmark, true);
            } else {
                System.out.printf("[Complete] Send Total: %d Send Failed: %d Response Failed: %d%n",
                        statsBenchmark.getSendRequestSuccessCount().longValue() + statsBenchmark.getSendRequestFailedCount().longValue(),
                        statsBenchmark.getSendRequestFailedCount().longValue(), statsBenchmark.getReceiveResponseFailedCount().longValue());
            }
            producer.shutdown();
        } catch (InterruptedException e) {
            System.out.printf("[Exit] Thread Interrupted Exception", e);
        }
    }

    private static void updateStatsSuccess(StatsBenchmarkProducer statsBenchmark, long beginTimestamp) {
        statsBenchmark.getSendRequestSuccessCount().increment();
        statsBenchmark.getReceiveResponseSuccessCount().increment();
        final long currentRT = System.currentTimeMillis() - beginTimestamp;
        statsBenchmark.getSendMessageSuccessTimeTotal().add(currentRT);
        long prevMaxRT = statsBenchmark.getSendMessageMaxRT().longValue();
        while (currentRT > prevMaxRT) {
            boolean updated = statsBenchmark.getSendMessageMaxRT().compareAndSet(prevMaxRT, currentRT);
            if (updated)
                break;

            prevMaxRT = statsBenchmark.getSendMessageMaxRT().longValue();
        }
    }

    private static Message buildMessage(final String topic) {
        return new Message(topic, msgBody);
    }

    private static void doPrintStats(final LinkedList<Long[]> snapshotList, final StatsBenchmarkProducer statsBenchmark, boolean done) {
        Long[] begin = snapshotList.getFirst();
        Long[] end = snapshotList.getLast();

        final long sendTps = (long) (((end[3] - begin[3]) / (double) (end[0] - begin[0])) * 1000L);
        final double averageRT = (end[5] - begin[5]) / (double) (end[3] - begin[3]);

        if (done) {
            System.out.printf("[Complete] Send Total: %d | Send TPS: %d | Max RT(ms): %d | Average RT(ms): %7.3f | Send Failed: %d | Response Failed: %d%n",
                    statsBenchmark.getSendRequestSuccessCount().longValue() + statsBenchmark.getSendRequestFailedCount().longValue(),
                    sendTps, statsBenchmark.getSendMessageMaxRT().longValue(), averageRT, end[2], end[4]);
        } else {
            System.out.printf("Current Time: %s | Send TPS: %d | Max RT(ms): %d | Average RT(ms): %7.3f | Send Failed: %d | Response Failed: %d%n",
                    UtilAll.timeMillisToHumanString2(System.currentTimeMillis()), sendTps, statsBenchmark.getSendMessageMaxRT().longValue(), averageRT, end[2], end[4]);
        }
    }
}

class StatsBenchmarkProducer {
    private final LongAdder sendRequestSuccessCount = new LongAdder();

    private final LongAdder sendRequestFailedCount = new LongAdder();

    private final LongAdder receiveResponseSuccessCount = new LongAdder();

    private final LongAdder receiveResponseFailedCount = new LongAdder();

    private final LongAdder sendMessageSuccessTimeTotal = new LongAdder();

    private final AtomicLong sendMessageMaxRT = new AtomicLong(0L);

    public Long[] createSnapshot() {
        Long[] snap = new Long[] {
                System.currentTimeMillis(),
                this.sendRequestSuccessCount.longValue(),
                this.sendRequestFailedCount.longValue(),
                this.receiveResponseSuccessCount.longValue(),
                this.receiveResponseFailedCount.longValue(),
                this.sendMessageSuccessTimeTotal.longValue(),
        };

        return snap;
    }

    public LongAdder getSendRequestSuccessCount() {
        return sendRequestSuccessCount;
    }

    public LongAdder getSendRequestFailedCount() {
        return sendRequestFailedCount;
    }

    public LongAdder getReceiveResponseSuccessCount() {
        return receiveResponseSuccessCount;
    }

    public LongAdder getReceiveResponseFailedCount() {
        return receiveResponseFailedCount;
    }

    public LongAdder getSendMessageSuccessTimeTotal() {
        return sendMessageSuccessTimeTotal;
    }

    public AtomicLong getSendMessageMaxRT() {
        return sendMessageMaxRT;
    }
}
