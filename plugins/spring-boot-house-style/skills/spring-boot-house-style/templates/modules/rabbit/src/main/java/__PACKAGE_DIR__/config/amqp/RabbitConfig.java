package __PACKAGE__.config.amqp;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Retry and backoff come from {@code spring.rabbitmq.listener.simple.retry.*}; once retries are exhausted the
 * recoverer rejects without requeue so the broker dead-letters the message.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter amqpMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    public MessageRecoverer messageRecoverer() {
        return new LoggingRejectAndDontRequeueRecoverer();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory
    ) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);

        List<Advice> chain = new ArrayList<>();
        chain.add(new MdcCleanupInterceptor());
        if (factory.getAdviceChain() != null) {
            chain.addAll(Arrays.asList(factory.getAdviceChain()));
        }
        factory.setAdviceChain(chain.toArray(Advice[]::new));
        return factory;
    }
}
