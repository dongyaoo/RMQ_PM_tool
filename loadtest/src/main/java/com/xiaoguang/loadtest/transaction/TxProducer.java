package com.xiaoguang.loadtest.transaction;

import com.xiaoguang.loadtest.utils.AclClient;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.RPCHook;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class TxProducer {
    private static final long START_TIME = System.currentTimeMillis();
    private static final LongAdder MSG_COUNT = new LongAdder();

    //broker max check times should less than this value
    static final int MAX_CHECK_RESULT_IN_MSG = 20;

    public static void main(String[] args) throws MQClientException, UnsupportedEncodingException {
        TxSendConfig config = new TxSendConfig();
        config.namesrv=args[0];
        config.topic = args[1];
        config.threadCount = Integer.parseInt(args[2]);
        config.messageSize = Integer.parseInt(args[3]);
        config.sendRollbackRate = Double.parseDouble(args[4]);
        config.sendUnknownRate = Double.parseDouble(args[5]);
        config.checkRollbackRate = Double.parseDouble(args[6]);
        config.checkUnknownRate = Double.parseDouble(args[7]);
        config.batchId = Long.parseLong(args[8]);
        config.sendInterval = Integer.parseInt(args[9]);
        config.aclEnable = Boolean.parseBoolean(args[10]);
        config.ak=args[11];
        config.sk=args[12];

        final ExecutorService sendThreadPool = Executors.newFixedThreadPool(config.threadCount);

        final StatsBenchmarkTProducer statsBenchmark = new StatsBenchmarkTProducer();

        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1,
                new BasicThreadFactory.Builder().namingPattern("BenchmarkTimerThread-%d").daemon(true).build());

        final LinkedList<Snapshot> snapshotList = new LinkedList<>();

        executorService.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                snapshotList.addLast(statsBenchmark.createSnapshot());
                while (snapshotList.size() > 10) {
                    snapshotList.removeFirst();
                }
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);

        executorService.scheduleAtFixedRate(new TimerTask() {
            private void printStats() {
                if (snapshotList.size() >= 10) {
                    Snapshot begin = snapshotList.getFirst();
                    Snapshot end = snapshotList.getLast();

                    final long sendCount = (end.sendRequestSuccessCount - begin.sendRequestSuccessCount)
                            + (end.sendRequestFailedCount - begin.sendRequestFailedCount);
                    final long sendTps = (sendCount * 1000L) / (end.endTime - begin.endTime);
                    final double averageRT = (end.sendMessageTimeTotal - begin.sendMessageTimeTotal) / (double) (end.sendRequestSuccessCount - begin.sendRequestSuccessCount);

                    final long failCount = end.sendRequestFailedCount - begin.sendRequestFailedCount;
                    final long checkCount = end.checkCount - begin.checkCount;
                    final long unexpectedCheck = end.unexpectedCheckCount - begin.unexpectedCheckCount;
                    final long dupCheck = end.duplicatedCheck - begin.duplicatedCheck;

                    System.out.printf(
                            "Current Time: %s | Send TPS: %5d | Max RT(ms): %5d | AVG RT(ms): %3.1f | Send Failed: %d | Check: %d | UnexpectedCheck: %d | DuplicatedCheck: %d%n",
                            UtilAll.timeMillisToHumanString2(System.currentTimeMillis()), sendTps, statsBenchmark.getSendMessageMaxRT().get(), averageRT, failCount, checkCount,
                            unexpectedCheck, dupCheck);
                    statsBenchmark.getSendMessageMaxRT().set(0);
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
        if (config.aclEnable) {
            rpcHook = AclClient.getAclRPCHook(config.ak, config.sk);
        }
        final TransactionListener transactionCheckListener = new TransactionListenerImpl(statsBenchmark, config);
        final TransactionMQProducer producer = new TransactionMQProducer(
                null,
                "benchmark_transaction_producer",
                rpcHook);
        producer.setInstanceName(Long.toString(System.currentTimeMillis()));
        producer.setTransactionListener(transactionCheckListener);
        producer.setDefaultTopicQueueNums(1000);
        producer.setNamesrvAddr(config.namesrv);
        producer.start();

        for (int i = 0; i < config.threadCount; i++) {
            sendThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        boolean success = false;
                        final long beginTimestamp = System.currentTimeMillis();
                        try {
                            SendResult sendResult =
                                    producer.sendMessageInTransaction(buildMessage(config), null);
                            success = sendResult != null && sendResult.getSendStatus() == SendStatus.SEND_OK;
                        } catch (Throwable e) {
                            success = false;
                        } finally {
                            final long currentRT = System.currentTimeMillis() - beginTimestamp;
                            statsBenchmark.getSendMessageTimeTotal().add(currentRT);
                            long prevMaxRT = statsBenchmark.getSendMessageMaxRT().get();
                            while (currentRT > prevMaxRT) {
                                boolean updated = statsBenchmark.getSendMessageMaxRT()
                                        .compareAndSet(prevMaxRT, currentRT);
                                if (updated)
                                    break;

                                prevMaxRT = statsBenchmark.getSendMessageMaxRT().get();
                            }
                            if (success) {
                                statsBenchmark.getSendRequestSuccessCount().increment();
                            } else {
                                statsBenchmark.getSendRequestFailedCount().increment();
                            }
                            if (config.sendInterval > 0) {
                                try {
                                    Thread.sleep(config.sendInterval);
                                } catch (InterruptedException e) {
                                }
                            }
                        }
                    }
                }
            });
        }
    }

    private static Message buildMessage(TxSendConfig config) {
        byte[] bs = new byte[config.messageSize];
        ThreadLocalRandom r = ThreadLocalRandom.current();
        r.nextBytes(bs);

        ByteBuffer buf = ByteBuffer.wrap(bs);
        buf.putLong(config.batchId);
        long sendMachineId = START_TIME << 32;
        long count = MSG_COUNT.longValue();
        long msgId = sendMachineId | count;
        MSG_COUNT.increment();
        buf.putLong(msgId);

        // save send tx result in message
        if (r.nextDouble() < config.sendRollbackRate) {
            buf.put((byte) LocalTransactionState.ROLLBACK_MESSAGE.ordinal());
        } else if (r.nextDouble() < config.sendUnknownRate) {
            buf.put((byte) LocalTransactionState.UNKNOW.ordinal());
        } else {
            buf.put((byte) LocalTransactionState.COMMIT_MESSAGE.ordinal());
        }

        // save check tx result in message
        for (int i = 0; i < MAX_CHECK_RESULT_IN_MSG; i++) {
            if (r.nextDouble() < config.checkRollbackRate) {
                buf.put((byte) LocalTransactionState.ROLLBACK_MESSAGE.ordinal());
            } else if (r.nextDouble() < config.checkUnknownRate) {
                buf.put((byte) LocalTransactionState.UNKNOW.ordinal());
            } else {
                buf.put((byte) LocalTransactionState.COMMIT_MESSAGE.ordinal());
            }
        }

        Message msg = new Message();
        msg.setTopic(config.topic);

        msg.setBody(bs);
        return msg;
    }

}

class TransactionListenerImpl implements TransactionListener {
    private StatsBenchmarkTProducer statBenchmark;
    private TxSendConfig sendConfig;
    private final LRUMap<Long, Integer> cache = new LRUMap<>(200000);

    private class MsgMeta {
        long batchId;
        long msgId;
        LocalTransactionState sendResult;
        List<LocalTransactionState> checkResult;
    }

    public TransactionListenerImpl(StatsBenchmarkTProducer statsBenchmark, TxSendConfig sendConfig) {
        this.statBenchmark = statsBenchmark;
        this.sendConfig = sendConfig;
    }

    /**
     * 执行本地事务
     * @param msg
     * @param arg
     * @return 成功返回 COMMIT_MESSAGE，触发 broker 投递；失败返回 ROLLBACK_MESSAGE，触发 broker 删除消息，否则为 UNKNOWN
     */

    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        return parseFromMsg(msg).sendResult;
    }

    private MsgMeta parseFromMsg(Message msg) {
        byte[] bs = msg.getBody();
        ByteBuffer buf = ByteBuffer.wrap(bs);
        MsgMeta msgMeta = new MsgMeta();
        // getlong 方法返回缓冲区当前位置的 long 值。
        msgMeta.batchId = buf.getLong();
        msgMeta.msgId = buf.getLong();
        msgMeta.sendResult = LocalTransactionState.values()[buf.get()];
        msgMeta.checkResult = new ArrayList<>();
        for (int i = 0; i < TxProducer.MAX_CHECK_RESULT_IN_MSG; i++) {
            msgMeta.checkResult.add(LocalTransactionState.values()[buf.get()]);
        }
        return msgMeta;
    }

    /**
     * broker 长时间未收到事务消息会回调该方法查看本地事务执行状态
     * @param msg
     * @return 成功返回 COMMIT_MESSAGE，触发 broker 投递；失败返回 ROLLBACK_MESSAGE，触发 broker 删除消息，否则为 UNKNOWN
     */
    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        MsgMeta msgMeta = parseFromMsg(msg);
        // 由于执行本地事务时会解析事务消息，生成对应的元数据(batchId会被更新)，所以如果回查时的 batchId 不一致，说明本地事务出错。
        if (msgMeta.batchId != sendConfig.batchId) {
            // message not generated in this test
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
        // 回查次数自增
        statBenchmark.getCheckCount().increment();

        int times = 0;
        try {
            String checkTimes = msg.getUserProperty(MessageConst.PROPERTY_TRANSACTION_CHECK_TIMES);
            times = Integer.parseInt(checkTimes);
        } catch (Exception e) {
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
        times = times <= 0 ? 1 : times;

        boolean dup;
        synchronized (cache) {
            Integer oldCheckLog = cache.get(msgMeta.msgId);
            Integer newCheckLog;
            if (oldCheckLog == null) {
                newCheckLog = 1 << (times - 1);
            } else {
                newCheckLog = oldCheckLog | (1 << (times - 1));
            }
            dup = newCheckLog.equals(oldCheckLog);
        }
        if (dup) {
            statBenchmark.getDuplicatedCheckCount().increment();
        }
        if (msgMeta.sendResult != LocalTransactionState.UNKNOW) {
            System.out.printf("%s unexpected check: msgId=%s,txId=%s,checkTimes=%s,sendResult=%s\n",
                    new SimpleDateFormat("HH:mm:ss,SSS").format(new Date()),
                    msg.getMsgId(), msg.getTransactionId(),
                    msg.getUserProperty(MessageConst.PROPERTY_TRANSACTION_CHECK_TIMES),
                    msgMeta.sendResult.toString());
            statBenchmark.getUnexpectedCheckCount().increment();
            return msgMeta.sendResult;
        }

        for (int i = 0; i < times - 1; i++) {
            LocalTransactionState s = msgMeta.checkResult.get(i);
            if (s != LocalTransactionState.UNKNOW) {
                System.out.printf("%s unexpected check: msgId=%s,txId=%s,checkTimes=%s,sendResult,lastCheckResult=%s\n",
                        new SimpleDateFormat("HH:mm:ss,SSS").format(new Date()),
                        msg.getMsgId(), msg.getTransactionId(),
                        msg.getUserProperty(MessageConst.PROPERTY_TRANSACTION_CHECK_TIMES), s);
                statBenchmark.getUnexpectedCheckCount().increment();
                return s;
            }
        }
        return msgMeta.checkResult.get(times - 1);
    }
}

class Snapshot {
    long endTime;

    long sendRequestSuccessCount;

    long sendRequestFailedCount;

    long sendMessageTimeTotal;

    long sendMessageMaxRT;

    long checkCount;

    long unexpectedCheckCount;

    long duplicatedCheck;
}

class StatsBenchmarkTProducer {
    private final LongAdder sendRequestSuccessCount = new LongAdder();

    private final LongAdder sendRequestFailedCount = new LongAdder();

    private final LongAdder sendMessageTimeTotal = new LongAdder();

    private final AtomicLong sendMessageMaxRT = new AtomicLong(0L);

    private final LongAdder checkCount = new LongAdder();

    private final LongAdder unexpectedCheckCount = new LongAdder();

    private final LongAdder duplicatedCheckCount = new LongAdder();

    public Snapshot createSnapshot() {
        Snapshot s = new Snapshot();
        s.endTime = System.currentTimeMillis();
        s.sendRequestSuccessCount = sendRequestSuccessCount.longValue();
        s.sendRequestFailedCount = sendRequestFailedCount.longValue();
        s.sendMessageTimeTotal = sendMessageTimeTotal.longValue();
        s.sendMessageMaxRT = sendMessageMaxRT.get();
        s.checkCount = checkCount.longValue();
        s.unexpectedCheckCount = unexpectedCheckCount.longValue();
        s.duplicatedCheck = duplicatedCheckCount.longValue();
        return s;
    }

    public LongAdder getSendRequestSuccessCount() {
        return sendRequestSuccessCount;
    }

    public LongAdder getSendRequestFailedCount() {
        return sendRequestFailedCount;
    }

    public LongAdder getSendMessageTimeTotal() {
        return sendMessageTimeTotal;
    }

    public AtomicLong getSendMessageMaxRT() {
        return sendMessageMaxRT;
    }

    public LongAdder getCheckCount() {
        return checkCount;
    }

    public LongAdder getUnexpectedCheckCount() {
        return unexpectedCheckCount;
    }

    public LongAdder getDuplicatedCheckCount() {
        return duplicatedCheckCount;
    }
}

class TxSendConfig {
    String namesrv;
    String topic;
    int threadCount;
    int messageSize;
    double sendRollbackRate;
    double sendUnknownRate;
    double checkRollbackRate;
    double checkUnknownRate;
    long batchId;
    int sendInterval;
    boolean aclEnable;
    String ak;
    String sk;
}

class LRUMap<K, V> extends LinkedHashMap<K, V> {

    private int maxSize;

    public LRUMap(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry eldest) {
        return size() > maxSize;
    }
}
