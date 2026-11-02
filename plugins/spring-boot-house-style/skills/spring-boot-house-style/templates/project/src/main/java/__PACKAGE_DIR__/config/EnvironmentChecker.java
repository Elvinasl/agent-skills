package __PACKAGE__.config;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnvironmentChecker {

    private final Environment environment;

    public boolean isDev() {
        return environment.acceptsProfiles(Profiles.of("dev"));
    }

    public boolean isRemote() {
        return environment.acceptsProfiles(Profiles.of("remote"));
    }
}
