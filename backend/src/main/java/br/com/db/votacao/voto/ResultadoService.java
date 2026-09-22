package br.com.db.votacao.voto;

import br.com.db.votacao.pauta.PautaRepository;
import br.com.db.votacao.pauta.Sessao;
import br.com.db.votacao.pauta.SessaoRepository;
import br.com.db.votacao.shared.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static br.com.db.votacao.voto.VotoDtos.ResultadoResponse;

@Service
public class ResultadoService {
    private final PautaRepository pautas;
    private final SessaoRepository sessoes;
    private final VotoRepository votos;
    private final Clock clock;

    public ResultadoService(PautaRepository pautas, SessaoRepository sessoes, VotoRepository votos, Clock clock) {
        this.pautas = pautas;
        this.sessoes = sessoes;
        this.votos = votos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ResultadoResponse apurar(Long pautaId) {
        if (!pautas.existsById(pautaId)) {
            throw DomainException.pautaNaoEncontrada();
        }
        Optional<Sessao> sessao = sessoes.findByPautaId(pautaId);
        Instant agora = clock.instant();
        List<VotoRepository.ContagemPorOpcao> contagem = votos.contarPorPauta(pautaId);
        long sim = quantidade(contagem, OpcaoVoto.SIM);
        long nao = quantidade(contagem, OpcaoVoto.NAO);
        String status = sessao.map(value -> value.estaAberta(agora) ? "ABERTA" : "ENCERRADA").orElse("NAO_INICIADA");
        String resultado = switch (status) {
            case "NAO_INICIADA" -> "AGUARDANDO";
            case "ABERTA" -> "EM_ANDAMENTO";
            default -> sim + nao == 0 ? "SEM_VOTOS" : sim == nao ? "EMPATE" : sim > nao ? "APROVADA" : "REJEITADA";
        };
        return new ResultadoResponse(pautaId, sim, nao, sim + nao, status, resultado);
    }

    private long quantidade(List<VotoRepository.ContagemPorOpcao> contagens, OpcaoVoto opcao) {
        return contagens.stream().filter(contagem -> contagem.getVoto() == opcao)
                .mapToLong(VotoRepository.ContagemPorOpcao::getQuantidade).sum();
    }
}
