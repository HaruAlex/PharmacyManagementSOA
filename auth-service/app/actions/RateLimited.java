package actions;

import play.mvc.With;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@With(RateLimitAction.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimited {
    Policy value();

    enum Policy {
        LOGIN("login"),
        REFRESH("refresh"),
        CHANGE_PASSWORD("changePassword");

        private final String configKey;

        Policy(String configKey) {
            this.configKey = configKey;
        }

        public String configKey() {
            return configKey;
        }
    }
}
