package io.xzxj.canal.spring.autoconfigure;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.FlatMessage;
import com.alibaba.otter.canal.protocol.Message;
import io.xzxj.canal.core.client.AbstractMqCanalClient;
import io.xzxj.canal.core.client.RabbitMqCanalClient;
import io.xzxj.canal.core.factory.EntryColumnConvertFactory;
import io.xzxj.canal.core.factory.MapConvertFactory;
import io.xzxj.canal.core.handler.IMessageHandler;
import io.xzxj.canal.core.handler.RowDataHandler;
import io.xzxj.canal.core.handler.impl.*;
import io.xzxj.canal.core.listener.EntryListener;
import io.xzxj.canal.spring.properties.CanalMqProperties;
import io.xzxj.canal.spring.properties.CanalProperties;
import io.xzxj.canal.spring.properties.CanalRabbitMqProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * @author xzxj
 * @date 2023/3/24 9:49
 */
@EnableConfigurationProperties(CanalProperties.class)
@ConditionalOnProperty(value = "canal.server-mode", havingValue = "rabbit_mq")
@Import(ThreadPoolAutoConfiguration.class)
public class RabbitMqClientAutoConfiguration {

    private final CanalProperties canalProperties;

    public RabbitMqClientAutoConfiguration(CanalProperties canalProperties) {
        this.canalProperties = canalProperties;
    }

    @Bean("rowDataHandler")
    @ConditionalOnMissingBean(RowDataHandler.class)
    @ConditionalOnProperty(value = "canal.mq.flat-message", havingValue = "false")
    public RowDataHandler<CanalEntry.RowData> messageRowDataHandler() {
        return new RowDataHandlerImpl(new EntryColumnConvertFactory());
    }

    @Bean
    @ConditionalOnMissingBean(IMessageHandler.class)
    @ConditionalOnExpression("${canal.async:true} and ${canal.mq.flat-message:true} == false")
    public IMessageHandler<Message> asyncMessageHandler(RowDataHandler<CanalEntry.RowData> rowDataHandler,
                                                        List<EntryListener<?>> entryHandlers,
                                                        ExecutorService executorService) {
        return new AsyncMessageHandlerImpl(entryHandlers, rowDataHandler, executorService);
    }

    @Bean
    @ConditionalOnMissingBean(IMessageHandler.class)
    @ConditionalOnExpression("${canal.async:true} == false and ${canal.mq.flat-message:true} == false")
    public IMessageHandler<Message> syncMessageHandler(RowDataHandler<CanalEntry.RowData> rowDataHandler,
                                                       List<EntryListener<?>> entryHandlers) {
        return new SyncMessageHandlerImpl(entryHandlers, rowDataHandler);
    }

    @Bean("rowDataHandler")
    @ConditionalOnMissingBean(RowDataHandler.class)
    @ConditionalOnProperty(value = "canal.mq.flat-message", havingValue = "true", matchIfMissing = true)
    public RowDataHandler<List<Map<String, String>>> flatMessageRowDataHandler() {
        return new MapRowDataHandlerImpl(new MapConvertFactory());
    }

    @Bean
    @ConditionalOnExpression("${canal.async:true} and ${canal.mq.flat-message:true}")
    public IMessageHandler<FlatMessage> asyncFlatMessageHandler(RowDataHandler<List<Map<String, String>>> rowDataHandler,
                                                                List<EntryListener<?>> entryListenerList,
                                                                ExecutorService executorService) {
        return new AsyncFlatMessageHandlerImpl(entryListenerList, rowDataHandler, executorService);
    }

    @Bean
    @ConditionalOnMissingBean(IMessageHandler.class)
    @ConditionalOnExpression("${canal.async:true} == false and ${canal.mq.flat-message:true}")
    public IMessageHandler<FlatMessage> syncFlatMessageHandler(RowDataHandler<List<Map<String, String>>> rowDataHandler,
                                                               List<EntryListener<?>> entryListenerList) {
        return new SyncFlatMessageHandlerImpl(entryListenerList, rowDataHandler);
    }

    @Bean(initMethod = "init", destroyMethod = "destroy")
    @ConditionalOnMissingBean(AbstractMqCanalClient.class)
    public RabbitMqCanalClient rabbitMqCanalClient(IMessageHandler<?> messageHandler) {
        Optional<CanalMqProperties> mqPropertiesOpt = Optional.ofNullable(canalProperties.getMq());
        CanalRabbitMqProperties mqProperties = canalProperties.getRabbitMq();
        return RabbitMqCanalClient.builder().servers(canalProperties.getServer())
                .virtualHost(mqProperties.getVirtualHost())
                .queueName(canalProperties.getDestination())
                .accessKey(mqProperties.getAccessKey())
                .secretKey(mqProperties.getSecretKey())
                .resourceOwnerId(mqProperties.getResourceOwnerId())
                .username(mqProperties.getUsername())
                .password(mqProperties.getPassword())
                .messageHandler(messageHandler)
                .batchSize(canalProperties.getBatchSize())
                .filter(canalProperties.getFilter())
                .timeout(canalProperties.getTimeout())
                .unit(canalProperties.getUnit())
                .flatMessage(mqPropertiesOpt.map(CanalMqProperties::getFlatMessage).orElse(Boolean.TRUE))
                .build();
    }

}
