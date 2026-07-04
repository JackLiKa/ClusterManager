package com.example.clustermanager.infrastructure.pseudo;

import com.example.clustermanager.core.model.MessageDeliveryResult;
import com.example.clustermanager.core.model.MessageScenario;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;

@Component
public class PseudoRocketMqBridge {

    public List<MessageDeliveryResult> simulateWithHostRocketMq(
            String namesrvAddr,
            MessageScenario scenario,
            List<String> consumerNodeIds
    ) {
        try {
            return doSimulate(namesrvAddr, scenario, consumerNodeIds);
        } catch (Exception exception) {
            return consumerNodeIds.stream()
                    .map(consumerNodeId -> new MessageDeliveryResult(
                            "host-bridge-failed",
                            scenario.producerNodeId(),
                            consumerNodeId,
                            false,
                            "Host RocketMQ bridge failed: " + exception.getMessage()
                    ))
                    .toList();
        }
    }

    private List<MessageDeliveryResult> doSimulate(
            String namesrvAddr,
            MessageScenario scenario,
            List<String> consumerNodeIds
    ) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer("cluster-manager-producer");
        producer.setNamesrvAddr(namesrvAddr);
        producer.start();

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(scenario.consumerGroup());
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.subscribe(scenario.topic(), "*");

        List<String> messageKeys = new ArrayList<>();
        Map<String, Boolean> consumptionState = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(scenario.messageCount());
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            for (MessageExt message : messages) {
                String key = message.getKeys();
                if (key != null && consumptionState.putIfAbsent(key, true) == null) {
                    latch.countDown();
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();

        try {
            List<MessageDeliveryResult> deliveries = new ArrayList<>();
            for (int index = 0; index < scenario.messageCount(); index++) {
                String messageKey = scenario.topic() + "-host-" + index;
                messageKeys.add(messageKey);
                String consumerNodeId = consumerNodeIds.get(index % consumerNodeIds.size());
                Message message = new Message(
                        scenario.topic(),
                        scenario.payloadTemplate() == null ? "{}".getBytes(StandardCharsets.UTF_8) : scenario.payloadTemplate().getBytes(StandardCharsets.UTF_8)
                );
                message.setKeys(messageKey);
                SendResult sendResult = producer.send(message);

                deliveries.add(new MessageDeliveryResult(
                        messageKey,
                        scenario.producerNodeId(),
                        consumerNodeId,
                        false,
                        "Sent to host RocketMQ via " + namesrvAddr + ", msgId=" + sendResult.getMsgId()
                ));
            }

            latch.await(Math.min(30, Math.max(5, scenario.messageCount())), TimeUnit.SECONDS);
            return deliveries.stream()
                    .map(delivery -> {
                        boolean consumed = Boolean.TRUE.equals(consumptionState.get(delivery.messageKey()));
                        String detail = consumed
                                ? "Delivered through host RocketMQ via " + namesrvAddr
                                : "Sent to host RocketMQ but did not confirm consumption before timeout";
                        return new MessageDeliveryResult(
                                delivery.messageKey(),
                                delivery.producerNodeId(),
                                delivery.consumerNodeId(),
                                consumed,
                                detail
                        );
                    })
                    .toList();
        } finally {
            shutdownQuietly(producer, consumer);
        }
    }

    private void shutdownQuietly(DefaultMQProducer producer, DefaultMQPushConsumer consumer) {
        try {
            producer.shutdown();
        } catch (Exception ignored) {
        }
        try {
            consumer.shutdown();
        } catch (Exception ignored) {
        }
    }
}
