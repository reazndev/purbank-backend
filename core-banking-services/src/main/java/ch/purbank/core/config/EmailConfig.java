package ch.purbank.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "purbank.mail")
@Data
public class EmailConfig {
    private String from;
    private String fromName;
    private String verificationSubject;
    private String successSubject;
}