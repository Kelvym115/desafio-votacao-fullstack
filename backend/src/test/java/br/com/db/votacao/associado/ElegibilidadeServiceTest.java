package br.com.db.votacao.associado;

import br.com.db.votacao.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.*;

class ElegibilidadeServiceTest {
    @Test
    void permiteApto() {
        var service = new ElegibilidadeService(cpf -> SituacaoCpf.APTO);

        assertThatCode(() -> service.exigirApto("00000000000")).doesNotThrowAnyException();
    }

    @Test
    void invalidoRetorna404SemExporCpfNaMensagem() {
        var service = new ElegibilidadeService(cpf -> SituacaoCpf.INVALIDO);

        assertThatThrownBy(() -> service.exigirApto("00000000000"))
                .isInstanceOfSatisfying(DomainException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("CPF_INVALIDO");
                    assertThat(exception.getMessage()).doesNotContain("00000000000");
                });
    }

    @Test
    void inaptoPodeSerConsultadoMasNaoPodeVotar() {
        var service = new ElegibilidadeService(cpf -> SituacaoCpf.INAPTO);

        assertThat(service.consultar("00000000000")).isEqualTo(SituacaoCpf.INAPTO);
        assertThatThrownBy(() -> service.exigirApto("00000000000"))
                .isInstanceOfSatisfying(DomainException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("ASSOCIADO_INAPTO");
                });
    }
}
