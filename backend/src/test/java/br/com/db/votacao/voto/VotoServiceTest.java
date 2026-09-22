package br.com.db.votacao.voto;

import br.com.db.votacao.associado.ElegibilidadeService;
import br.com.db.votacao.pauta.Pauta;
import br.com.db.votacao.pauta.PautaService;
import br.com.db.votacao.pauta.Sessao;
import br.com.db.votacao.pauta.SessaoRepository;
import br.com.db.votacao.shared.DomainException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static br.com.db.votacao.voto.VotoDtos.RegistrarVoto;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VotoServiceTest {
    private static final Instant INICIO = Instant.parse("2026-09-22T12:00:00Z");
    @Mock private PautaService pautas;
    @Mock private SessaoRepository sessoes;
    @Mock private VotoRepository votos;
    @Mock private ElegibilidadeService elegibilidade;
    @Mock private Clock clock;
    private VotoService service;
    private Pauta pauta;
    private Sessao sessao;

    @BeforeEach
    void setUp() {
        service = new VotoService(pautas, sessoes, votos, elegibilidade, clock);
        pauta = new Pauta("Novo horário", null, INICIO);
        ReflectionTestUtils.setField(pauta, "id", 1L);
        sessao = new Sessao(pauta, INICIO, INICIO.plusSeconds(60));
    }

    private void prepararSessao() {
        when(pautas.buscarEntidade(1L)).thenReturn(pauta);
        when(sessoes.findByPautaId(1L)).thenReturn(Optional.of(sessao));
    }

    @Test
    void aceitaAntesDoPrazoENaoExigeCpfNoFluxoPrincipal() {
        prepararSessao();
        when(clock.instant()).thenReturn(INICIO.plusSeconds(59).plusNanos(999_999_000));
        when(votos.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.votar(1L, new RegistrarVoto("  associado-1  ", OpcaoVoto.SIM, null));

        assertThat(response.associadoId()).isEqualTo("associado-1");
        assertThat(response.voto()).isEqualTo(OpcaoVoto.SIM);
        assertThat(response.registradoEm()).isBefore(sessao.getEncerraEm());
        verifyNoInteractions(elegibilidade);
    }

    @Test
    void rejeitaExatamenteNoInstanteDeEncerramento() {
        prepararSessao();
        when(clock.instant()).thenReturn(INICIO.plusSeconds(60));

        assertCode("SESSAO_ENCERRADA", () -> service.votar(1L, new RegistrarVoto("1", OpcaoVoto.SIM, null)));
        verifyNoInteractions(votos, elegibilidade);
    }

    @Test
    void rejeitaVotoAntesDeAbrirSessao() {
        when(pautas.buscarEntidade(1L)).thenReturn(pauta);
        when(sessoes.findByPautaId(1L)).thenReturn(Optional.empty());

        assertCode("SESSAO_NAO_ABERTA", () -> service.votar(1L, new RegistrarVoto("1", OpcaoVoto.SIM, null)));
        verifyNoInteractions(votos, elegibilidade);
    }

    @Test
    void consultaCpfAntesDeSalvarQuandoInformado() {
        prepararSessao();
        when(clock.instant()).thenReturn(INICIO.plusSeconds(5));
        when(votos.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.votar(1L, new RegistrarVoto("1", OpcaoVoto.NAO, "00000000000"));

        var ordered = inOrder(elegibilidade, votos);
        ordered.verify(elegibilidade).exigirApto("00000000000");
        ordered.verify(votos).saveAndFlush(any());
    }

    @Test
    void naoSalvaSeSessaoEncerraDuranteConsultaDeElegibilidade() {
        prepararSessao();
        when(clock.instant()).thenReturn(INICIO.plusSeconds(59), INICIO.plusSeconds(60));

        assertCode("SESSAO_ENCERRADA", () -> service.votar(1L, new RegistrarVoto("1", OpcaoVoto.SIM, "00000000000")));

        verify(elegibilidade).exigirApto("00000000000");
        verify(votos, never()).saveAndFlush(any());
    }

    @Test
    void usaMesmoInstanteNaValidacaoFinalENaPersistencia() {
        prepararSessao();
        when(clock.instant()).thenReturn(INICIO.plusSeconds(10), INICIO.plusSeconds(11).plusNanos(999_999_999));
        when(votos.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.votar(1L, new RegistrarVoto("1", OpcaoVoto.SIM, null));

        ArgumentCaptor<Voto> captor = ArgumentCaptor.forClass(Voto.class);
        verify(votos).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRegistradoEm()).isEqualTo(INICIO.plusSeconds(11).plusNanos(999_999_000));
        verify(clock, times(2)).instant();
    }

    @Test
    void duplicidadeConhecidaNaoConsultaCpfNemTentaPersistir() {
        prepararSessao();
        when(clock.instant()).thenReturn(INICIO.plusSeconds(5));
        when(votos.existsByPautaIdAndAssociadoId(1L, "1")).thenReturn(true);

        assertCode("VOTO_DUPLICADO", () -> service.votar(1L, new RegistrarVoto("1", OpcaoVoto.NAO, "00000000000")));
        verify(votos, never()).saveAndFlush(any());
        verifyNoInteractions(elegibilidade);
    }

    @Test
    void unicidadeDoBancoRejeitaCorridaMesmoSeConsultaInicialNaoEncontrouVoto() {
        prepararSessao();
        when(clock.instant()).thenReturn(INICIO.plusSeconds(5));
        var constraint = new ConstraintViolationException("unique", new SQLException(), "uq_voto_pauta_associado");
        when(votos.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("conflict", constraint));

        assertCode("VOTO_DUPLICADO", () -> service.votar(1L, new RegistrarVoto("1", OpcaoVoto.SIM, null)));
    }

    @Test
    void naoEscondeErroDePersistenciaNaoRelacionadoAUnicidade() {
        prepararSessao();
        when(clock.instant()).thenReturn(INICIO.plusSeconds(5));
        var databaseError = new DataIntegrityViolationException("other database error");
        when(votos.saveAndFlush(any())).thenThrow(databaseError);

        assertThatThrownBy(() -> service.votar(1L, new RegistrarVoto("1", OpcaoVoto.SIM, null))).isSameAs(databaseError);
    }

    @Test
    void cpfInaptoImpedePersistencia() {
        prepararSessao();
        when(clock.instant()).thenReturn(INICIO.plusSeconds(5));
        doThrow(new DomainException(HttpStatus.NOT_FOUND, "ASSOCIADO_INAPTO", "Inapto"))
                .when(elegibilidade).exigirApto("00000000000");

        assertCode("ASSOCIADO_INAPTO", () -> service.votar(1L, new RegistrarVoto("1", OpcaoVoto.SIM, "00000000000")));
        verify(votos, never()).saveAndFlush(any());
    }

    @Test
    void pautaAusenteNaoAcessaVotos() {
        when(pautas.buscarEntidade(1L)).thenThrow(DomainException.pautaNaoEncontrada());

        assertCode("PAUTA_NAO_ENCONTRADA", () -> service.votar(1L, new RegistrarVoto("1", OpcaoVoto.SIM, null)));
        verifyNoInteractions(sessoes, votos, elegibilidade);
    }

    private void assertCode(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
