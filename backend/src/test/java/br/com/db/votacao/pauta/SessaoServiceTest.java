package br.com.db.votacao.pauta;

import br.com.db.votacao.shared.DomainException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static br.com.db.votacao.pauta.PautaDtos.AbrirSessao;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessaoServiceTest {
    private static final Instant AGORA = Instant.parse("2026-09-22T12:00:00.123456789Z");
    @Mock private PautaService pautas;
    @Mock private SessaoRepository sessoes;
    private SessaoService service;
    private Pauta pauta;

    @BeforeEach
    void setUp() {
        service = new SessaoService(pautas, sessoes, Clock.fixed(AGORA, ZoneOffset.UTC));
        pauta = new Pauta("Horário da assembleia", null, AGORA);
        ReflectionTestUtils.setField(pauta, "id", 1L);
    }

    @Test
    void semCorpoAbrePorUmMinutoComPrecisaoCompativelComBanco() {
        when(pautas.buscarEntidade(1L)).thenReturn(pauta);
        when(sessoes.saveAndFlush(any(Sessao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.abrir(1L, null);

        assertThat(response.abertaEm()).isEqualTo(Instant.parse("2026-09-22T12:00:00.123456Z"));
        assertThat(response.encerraEm()).isEqualTo(response.abertaEm().plusSeconds(60));
        assertThat(response.status()).isEqualTo("ABERTA");
    }

    @Test
    void duracaoNullTambemAbrePorUmMinuto() {
        when(pautas.buscarEntidade(1L)).thenReturn(pauta);
        when(sessoes.saveAndFlush(any(Sessao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.abrir(1L, new AbrirSessao(null));

        assertThat(response.encerraEm()).isEqualTo(response.abertaEm().plusSeconds(60));
    }

    @Test
    void respeitaDuracaoInformada() {
        when(pautas.buscarEntidade(1L)).thenReturn(pauta);
        when(sessoes.saveAndFlush(any(Sessao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.abrir(1L, new AbrirSessao(1440));

        assertThat(response.encerraEm()).isEqualTo(response.abertaEm().plusSeconds(86400));
    }

    @Test
    void naoPermiteReabrirSessaoMesmoEncerrada() {
        when(pautas.buscarEntidade(1L)).thenReturn(pauta);
        when(sessoes.existsByPautaId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.abrir(1L, new AbrirSessao(1)))
                .isInstanceOfSatisfying(DomainException.class, ex -> assertThat(ex.getCode()).isEqualTo("SESSAO_JA_EXISTE"));
        verify(sessoes, never()).saveAndFlush(any());
    }

    @Test
    void traduzUnicidadeDoBancoQuandoDuasAberturasPassamPelaConsultaInicial() {
        when(pautas.buscarEntidade(1L)).thenReturn(pauta);
        var constraint = new ConstraintViolationException("unique", new SQLException(), "uq_sessao_pauta");
        when(sessoes.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("conflict", constraint));

        assertThatThrownBy(() -> service.abrir(1L, null))
                .isInstanceOfSatisfying(DomainException.class, ex -> assertThat(ex.getCode()).isEqualTo("SESSAO_JA_EXISTE"));
    }

    @Test
    void naoDisfarcaOutraFalhaDoBancoComoSessaoDuplicada() {
        when(pautas.buscarEntidade(1L)).thenReturn(pauta);
        var databaseError = new DataIntegrityViolationException("outro problema");
        when(sessoes.saveAndFlush(any())).thenThrow(databaseError);

        assertThatThrownBy(() -> service.abrir(1L, null)).isSameAs(databaseError);
    }

    @Test
    void falhaAntesDeAbrirSePautaNaoExiste() {
        when(pautas.buscarEntidade(1L)).thenThrow(DomainException.pautaNaoEncontrada());

        assertThatThrownBy(() -> service.abrir(1L, null))
                .isInstanceOfSatisfying(DomainException.class, ex -> assertThat(ex.getCode()).isEqualTo("PAUTA_NAO_ENCONTRADA"));
        verifyNoInteractions(sessoes);
    }
}
