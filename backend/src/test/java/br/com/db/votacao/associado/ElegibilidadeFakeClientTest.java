package br.com.db.votacao.associado;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ElegibilidadeFakeClientTest {
    @ParameterizedTest
    @CsvSource({"0, APTO", "1, INAPTO", "2, INVALIDO"})
    void formatoDeOnzeDigitosPodeProduzirOsTresEstadosSemAleatoriedadeNoTeste(int sorteio, SituacaoCpf esperado) {
        var client = new ElegibilidadeFakeClient(() -> sorteio);

        assertThat(client.consultar("00000000000")).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123", "123456789012", "000.000.000-00", "abcdefghijk", " 00000000000", "00000000000 "})
    void formatoInvalidoNuncaPodeSerSorteadoComoApto(String cpf) {
        var client = new ElegibilidadeFakeClient(() -> { throw new AssertionError("Não deveria sortear CPF fora do formato"); });

        assertThat(client.consultar(cpf)).isEqualTo(SituacaoCpf.INVALIDO);
    }
}
