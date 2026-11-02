package __PACKAGE__;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
// >>>mod:postgres
import org.testcontainers.postgresql.PostgreSQLContainer;
// <<<mod:postgres
// >>>mod:mysql
import org.testcontainers.mysql.MySQLContainer;
// <<<mod:mysql
// >>>mod:rabbit
import org.testcontainers.rabbitmq.RabbitMQContainer;
// <<<mod:rabbit

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {
    // >>>mod:postgres

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }
    // <<<mod:postgres
    // >>>mod:mysql

    @Bean
    @ServiceConnection
    MySQLContainer mysql() {
        return new MySQLContainer("mysql:8.4");
    }
    // <<<mod:mysql
    // >>>mod:rabbit

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return new RabbitMQContainer("rabbitmq:4.1-management");
    }
    // <<<mod:rabbit
}
