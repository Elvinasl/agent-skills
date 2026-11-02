package __PACKAGE__.amqp;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class AMQPDefs {
    public static final String EXCHANGE = "__APP_NAME__";
    public static final String DLX_EXCHANGE = EXCHANGE + ".dlx";

    @Bean
    public Declarables amqpDeclarables() {
        var exchange = new TopicExchange(EXCHANGE, true, false);
        var dlx = new TopicExchange(DLX_EXCHANGE, true, false);

        List<Declarable> declarables = new ArrayList<>(List.of(exchange, dlx));
        return new Declarables(declarables);
    }

    static List<Declarable> declareWithDlq(TopicExchange exchange, TopicExchange dlx, String queueName, String routingKey) {
        String dlqName = queueName + ".dlq";

        Queue queue = QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", dlqName)
                .build();
        Queue dlq = QueueBuilder.durable(dlqName).build();

        Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);
        Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with(dlqName);

        return List.of(queue, dlq, binding, dlqBinding);
    }
}
