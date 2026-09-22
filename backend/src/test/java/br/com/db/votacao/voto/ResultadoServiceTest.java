package br.com.db.votacao.voto;

import br.com.db.votacao.pauta.Pauta;
import br.com.db.votacao.pauta.PautaRepository;
import br.com.db.votacao.pauta.Sessao;
import br.com.db.votacao.pauta.SessaoRepository;
import br.com.db.votacao.shared.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultadoServiceTest {
    private static final Instant AGORA = Instant.parse("2026-09-22T12:00:00Z");
    @Mock private PautaRepository pautas;
    @Mock private SessaoRepository sessoes;
    @Mock private VotoRepository votos;
    private ResultadoService service;
    private final Pauta pauta = new Pauta("Proposta", null, AGORA.minusSeconds(3600));

    @BeforeEach
    void setUp() {
        service = new ResultadoService(pautas, sessoes, votos, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @ParameterizedTest(name = "{0} sim e {1} não resultam em {2}")
    @CsvSource({"2, 1, APROVADA", "1, 2, REJEITADA", "2, 2, EMPATE", "0, 0, SEM_VOTOS", "3000000000, 1, APROVADA"})
    void apuraEncerradaInclusiveNoInstanteExatoDoPrazo(long sim, long nao, String resultado) {
        when(pautas.existsById(1L)).thenReturn(true);
        when(sessoes.findByPautaId(1L)).thenReturn(Optional.of(new Sessao(pauta, AGORA.minusSeconds(60), AGORA)));
        when(votos.contarPorPauta(1L)).thenReturn(List.of(contagem(OpcaoVoto.SIM, sim), contagem(OpcaoVoto.NAO, nao)));

        var response = service.apurar(1L);

        assertThat(response.status()).isEqualTo("ENCERRADA");
        assertThat(response.resultado()).isEqualTo(resultado);
        assertThat(response.sim()).isEqualTo(sim);
        assertThat(response.nao()).isEqualTo(nao);
        assertThat(response.total()).isEqualTo(sim + nao);
    }

    @Test
    void contagemParcialNaoDeclaraVencedorAntesDoEncerramento() {
        when(pautas.existsById(1L)).thenReturn(true);
        when(sessoes.findByPautaId(1L)).thenReturn(Optional.of(new Sessao(pauta, AGORA.minusSeconds(59), AGORA.plusSeconds(1))));
        when(votos.contarPorPauta(1L)).thenReturn(List.of(contagem(OpcaoVoto.SIM, 100_000)));

        var response = service.apurar(1L);

        assertThat(response.status()).isEqualTo("ABERTA");
        assertThat(response.resultado()).isEqualTo("EM_ANDAMENTO");
        assertThat(response.sim()).isEqualTo(100_000);
        assertThat(response.nao()).isZero();
    }

    @Test
    void pautaSemSessaoAguardaAbertura() {
        when(pautas.existsById(1L)).thenReturn(true);
        when(sessoes.findByPautaId(1L)).thenReturn(Optional.empty());
        when(votos.contarPorPauta(1L)).thenReturn(List.of());

        var response = service.apurar(1L);

        assertThat(response.status()).isEqualTo("NAO_INICIADA");
        assertThat(response.resultado()).isEqualTo("AGUARDANDO");
        assertThat(response.total()).isZero();
    }

    @Test
    void sessaoEncerradaSemVotosNaoETratadaComoEmpate() {
        when(pautas.existsById(1L)).thenReturn(true);
        when(sessoes.findByPautaId(1L)).thenReturn(Optional.of(new Sessao(pauta, AGORA.minusSeconds(120), AGORA.minusSeconds(60))));
        when(votos.contarPorPauta(1L)).thenReturn(List.of());

        var response = service.apurar(1L);

        assertThat(response.resultado()).isEqualTo("SEM_VOTOS");
    }

    @Test
    void naoRetornaContagemVaziaParaPautaInexistente() {
        assertThatThrownBy(() -> service.apurar(99L))
                .isInstanceOfSatisfying(DomainException.class, exception -> assertThat(exception.getCode()).isEqualTo("PAUTA_NAO_ENCONTRADA"));
        verifyNoInteractions(sessoes, votos);
    }

    private VotoRepository.ContagemPorOpcao contagem(OpcaoVoto opcao, long quantidade) {
        return new VotoRepository.ContagemPorOpcao() {
            @Override public OpcaoVoto getVoto() { return opcao; }
            @Override public long getQuantidade() { return quantidade; }
        };
    }
}
