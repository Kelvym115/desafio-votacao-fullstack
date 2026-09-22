package br.com.db.votacao;

import br.com.db.votacao.support.IntegrationFixtures.Api;
import br.com.db.votacao.support.IntegrationFixtures.ClockConfiguration;
import br.com.db.votacao.support.IntegrationFixtures.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;

import static br.com.db.votacao.support.IntegrationFixtures.INICIO;
import static org.assertj.core.api.Assertions.assertThat;

/** Fecha efetivamente um servidor e abre outro sobre o mesmo arquivo de banco. */
class PersistenciaAposReinicioIT {
    @TempDir
    Path directory;

    @Test
    void pautaSessaoEVotoSobrevivemAoReinicioDaAplicacao() throws Exception {
        String databaseUrl = "jdbc:h2:file:" + directory.resolve("votacao") + ";DB_CLOSE_ON_EXIT=FALSE";
        long pauta;
        long sessao;
        try (var primeiraAplicacao = iniciar(databaseUrl)) {
            var api = cliente(primeiraAplicacao);
            var criada = api.post("/pautas", "{\"titulo\":\"Pauta persistente\",\"descricao\":\"Sobrevive ao reinício\"}");
            assertThat(criada.status()).isEqualTo(201);
            pauta = criada.body().path("id").asLong();
            var aberta = api.post("/pautas/" + pauta + "/sessoes", "{}");
            assertThat(aberta.status()).isEqualTo(201);
            sessao = aberta.body().path("id").asLong();
            assertThat(api.post("/pautas/" + pauta + "/votos", "{\"associadoId\":\"associado-persistente\",\"voto\":\"SIM\"}").status())
                    .isEqualTo(201);
        }
        assertThat(Files.isRegularFile(directory.resolve("votacao.mv.db"))).isTrue();

        try (var segundaAplicacao = iniciar(databaseUrl)) {
            var api = cliente(segundaAplicacao);
            var consultada = api.get("/pautas/" + pauta);
            assertThat(consultada.status()).isEqualTo(200);
            assertThat(consultada.body().path("titulo").asText()).isEqualTo("Pauta persistente");
            assertThat(consultada.body().path("descricao").asText()).isEqualTo("Sobrevive ao reinício");
            assertThat(consultada.body().path("sessao").path("id").asLong()).isEqualTo(sessao);
            assertThat(consultada.body().path("sessao").path("abertaEm").asText()).isEqualTo(INICIO.toString());
            assertThat(consultada.body().path("sessao").path("encerraEm").asText()).isEqualTo(INICIO.plusSeconds(60).toString());
            // O voto persistido continua impedindo uma segunda participação após reiniciar.
            assertThat(api.post("/pautas/" + pauta + "/votos", "{\"associadoId\":\"associado-persistente\",\"voto\":\"NAO\"}").status())
                    .isEqualTo(409);
            segundaAplicacao.getBean(MutableClock.class).set(INICIO.plusSeconds(60));
            var resultado = api.get("/pautas/" + pauta + "/resultado");
            assertThat(resultado.status()).isEqualTo(200);
            assertThat(resultado.body().path("sim").asLong()).isEqualTo(1);
            assertThat(resultado.body().path("nao").asLong()).isZero();
            assertThat(resultado.body().path("total").asLong()).isEqualTo(1);
            assertThat(resultado.body().path("status").asText()).isEqualTo("ENCERRADA");
            assertThat(resultado.body().path("resultado").asText()).isEqualTo("APROVADA");
            assertThat(api.post("/pautas/" + pauta + "/votos", "{\"associadoId\":\"novo-associado\",\"voto\":\"SIM\"}").status())
                    .isEqualTo(409);
        }
    }

    private ConfigurableApplicationContext iniciar(String url) {
        return new SpringApplicationBuilder(VotacaoApplication.class, ClockConfiguration.class)
                .bannerMode(Banner.Mode.OFF)
                .run("--spring.profiles.active=integration", "--server.port=0",
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.username=sa", "--spring.datasource.password=",
                        "--spring.datasource.driver-class-name=org.h2.Driver");
    }

    private Api cliente(ConfigurableApplicationContext context) {
        return new Api(((WebServerApplicationContext) context).getWebServer().getPort());
    }
}
