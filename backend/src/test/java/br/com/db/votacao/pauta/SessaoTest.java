package br.com.db.votacao.pauta;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SessaoTest {
    private final Instant inicio = Instant.parse("2026-09-22T12:00:00Z");
    private final Sessao sessao = new Sessao(new Pauta("Pauta", null, inicio), inicio, inicio.plusSeconds(60));

    @Test
    void intervaloIncluiInicioEExcluiEncerramento() {
        assertThat(sessao.estaAberta(inicio.minusNanos(1))).isFalse();
        assertThat(sessao.estaAberta(inicio)).isTrue();
        assertThat(sessao.estaAberta(inicio.plusSeconds(60).minusNanos(1))).isTrue();
        assertThat(sessao.estaAberta(inicio.plusSeconds(60))).isFalse();
        assertThat(sessao.estaAberta(inicio.plusSeconds(60).plusNanos(1))).isFalse();
    }
}
