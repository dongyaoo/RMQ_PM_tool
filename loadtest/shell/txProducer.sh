# 事务消息发送
 FILE=TxProducer
# 开启事务消息 commit
 SEND_ROLLBACK_RATE=0.0
# 开启事务消息 rollback
# SEND_ROLLBACK_RATE=1.0

# rocketmq namesrv address，更改为您的 nameserver 地址
 NAMESRV_ADDR=12.34.56.78:9876
# rocketmq topic，确保您的是 TP_LOADTEST_COMMON
 TOPIC="TP_LOADTEST_COMMON"
# 消息大小设为512*2=1024Byte=1kb
  MESSAGE_SIZE=512
# 生产者并发线程数
  THREAD_COUNT=64

java -Xms4g -Xmx4g -Xmn2g -jar ../target/loadtest-1.0-SNAPSHOT-executable.jar -x ${FILE} -n ${NAMESRV_ADDR} -t ${TOPIC} -sr ${SEND_ROLLBACK_RATE} -c ${THREAD_COUNT} -s ${MESSAGE_SIZE}

# 更改 JVM 参数使用以下命令
# java -Xms4g -Xmx4g -Xmn2g -jar ../target/loadtest-1.0-SNAPSHOT-executable.jar -x ${FILE} -n ${NAMESRV_ADDR} -t ${TOPIC} -sr ${SEND_ROLLBACK_RATE} -c ${THREAD_COUNT} -s ${MESSAGE_SIZE}