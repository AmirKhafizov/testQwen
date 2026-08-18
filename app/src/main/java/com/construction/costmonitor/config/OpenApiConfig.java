package com.construction.costmonitor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI costMonitorOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cost Monitor API")
                        .description("""
                                Мониторинг цен стройматериалов (LemanaPro parser) и прогноз стоимости объектов.
                                
                                **Ручное тестирование:**
                                - `GET /api/v1/ping` — health
                                - `GET /api/v1/admin/prices/probe/{sku}` — цена без записи в БД
                                - `POST /api/v1/admin/prices/refresh-all` — обновить все CONFIRMED маппинги
                                """.stripIndent())
                        .version("0.0.1")
                        .contact(new Contact().name("Cost Monitor")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local")
                ));
    }
}
