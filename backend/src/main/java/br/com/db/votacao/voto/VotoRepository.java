package br.com.db.votacao.voto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VotoRepository extends JpaRepository<Voto, Long> {
    boolean existsByPautaIdAndAssociadoId(Long pautaId, String associadoId);

    @Query("select v.voto as voto, count(v) as quantidade from Voto v where v.pauta.id = :pautaId group by v.voto")
    List<ContagemPorOpcao> contarPorPauta(@Param("pautaId") Long pautaId);

    interface ContagemPorOpcao {
        OpcaoVoto getVoto();
        long getQuantidade();
    }
}
