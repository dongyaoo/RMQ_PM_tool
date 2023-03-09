<a name="pmFVo"></a>
### Introduction
For my friends, you can get more information about this tool such as how to use it to do test plans in my blog at ZhiHu, here is the site: [程序员小光的主页](https://www.zhihu.com/people/dongyaoo_HIT).
<a name="R12Hj"></a>
### What is RocketMQ
[Apache RocketMQ](https://rocketmq.apache.org/)** is a distributed messaging and streaming platform with low latency, high performance and reliability, trillion-level capacity and flexible scalability.**<br />It offers a variety of features:

- Messaging patterns including publish/subscribe, request/reply and streaming
- Financial grade transactional message
- Built-in fault tolerance and high availability configuration options base on [DLedger Controller](https://github.com/apache/rocketmq/blob/develop/docs/en/controller/quick_start.md)
- Built-in message tracing capability, also support opentracing
- Versatile big-data and streaming ecosystem integration
- Message retroactivity by time or offset
- Reliable FIFO and strict ordered messaging in the same queue
- Efficient pull and push consumption model
- Million-level message accumulation capacity in a single queue
- Multiple messaging protocols like gRPC, MQTT, JMS and OpenMessaging
- Flexible distributed scale-out deployment architecture
- Lightning-fast batch message exchange system
- Various message filter mechanics such as SQL and Tag
- Docker images for isolated testing and cloud isolated clusters
- Feature-rich administrative dashboard for configuration, metrics and monitoring
- Authentication and authorization
- Free open source connectors, for both sources and sinks
- Lightweight real-time computing
  <a name="P3cA6"></a>
### About me
**This Repository based on RocketMQ Benchmark to start a series of test plans for performance pressure mesurement. I just detached the core logic from the official demo and encapsulated some scripts which could make users easier to use in local environment.**
<a name="eivF3"></a>
### Quick Start
> This part guides you how to run RMQ_PM_tool.

1  First, you'd better run init.sh in the beginning.
```shell
cd shell
sh init.sh
```
2  After that, you'll get a executable jar package which name is`loadtest-1.0-SNAPSHOT-executable.jar`in directory`loadtest/target`.<br />3  modify the `commonProducer.sh`.
```shell
# 普通消息发送
# send common message
 FILE=Producer

# rocketmq namesrv address，更改为您的 nameserver 地址
# rocketmq namesrv address，please change to your nameserver address
 NAMESRV_ADDR=12.34.56.78:9876

# rocketmq topic，确保您的是 TP_LOADTEST_COMMON
# rocketmq topic，make sure yours is: TP_LOADTEST_COMMON
 TOPIC="TP_LOADTEST_COMMON"

# 消息大小设为512*2=1024Byte=1kb
# set message size as 512*2= 1024Byte= 1kb
 MESSAGE_SIZE=512

# 生产者并发线程数
# producer thread count
 THREAD_COUNT=64

java -Xms4g -Xmx4g -Xmn2g -jar ../target/loadtest-1.0-SNAPSHOT-executable.jar -x ${FILE} -n ${NAMESRV_ADDR} -t ${TOPIC} -s ${MESSAGE_SIZE} -c ${THREAD_COUNT}

# 更改 JVM 参数使用以下命令
# use below command to adjust JVM parameters
# java -Xms8g -Xmx8g -Xmn4g -jar ../target/loadtest-1.0-SNAPSHOT-executable.jar -x ${FILE} -n ${NAMESRV_ADDR} -t ${TOPIC} -s ${MESSAGE_SIZE} -c ${THREAD_COUNT}
```
4 Just let the shell go on!
```shell
sh commonProducer.sh
```
<a name="hyYew"></a>
### Results
![image.png](https://intranetproxy.alipay.com/skylark/lark/0/2023/png/25856461/1678363510775-f28aa161-2995-48fa-afbe-51f5c8fb1eb7.png#clientId=ua5ada232-4322-4&from=paste&height=111&id=uac26e3b0&name=image.png&originHeight=221&originWidth=1500&originalType=binary&ratio=2&rotation=0&showTitle=false&size=241798&status=done&style=none&taskId=uba7b7837-084c-46f8-8e6d-0b1a2277bd2&title=&width=750)
