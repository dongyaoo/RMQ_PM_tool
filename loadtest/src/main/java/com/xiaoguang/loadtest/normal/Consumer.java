package com.xiaoguang.loadtest.normal;

import com.xiaoguang.loadtest.utils.AclClient;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.MessageSelector;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.filter.ExpressionType;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.remoting.RPCHook;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class Consumer {

    public static void main(String[] args) throws IOException, org.apache.rocketmq.client.exception.MQClientException, MQClientException {

        final String namesrv = args[0];
        final String topic = args[1];
        final int threadCount = Integer.parseInt(args[2]);
        final String groupPrefix = args[3];
        final String isSuffixEnable = args[4];
        final String filterType = args[5];
        final String expression = args[6];
        final double failRate = Double.parseDouble(args[7]);
        final boolean msgTraceEnable = Boolean.parseBoolean(args[8]);
        final boolean aclEnable = Boolean.parseBoolean(args[9]);
        final String ak = args[10];
        final String sk = args[11];
        final boolean enableBroadcast = Boolean.parseBoolean(args[12]);
        final String consumeFromWhere = args[13];

        String group = groupPrefix;
        if (Boolean.parseBoolean(isSuffixEnable)) {
            group = groupPrefix + "_" + (System.currentTimeMillis() % 100);
        }

        System.out.printf("enableBroadcast: %s, topic: %s, threadCount %d, group: %s, suffix: %s, filterType: %s, expression: %s, msgTraceEnable: %s, aclEnable: %s%n",
                enableBroadcast, topic, threadCount, group, isSuffixEnable, filterType, expression, msgTraceEnable, aclEnable);

        final StatsBenchmarkConsumer statsBenchmarkConsumer = new StatsBenchmarkConsumer();

        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1,
                new BasicThreadFactory.Builder().namingPattern("BenchmarkTimerThread-%d").daemon(true).build());

        final LinkedList<Long[]> snapshotList = new LinkedList<>();

        executorService.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                snapshotList.addLast(statsBenchmarkConsumer.createSnapshot());
                if (snapshotList.size() > 10) {
                    snapshotList.removeFirst();
                }
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);

        executorService.scheduleAtFixedRate(new TimerTask() {
            private void printStats() {
                if (snapshotList.size() >= 10) {
                    Long[] begin = snapshotList.getFirst();
                    Long[] end = snapshotList.getLast();

                    final long consumeTps =
                            (long) (((end[1] - begin[1]) / (double) (end[0] - begin[0])) * 1000L);
                    final double averageB2CRT = (end[2] - begin[2]) / (double) (end[1] - begin[1]);
                    final double averageS2CRT = (end[3] - begin[3]) / (double) (end[1] - begin[1]);
                    final long failCount = end[4] - begin[4];
                    final long b2cMax = statsBenchmarkConsumer.getBorn2ConsumerMaxRT().get();
                    final long s2cMax = statsBenchmarkConsumer.getStore2ConsumerMaxRT().get();

                    statsBenchmarkConsumer.getBorn2ConsumerMaxRT().set(0);
                    statsBenchmarkConsumer.getStore2ConsumerMaxRT().set(0);

                    System.out.printf("Current Time: %s | Consume TPS: %d | AVG(B2C) RT(ms): %7.3f | AVG(S2C) RT(ms): %7.3f | MAX(B2C) RT(ms): %d | MAX(S2C) RT(ms): %d | Consume Fail: %d%n",
                            UtilAll.timeMillisToHumanString2(System.currentTimeMillis()), consumeTps, averageB2CRT, averageS2CRT, b2cMax, s2cMax, failCount);
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
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group, rpcHook, new AllocateMessageQueueAveragely(), msgTraceEnable, null);
        consumer.setNamesrvAddr(namesrv);
        consumer.setConsumeThreadMin(threadCount);
        consumer.setConsumeThreadMax(threadCount);
        consumer.setInstanceName(Long.toString(System.currentTimeMillis()));
        consumer.setConsumeFromWhere(ConsumeFromWhere.valueOf(consumeFromWhere));
        if (enableBroadcast){
            consumer.setMessageModel(MessageModel.BROADCASTING);
        }

        if (filterType == null || expression == null) {
            consumer.subscribe(topic, "*");
        } else {
            if (ExpressionType.TAG.equals(filterType)) {
                String expr = MixAll.file2String(expression);
                System.out.printf("Expression: %s%n", expr);
                consumer.subscribe(topic, MessageSelector.byTag(expr));
            } else if (ExpressionType.SQL92.equals(filterType)) {
                String expr = MixAll.file2String(expression);
                System.out.printf("Expression: %s%n", expr);
                consumer.subscribe(topic, MessageSelector.bySql(expr));
            } else {
                throw new IllegalArgumentException("Not support filter type! " + filterType);
            }
        }

        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs,
                                                            ConsumeConcurrentlyContext context) {
                MessageExt msg = msgs.get(0);
                long now = System.currentTimeMillis();

                statsBenchmarkConsumer.getReceiveMessageTotalCount().increment();

                long born2ConsumerRT = now - msg.getBornTimestamp();
                statsBenchmarkConsumer.getBorn2ConsumerTotalRT().add(born2ConsumerRT);

                long store2ConsumerRT = now - msg.getStoreTimestamp();
                statsBenchmarkConsumer.getStore2ConsumerTotalRT().add(store2ConsumerRT);

                compareAndSetMax(statsBenchmarkConsumer.getBorn2ConsumerMaxRT(), born2ConsumerRT);

                compareAndSetMax(statsBenchmarkConsumer.getStore2ConsumerMaxRT(), store2ConsumerRT);

                if (ThreadLocalRandom.current().nextDouble() < failRate) {
                    statsBenchmarkConsumer.getFailCount().increment();
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                } else {
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
            }
        });

        consumer.start();

        System.out.printf("Consumer Started.%n");
    }

    public static void compareAndSetMax(final AtomicLong target, final long value) {
        long prev = target.get();
        while (value > prev) {
            boolean updated = target.compareAndSet(prev, value);
            if (updated)
                break;

            prev = target.get();
        }
    }
}

class StatsBenchmarkConsumer {
    private final LongAdder receiveMessageTotalCount = new LongAdder();

    private final LongAdder born2ConsumerTotalRT = new LongAdder();

    private final LongAdder store2ConsumerTotalRT = new LongAdder();

    private final AtomicLong born2ConsumerMaxRT = new AtomicLong(0L);

    private final AtomicLong store2ConsumerMaxRT = new AtomicLong(0L);

    private final LongAdder failCount = new LongAdder();

    public Long[] createSnapshot() {
        Long[] snap = new Long[] {
                System.currentTimeMillis(),
                this.receiveMessageTotalCount.longValue(),
                this.born2ConsumerTotalRT.longValue(),
                this.store2ConsumerTotalRT.longValue(),
                this.failCount.longValue()
        };

        return snap;
    }

    public LongAdder getReceiveMessageTotalCount() {
        return receiveMessageTotalCount;
    }

    public LongAdder getBorn2ConsumerTotalRT() {
        return born2ConsumerTotalRT;
    }

    public LongAdder getStore2ConsumerTotalRT() {
        return store2ConsumerTotalRT;
    }

    public AtomicLong getBorn2ConsumerMaxRT() {
        return born2ConsumerMaxRT;
    }

    public AtomicLong getStore2ConsumerMaxRT() {
        return store2ConsumerMaxRT;
    }

    public LongAdder getFailCount() {
        return failCount;
    }
}
