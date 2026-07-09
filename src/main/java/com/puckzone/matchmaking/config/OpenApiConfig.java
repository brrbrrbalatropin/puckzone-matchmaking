package com.puckzone.matchmaking.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos del API para springdoc. El esquema bearer-jwt habilita el botón
 * Authorize de Swagger UI (el JWT identifica al jugador en la cola). OJO al
 * probar vía gateway: el path público es /api/matching (reescrito a /queue),
 * no el interno que documenta esta spec.
 * El server relativo "/" hace que el Try it out apunte al origen desde donde
 * se cargó la spec (el gateway en Azure).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI puckzoneOpenApi() {
        return new OpenAPI()
                .servers(List.of(new Server().url("/")))
                .info(new Info()
                        .title("PuckZone Matchmaking API")
                        .version("v1")
                        .description("Cola de emparejamiento por rating con ventana expansiva. "
                                + "Tras 10s de espera ofrece jugar contra el bot (botAvailable). "
                                + "Path público vía gateway: /api/matching (aquí documentado como /queue)."))
                .components(new Components().addSecuritySchemes("bearer-jwt",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
