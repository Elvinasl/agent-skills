package __PACKAGE__;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class __APP_CLASS__Application {

    public static void main(String[] args) {
        SpringApplication.run(__APP_CLASS__Application.class, args);
    }
}
