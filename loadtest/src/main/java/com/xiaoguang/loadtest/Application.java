package com.xiaoguang.loadtest;

import com.xiaoguang.loadtest.utils.AclClient;
import com.xiaoguang.loadtest.normal.Consumer;
import com.xiaoguang.loadtest.normal.Producer;
import com.xiaoguang.loadtest.transaction.TxProducer;
import com.xiaoguang.loadtest.utils.ServerUtil;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.SerializeType;

import java.io.IOException;


public class Application {
    public static void main(String[] args) throws IOException, MQClientException {
        System.setProperty(RemotingCommand.SERIALIZE_TYPE_PROPERTY, SerializeType.ROCKETMQ.name());
        Options options = ServerUtil.buildCommandlineOptions(new Options());
        CommandLine commandLine = ServerUtil.parseCmdLine("benchmarkProducer", args, buildCommandlineOptions(options), new DefaultParser());
        if (null == commandLine) {
            System.exit(-1);
        }
        // producer params
        final String xfile = commandLine.hasOption('x') ? commandLine.getOptionValue('x').trim() : "Producer";
        final String namesrv = commandLine.hasOption('n') ? commandLine.getOptionValue('n').trim() : "localhost:9876";
        final String topic = commandLine.hasOption('t') ? commandLine.getOptionValue('t').trim() : "BenchmarkTest";
        final int messageSize = commandLine.hasOption('s') ? Integer.parseInt(commandLine.getOptionValue('s')) : 128;
        final boolean keyEnable = commandLine.hasOption('k') && Boolean.parseBoolean(commandLine.getOptionValue('k'));
        final int propertySize = commandLine.hasOption('p') ? Integer.parseInt(commandLine.getOptionValue('p')) : 0;
        final int tagCount = commandLine.hasOption('l') ? Integer.parseInt(commandLine.getOptionValue('l')) : 0;
        final boolean msgTraceEnable = commandLine.hasOption('m') && Boolean.parseBoolean(commandLine.getOptionValue('m'));
        final boolean aclEnable = commandLine.hasOption('a') && Boolean.parseBoolean(commandLine.getOptionValue('a'));
        final long messageNum = commandLine.hasOption('q') ? Long.parseLong(commandLine.getOptionValue('q')) : 0;
        final boolean delayEnable = commandLine.hasOption('d') && Boolean.parseBoolean(commandLine.getOptionValue('d'));
        final int delayLevel = commandLine.hasOption('e') ? Integer.parseInt(commandLine.getOptionValue('e')) : 1;
        final boolean asyncEnable = commandLine.hasOption('y') && Boolean.parseBoolean(commandLine.getOptionValue('y'));
        final int threadCount = asyncEnable ? 1 : commandLine.hasOption('c') ? Integer.parseInt(commandLine.getOptionValue('c')) : 64;

        final double sendRollbackRate = commandLine.hasOption("sr") ? Double.parseDouble(commandLine.getOptionValue("sr")) : 0.0;
        final double sendUnknownRate = commandLine.hasOption("su") ? Double.parseDouble(commandLine.getOptionValue("su")) : 0.0;
        final double checkRollbackRate = commandLine.hasOption("cr") ? Double.parseDouble(commandLine.getOptionValue("cr")) : 0.0;
        final double checkUnknownRate = commandLine.hasOption("cu") ? Double.parseDouble(commandLine.getOptionValue("cu")) : 0.0;
        final long batchId = commandLine.hasOption("batchId") ? Long.parseLong(commandLine.getOptionValue("batchId")) : System.currentTimeMillis();
        final int sendInterval = commandLine.hasOption("interval") ? Integer.parseInt(commandLine.getOptionValue("interval")) : 0;

        // common params
        final String ak = commandLine.hasOption("ak") ? String.valueOf(commandLine.getOptionValue("ak")) : AclClient.ACL_ACCESS_KEY;
        final String sk = commandLine.hasOption("sk") ? String.valueOf(commandLine.getOptionValue("sk")) : AclClient.ACL_SECRET_KEY;

        // consumer params
        final String groupId = commandLine.hasOption('g') ? commandLine.getOptionValue('g').trim() : "benchmark_consumer";
        final String isSuffixEnable = commandLine.hasOption('i') ? commandLine.getOptionValue('i').trim() : "false";
        final String filterType = commandLine.hasOption('f') ? commandLine.getOptionValue('f').trim() : null;
        final String expression = commandLine.hasOption("expre") ? commandLine.getOptionValue("expre").trim() : null;
        final double failRate = commandLine.hasOption('r') ? Double.parseDouble(commandLine.getOptionValue('r').trim()) : 0.0;
        // rocket 5.x 支持 clientRebalance
        // final boolean clientRebalanceEnable = commandLine.hasOption('c') ? Boolean.parseBoolean(commandLine.getOptionValue('c')) : true;
        final boolean enableBroadcast = commandLine.hasOption('b') && Boolean.parseBoolean(commandLine.getOptionValue('b').trim());
        // 设置消费点位，即从 message queue 的哪个 OFFSET 开始消费：CONSUME_FROM_LAST_OFFSET(从上次消费的偏移量开始接着消费), CONSUME_FROM_FIRST_OFFSET(从历史消息开始消费), CONSUME_FROM_TIMESTAMP(从指定时刻开始消费);
        final String consumeFromWhere = commandLine.hasOption('w') ? commandLine.getOptionValue('w').trim() : "CONSUME_FROM_LAST_OFFSET";

        if (xfile.equals("Producer")){
            String[] params = {namesrv, topic, String.valueOf(messageSize), String.valueOf(keyEnable), String.valueOf(propertySize), String.valueOf(tagCount), String.valueOf(msgTraceEnable), String.valueOf(aclEnable), String.valueOf(messageNum), String.valueOf(delayEnable), String.valueOf(delayLevel), String.valueOf(asyncEnable), String.valueOf(threadCount), ak, sk};
            Producer.main(params);
        } else if (xfile.equals("Consumer")){
            String[] params = {namesrv, topic, String.valueOf(threadCount), groupId, isSuffixEnable, filterType, expression, String.valueOf(failRate), String.valueOf(msgTraceEnable), String.valueOf(aclEnable), ak, sk, String.valueOf(enableBroadcast), consumeFromWhere};
            Consumer.main(params);
        } else if (xfile.equals("TxProducer")){
            String[] params = {namesrv, topic, String.valueOf(threadCount), String.valueOf(messageSize), String.valueOf(sendRollbackRate), String.valueOf(sendUnknownRate), String.valueOf(checkRollbackRate), String.valueOf(checkUnknownRate), String.valueOf(batchId), String.valueOf(sendInterval), String.valueOf(aclEnable), ak, sk};
            TxProducer.main(params);
        }

    }

    public static Options buildCommandlineOptions(final Options options) {
        Option opt = new Option("c", "threadCount", true, "Thread count, Default: 64");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("s", "messageSize", true, "Message Size, Default: 128");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("k", "keyEnable", true, "Message Key Enable, Default: false");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("t", "topic", true, "Topic name, Default: BenchmarkTest");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("l", "tagCount", true, "Tag count, Default: 0");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("m", "msgTraceEnable", true, "Message Trace Enable, Default: false");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("a", "aclEnable", true, "Acl Enable, Default: false");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("ak", "accessKey", true, "Acl access key, Default: 12345678");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("sk", "secretKey", true, "Acl secret key, Default: rocketmq2");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("q", "messageQuantity", true, "Send message quantity, Default: 0, running forever");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("d", "delayEnable", true, "Delay message Enable, Default: false");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("e", "delayLevel", true, "Delay message level, Default: 1");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("y", "asyncEnable", true, "Enable async produce, Default: false");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("n", "namesrv", true, "Name server address list, Default: localhost:9876");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("x", "xfile", true, "specify file to execute, Default: Producer");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("n", "namesrv", true, "Name server address list, Default: localhost:9876");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("ak", "accessKey", true, "Access Key, Default: rocketmq2");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("sk", "secretKey", true, "Secret Key, Default: 12345678");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("g", "groupId", true, "Consumer Group ID, Default: benchmark_consumer");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("i", "isSuffixEnable", true, "is add suffix to groupId automatically, Default: false");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("f", "filterType", true, "message filter type, Default: null");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("expre", "filterExpression", true, "message filter expression, Default: null");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("r", "failRate", true, "consume fail rate, Default: 0.0");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("c", "clientRebalanceEnable", true, "enable client rebalance, Default: true");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("b", "enableBroadcast", true, "enable broadcast consume, Default: false");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("w", "consumeFromWhere", true, "set consume from which offset in message queue, Default: CONSUME_FROM_LAST_OFFSET");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("sr", "send rollback rate", true, "Send rollback rate, Default: 0.0");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("su", "send unknown rate", true, "Send unknown rate, Default: 0.0");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("cr", "check rollback rate", true, "Check rollback rate, Default: 0.0");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("cu", "check unknown rate", true, "Check unknown rate, Default: 0.0");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("batchId", "test batch id", true, "test batch id, Default: System.currentMillis()");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("interval", "send interval", true, "sleep interval in millis between messages, Default: 0");
        opt.setRequired(false);
        options.addOption(opt);

        return options;
    }
}
