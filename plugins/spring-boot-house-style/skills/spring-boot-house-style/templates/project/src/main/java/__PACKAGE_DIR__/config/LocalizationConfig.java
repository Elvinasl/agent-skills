package __PACKAGE__.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

@Configuration
public class LocalizationConfig {

    @Bean
    public LocaleResolver localeResolver(AppProperties appProperties) {
        var resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(appProperties.getSupportedLocales());
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }
}
