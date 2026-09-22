package br.com.db.votacao.pauta;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "sessao", uniqueConstraints = @UniqueConstraint(name = "uq_sessao_pauta", columnNames = "pauta_id"))
public class Sessao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pauta_id", nullable = false, updatable = false)
    private Pauta pauta;

    @Column(name = "aberta_em", nullable = false, updatable = false)
    private Instant abertaEm;

    @Column(name = "encerra_em", nullable = false, updatable = false)
    private Instant encerraEm;

    protected Sessao() { }

    public Sessao(Pauta pauta, Instant abertaEm, Instant encerraEm) {
        this.pauta = pauta;
        this.abertaEm = abertaEm;
        this.encerraEm = encerraEm;
    }

    public boolean estaAberta(Instant agora) {
        return !agora.isBefore(abertaEm) && agora.isBefore(encerraEm);
    }

    public Long getId() { return id; }
    public Pauta getPauta() { return pauta; }
    public Instant getAbertaEm() { return abertaEm; }
    public Instant getEncerraEm() { return encerraEm; }
}
