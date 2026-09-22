package br.com.db.votacao.associado;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/** Simula o contrato externo do enunciado; não valida CPF real nem consulta terceiros. */
@Component
public class ElegibilidadeFakeClient implements ElegibilidadeClient {
    private final IntSupplier sortear;

    public ElegibilidadeFakeClient() {
        this(() -> ThreadLocalRandom.current().nextInt(SituacaoCpf.values().length));
    }

    ElegibilidadeFakeClient(IntSupplier sortear) {
        this.sortear = sortear;
    }

    @Override
    public SituacaoCpf consultar(String cpf) {
        if (cpf == null || !cpf.matches("[0-9]{11}")) {
            return SituacaoCpf.INVALIDO;
        }
        return SituacaoCpf.values()[sortear.getAsInt()];
    }
}
