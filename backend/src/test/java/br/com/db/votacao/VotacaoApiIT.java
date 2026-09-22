package br.com.db.votacao;

import br.com.db.votacao.associado.ElegibilidadeClient;
import br.com.db.votacao.associado.SituacaoCpf;
import br.com.db.votacao.support.IntegrationFixtures.Api;
import br.com.db.votacao.support.IntegrationFixtures.ClockConfiguration;
import br.com.db.votacao.support.IntegrationFixtures.MutableClock;
import br.com.db.votacao.support.IntegrationFixtures.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static br.com.db.votacao.support.IntegrationFixtures.INICIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Integra HTTP, validação, serviços, JPA e migrações em banco real, sem transação de teste.
 * H2 por padrão; o CI também executa sobre PostgreSQL via SPRING_DATASOURCE_*. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Import(ClockConfiguration.class)
@Execution(ExecutionMode.SAME_THREAD)
class VotacaoApiIT {
    @LocalServerPort
    int port;

    @Autowired
    MutableClock clock;

    // Somente a fronteira do serviço externo fake é substituída; banco e HTTP são reais.
    @MockitoBean
    ElegibilidadeClient elegibilidade;

    Api api;

    @BeforeEach
    void configurarTempoEClienteHttp() {
        clock.set(INICIO);
        api = new Api(port);
    }

    @Test
    void cadastraPautaEConsultaEstadoInicial() throws Exception {
        var criada = api.post("/pautas", """
                {"titulo":"Novo horário","descricao":"Discussão da assembleia"}
                """);
        assertStatus(criada, 201);
        long id = criada.body().path("id").asLong();
        assertThat(id).isPositive();
        assertThat(criada.body().path("titulo").asText()).isEqualTo("Novo horário");
        assertThat(criada.body().path("descricao").asText()).isEqualTo("Discussão da assembleia");
        assertThat(Instant.parse(criada.body().path("criadaEm").asText())).isEqualTo(INICIO);
        assertThat(criada.body().path("sessao").isNull()).isTrue();

        var consultada = api.get("/pautas/" + id);
        assertStatus(consultada, 200);
        assertThat(consultada.body()).isEqualTo(criada.body());
        var resultado = api.get("/pautas/" + id + "/resultado");
        assertStatus(resultado, 200);
        assertThat(resultado.body().path("status").asText()).isEqualTo("NAO_INICIADA");
        assertThat(resultado.body().path("resultado").asText()).isEqualTo("AGUARDANDO");
        assertThat(resultado.body().path("total").asLong()).isZero();
    }

    @ParameterizedTest
    @MethodSource("pautasInvalidas")
    void rejeitaPautaInvalidaComProblemDetail(String body) throws Exception {
        assertProblem(api.post("/pautas", body), 400);
    }

    static Stream<String> pautasInvalidas() {
        return Stream.of("{}", "{\"titulo\":null}", "{\"titulo\":\"   \"}",
                "{\"titulo\":\"" + "a".repeat(141) + "\"}",
                "{\"titulo\":\"Válida\",\"descricao\":\"" + "a".repeat(2001) + "\"}",
                "{nao-e-json}");
    }

    @Test
    void validacaoIdentificaOCampoParaOFormulario() throws Exception {
        var resposta = api.post("/pautas", "{\"titulo\":\"   \"}");
        assertProblem(resposta, 400);
        assertThat(resposta.body().path("errors").path("titulo").asText()).isNotBlank();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"{}", "{\"duracaoMinutos\":null}"})
    void duracaoOmitidaUsaUmMinuto(String body) throws Exception {
        long pauta = criarPauta("Sessão padrão");
        var sessao = api.post("/pautas/" + pauta + "/sessoes", body);
        assertStatus(sessao, 201);
        assertThat(Instant.parse(sessao.body().path("abertaEm").asText())).isEqualTo(INICIO);
        assertThat(Instant.parse(sessao.body().path("encerraEm").asText())).isEqualTo(INICIO.plusSeconds(60));
        assertThat(sessao.body().path("status").asText()).isEqualTo("ABERTA");
    }

    @Test
    void duracaoInformadaEhRespeitada() throws Exception {
        long pauta = criarPauta("Sessão de cinco minutos");
        var sessao = api.post("/pautas/" + pauta + "/sessoes", "{\"duracaoMinutos\":5}");
        assertStatus(sessao, 201);
        assertThat(Instant.parse(sessao.body().path("encerraEm").asText())).isEqualTo(INICIO.plusSeconds(300));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1441})
    void rejeitaDuracaoForaDosLimites(int minutos) throws Exception {
        long pauta = criarPauta("Duração inválida");
        assertProblem(api.post("/pautas/" + pauta + "/sessoes", "{\"duracaoMinutos\":" + minutos + "}"), 400);
        assertThat(api.get("/pautas/" + pauta).body().path("sessao").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"duracaoMinutos\":1.5}", "{\"duracaoMinutos\":\"5\"}"})
    void duracaoExigeNumeroInteiroSemConversaoSilenciosa(String body) throws Exception {
        long pauta = criarPauta("Tipo da duração");
        assertProblem(api.post("/pautas/" + pauta + "/sessoes", body), 400);
        assertThat(api.get("/pautas/" + pauta).body().path("sessao").isNull()).isTrue();
    }

    @Test
    void naoPermiteSegundaSessaoMesmoDepoisDoEncerramento() throws Exception {
        long pauta = criarPautaAberta();
        assertProblem(api.post("/pautas/" + pauta + "/sessoes", "{}"), 409);
        clock.set(INICIO.plusSeconds(60));
        assertProblem(api.post("/pautas/" + pauta + "/sessoes", "{}"), 409);
        var consultada = api.get("/pautas/" + pauta);
        assertThat(consultada.body().path("sessao").path("encerraEm").asText()).isEqualTo(INICIO.plusSeconds(60).toString());
    }

    @Test
    void votoSemSessaoRetornaConflito() throws Exception {
        long pauta = criarPauta("Ainda sem sessão");
        assertProblem(votar(pauta, "associado-1", "SIM"), 409);
        assertThat(resultado(pauta).body().path("total").asLong()).isZero();
    }

    @Test
    void aceitaAntesDoEncerramentoERecusaExatamenteNoPrazo() throws Exception {
        long pauta = criarPautaAberta();
        clock.set(INICIO.plusSeconds(60).minusNanos(1000));
        assertStatus(votar(pauta, "antes", "SIM"), 201);
        clock.set(INICIO.plusSeconds(60));
        assertProblem(votar(pauta, "no-prazo", "NAO"), 409);
        clock.set(INICIO.plusSeconds(600));
        assertProblem(votar(pauta, "depois", "NAO"), 409);
        var resultado = resultado(pauta);
        assertThat(resultado.body().path("total").asLong()).isEqualTo(1);
        assertThat(resultado.body().path("status").asText()).isEqualTo("ENCERRADA");
        assertThat(resultado.body().path("resultado").asText()).isEqualTo("APROVADA");
    }

    @ParameterizedTest
    @MethodSource("votosInvalidos")
    void rejeitaPayloadDeVotoInvalido(String body) throws Exception {
        long pauta = criarPautaAberta();
        assertProblem(api.post("/pautas/" + pauta + "/votos", body), 400);
        assertThat(resultado(pauta).body().path("total").asLong()).isZero();
    }

    static Stream<String> votosInvalidos() {
        return Stream.of("{}", "{\"associadoId\":\"\",\"voto\":\"SIM\"}",
                "{\"associadoId\":\"   \",\"voto\":\"SIM\"}",
                "{\"associadoId\":\"" + "a".repeat(65) + "\",\"voto\":\"SIM\"}",
                "{\"associadoId\":\"teste\",\"voto\":\"TALVEZ\"}",
                "{\"associadoId\":\"teste\",\"voto\":\"sim\"}",
                "{\"associadoId\":\"teste\",\"voto\":0}",
                "{\"associadoId\":\"teste\",\"voto\":null}");
    }

    @Test
    void removeEspacosDaIdentificacaoEImpedeVotoRepetido() throws Exception {
        long pauta = criarPautaAberta();
        var primeiro = votar(pauta, "  associado-42  ", "SIM");
        assertStatus(primeiro, 201);
        assertThat(primeiro.body().path("associadoId").asText()).isEqualTo("associado-42");
        assertThat(primeiro.body().path("pautaId").asLong()).isEqualTo(pauta);
        assertThat(primeiro.body().path("voto").asText()).isEqualTo("SIM");
        assertProblem(votar(pauta, "associado-42", "NAO"), 409);
        var resultado = resultado(pauta);
        assertThat(resultado.body().path("sim").asLong()).isEqualTo(1);
        assertThat(resultado.body().path("nao").asLong()).isZero();
    }

    @Test
    void associadoPodeVotarEmPautasDiferentes() throws Exception {
        long primeira = criarPautaAberta();
        long segunda = criarPautaAberta();
        assertStatus(votar(primeira, "mesmo-associado", "SIM"), 201);
        assertStatus(votar(segunda, "mesmo-associado", "NAO"), 201);
        assertThat(resultado(primeira).body().path("sim").asLong()).isEqualTo(1);
        assertThat(resultado(segunda).body().path("nao").asLong()).isEqualTo(1);
    }

    @Test
    void disponibilizaResultadoParcialSemDeclararVencedor() throws Exception {
        long pauta = criarPautaAberta();
        assertStatus(votar(pauta, "associado-1", "SIM"), 201);
        var resultado = resultado(pauta);
        assertThat(resultado.body().path("status").asText()).isEqualTo("ABERTA");
        assertThat(resultado.body().path("resultado").asText()).isEqualTo("EM_ANDAMENTO");
        assertThat(resultado.body().path("total").asLong()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"0,0,SEM_VOTOS", "1,1,EMPATE", "2,1,APROVADA", "1,2,REJEITADA"})
    void calculaResultadoFinal(int sim, int nao, String esperado) throws Exception {
        long pauta = criarPautaAberta();
        for (int i = 0; i < sim; i++) {
            assertStatus(votar(pauta, "sim-" + i, "SIM"), 201);
        }
        for (int i = 0; i < nao; i++) {
            assertStatus(votar(pauta, "nao-" + i, "NAO"), 201);
        }
        clock.set(INICIO.plusSeconds(60));
        var resultado = resultado(pauta);
        assertThat(resultado.body().path("status").asText()).isEqualTo("ENCERRADA");
        assertThat(resultado.body().path("resultado").asText()).isEqualTo(esperado);
        assertThat(resultado.body().path("sim").asLong()).isEqualTo(sim);
        assertThat(resultado.body().path("nao").asLong()).isEqualTo(nao);
        assertThat(resultado.body().path("total").asLong()).isEqualTo(sim + nao);
    }

    @Test
    void duasRequisicoesSimultaneasDoMesmoAssociadoPersistemUmUnicoVoto() throws Exception {
        long pauta = criarPautaAberta();
        var respostas = simultaneamente(() -> votar(pauta, "concorrente", "SIM"));
        assertThat(respostas).extracting(Response::status).containsExactlyInAnyOrder(201, 409);
        assertProblem(respostas.stream().filter(r -> r.status() == 409).findFirst().orElseThrow(), 409);
        var resultado = resultado(pauta);
        assertThat(resultado.body().path("total").asLong()).isEqualTo(1);
        assertThat(resultado.body().path("sim").asLong()).isEqualTo(1);
    }

    @Test
    void aberturaSimultaneaCriaApenasUmaSessao() throws Exception {
        long pauta = criarPauta("Abertura concorrente");
        var respostas = simultaneamente(() -> api.post("/pautas/" + pauta + "/sessoes", "{}"));
        assertThat(respostas).extracting(Response::status).containsExactlyInAnyOrder(201, 409);
        var sessaoCriada = respostas.stream().filter(r -> r.status() == 201).findFirst().orElseThrow();
        assertThat(api.get("/pautas/" + pauta).body().path("sessao").path("id").asLong())
                .isEqualTo(sessaoCriada.body().path("id").asLong());
    }

    @Test
    void paginaFiltraPorTituloEOrdenaMaisRecentesPrimeiro() throws Exception {
        String busca = "paginacao-" + UUID.randomUUID();
        long primeira = criarPauta(busca + " primeira");
        long segunda = criarPauta(busca + " segunda");
        long terceira = criarPauta(busca + " terceira");
        var pagina = api.get("/pautas?busca=" + busca + "&page=0&size=2");
        assertStatus(pagina, 200);
        assertThat(pagina.body().path("page").asInt()).isZero();
        assertThat(pagina.body().path("size").asInt()).isEqualTo(2);
        assertThat(pagina.body().path("totalElements").asLong()).isEqualTo(3);
        assertThat(pagina.body().path("totalPages").asInt()).isEqualTo(2);
        assertThat(pagina.body().path("content").size()).isEqualTo(2);
        assertThat(pagina.body().path("content").get(0).path("id").asLong()).isEqualTo(terceira);
        assertThat(pagina.body().path("content").get(1).path("id").asLong()).isEqualTo(segunda);
        var proxima = api.get("/pautas?busca=" + busca + "&page=1&size=2");
        assertStatus(proxima, 200);
        assertThat(proxima.body().path("content").size()).isEqualTo(1);
        assertThat(proxima.body().path("content").get(0).path("id").asLong()).isEqualTo(primeira);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=texto"})
    void rejeitaPaginacaoInvalida(String query) throws Exception {
        assertProblem(api.get("/pautas?" + query), 400);
    }

    @Test
    void pautaInexistenteRetorna404NasOperacoes() throws Exception {
        long id = Long.MAX_VALUE;
        assertProblem(api.get("/pautas/" + id), 404);
        assertProblem(api.get("/pautas/" + id + "/resultado"), 404);
        assertProblem(api.post("/pautas/" + id + "/sessoes", "{}"), 404);
        assertProblem(votar(id, "associado", "SIM"), 404);
    }

    @Test
    void consultaCpfComTresRespostasControladasDoClient() throws Exception {
        when(elegibilidade.consultar("11111111111")).thenReturn(SituacaoCpf.APTO);
        when(elegibilidade.consultar("22222222222")).thenReturn(SituacaoCpf.INAPTO);
        when(elegibilidade.consultar("33333333333")).thenReturn(SituacaoCpf.INVALIDO);
        var apto = api.get("/associados/11111111111/elegibilidade");
        assertStatus(apto, 200);
        assertThat(apto.body().path("status").asText()).isEqualTo("ABLE_TO_VOTE");
        var inapto = api.get("/associados/22222222222/elegibilidade");
        assertStatus(inapto, 404);
        assertThat(inapto.body().path("status").asText()).isEqualTo("UNABLE_TO_VOTE");
        assertProblem(api.get("/associados/33333333333/elegibilidade"), 404);
    }

    @Test
    void consultaOpcionalDoCpfAceitaAptoEBloqueiaInaptoOuInvalido() throws Exception {
        when(elegibilidade.consultar("11111111111")).thenReturn(SituacaoCpf.APTO);
        when(elegibilidade.consultar("22222222222")).thenReturn(SituacaoCpf.INAPTO);
        when(elegibilidade.consultar("33333333333")).thenReturn(SituacaoCpf.INVALIDO);
        long pauta = criarPautaAberta();
        String path = "/pautas/" + pauta + "/votos";
        assertProblem(api.post(path, "{\"associadoId\":\"inapto\",\"voto\":\"SIM\",\"cpf\":\"22222222222\"}"), 404);
        assertProblem(api.post(path, "{\"associadoId\":\"invalido\",\"voto\":\"SIM\",\"cpf\":\"33333333333\"}"), 404);
        assertThat(resultado(pauta).body().path("total").asLong()).isZero();
        assertStatus(api.post(path, "{\"associadoId\":\"apto\",\"voto\":\"SIM\",\"cpf\":\"11111111111\"}"), 201);
        assertThat(resultado(pauta).body().path("total").asLong()).isEqualTo(1);
    }

    private long criarPauta(String titulo) throws Exception {
        var resposta = api.post("/pautas", "{\"titulo\":\"" + titulo + "\"}");
        assertStatus(resposta, 201);
        return resposta.body().path("id").asLong();
    }

    private long criarPautaAberta() throws Exception {
        long pauta = criarPauta("Pauta de integração");
        assertStatus(api.post("/pautas/" + pauta + "/sessoes", "{}"), 201);
        return pauta;
    }

    private Response votar(long pauta, String associado, String voto) throws Exception {
        return api.post("/pautas/" + pauta + "/votos",
                "{\"associadoId\":\"" + associado + "\",\"voto\":\"" + voto + "\"}");
    }

    private Response resultado(long pauta) throws Exception {
        var resultado = api.get("/pautas/" + pauta + "/resultado");
        assertStatus(resultado, 200);
        return resultado;
    }

    private static List<Response> simultaneamente(Callable<Response> operacao) throws Exception {
        var prontas = new CountDownLatch(2);
        var iniciar = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        Callable<Response> chamada = () -> {
            prontas.countDown();
            if (!iniciar.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("As duas requisições não foram iniciadas a tempo");
            }
            return operacao.call();
        };
        try {
            var primeira = executor.submit(chamada);
            var segunda = executor.submit(chamada);
            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();
            return List.of(primeira.get(25, TimeUnit.SECONDS), segunda.get(25, TimeUnit.SECONDS));
        } finally {
            iniciar.countDown();
            executor.shutdownNow();
        }
    }

    private static void assertStatus(Response response, int expected) {
        assertThat(response.status()).withFailMessage("HTTP esperado %s, recebido %s: %s",
                expected, response.status(), response.body()).isEqualTo(expected);
    }

    private static void assertProblem(Response response, int expected) {
        assertStatus(response, expected);
        assertThat(response.body().path("status").asInt()).isEqualTo(expected);
        assertThat(response.body().path("detail").asText()).as("detail da resposta %s", response.body()).isNotBlank();
        assertThat(response.body().path("code").asText()).as("code da resposta %s", response.body()).isNotBlank();
        assertThat(response.body().has("trace")).isFalse();
    }
}
