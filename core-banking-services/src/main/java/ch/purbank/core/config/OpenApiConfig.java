package ch.purbank.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI purbankOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Purbank Core Banking API")
                        .description("REST API for Purbank core banking services")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Purbank Dev Team")
                                .email("dev@purbank.ch")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Development Server")));
    }
}