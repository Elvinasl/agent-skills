package __PACKAGE__.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Getter
@Setter
@ConfigurationProperties("app")
public class AppProperties {

    private Cors cors = new Cors();
    private List<Locale> supportedLocales = new ArrayList<>(List.of(Locale.ENGLISH));

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
    }
}
