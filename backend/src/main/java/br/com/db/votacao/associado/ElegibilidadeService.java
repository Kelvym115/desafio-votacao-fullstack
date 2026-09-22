package br.com.db.votacao.associado;

import br.com.db.votacao.shared.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ElegibilidadeService {
    private final ElegibilidadeClient client;

    public ElegibilidadeService(ElegibilidadeClient client) {
        this.client = client;
    }

    public SituacaoCpf consultar(String cpf) {
        SituacaoCpf situacao = client.consultar(cpf);
        if (situacao == SituacaoCpf.INVALIDO) {
            throw new DomainException(HttpStatus.NOT_FOUND, "CPF_INVALIDO", "CPF considerado inválido pela simulação de elegibilidade.");
        }
        return situacao;
    }

    public void exigirApto(String cpf) {
        if (consultar(cpf) == SituacaoCpf.INAPTO) {
            throw new DomainException(HttpStatus.NOT_FOUND, "ASSOCIADO_INAPTO", "Associado sem permissão para votar nesta consulta simulada.");
        }
    }
}
