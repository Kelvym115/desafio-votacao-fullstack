package br.com.db.votacao.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationConfig {
    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("Assembleia — API de votação")
                .version("v1")
                .description("Pautas, sessões e votos. Datas em UTC. Sem autenticação neste desafio. "
                        + "Um associado vota uma única vez por pauta. A sessão encerra no instante encerraEm. "
                        + "CPF é uma simulação opcional e aleatória, sem consulta externa real."));
    }
}
