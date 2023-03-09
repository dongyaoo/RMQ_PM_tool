# 普通消息发送
 FILE=Producer

# rocketmq namesrv address，更改为您的 nameserver 地址
 NAMESRV_ADDR=12.34.56.78:9876
# rocketmq topic，确保您的是 TP_LOADTEST_COMMON
 TOPIC="TP_LOADTEST_COMMON"
# 消息大小设为512*2=1024Byte=1kb
 MESSAGE_SIZE=512
# 生产者并发线程数
 THREAD_COUNT=64

java -Xms4g -Xmx4g -Xmn2g -jar ../target/loadtest-1.0-SNAPSHOT-executable.jar -x ${FILE} -n ${NAMESRV_ADDR} -t ${TOPIC} -s ${MESSAGE_SIZE} -c ${THREAD_COUNT}

# 更改 JVM 参数使用以下命令
# java -Xms8g -Xmx8g -Xmn4g -jar ../target/loadtest-1.0-SNAPSHOT-executable.jar -x ${FILE} -n ${NAMESRV_ADDR} -t ${TOPIC} -s ${MESSAGE_SIZE} -c ${THREAD_COUNT}