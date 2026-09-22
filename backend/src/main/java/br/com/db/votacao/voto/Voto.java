package br.com.db.votacao.voto;

import br.com.db.votacao.pauta.Pauta;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "voto", uniqueConstraints = @UniqueConstraint(name = "uq_voto_pauta_associado", columnNames = {"pauta_id", "associado_id"}))
public class Voto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pauta_id", nullable = false, updatable = false)
    private Pauta pauta;

    @Column(name = "associado_id", nullable = false, length = 64, updatable = false)
    private String associadoId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private OpcaoVoto voto;

    @Column(name = "registrado_em", nullable = false, updatable = false)
    private Instant registradoEm;

    protected Voto() { }

    public Voto(Pauta pauta, String associadoId, OpcaoVoto voto, Instant registradoEm) {
        this.pauta = pauta;
        this.associadoId = associadoId;
        this.voto = voto;
        this.registradoEm = registradoEm;
    }

    public Long getId() { return id; }
    public Pauta getPauta() { return pauta; }
    public String getAssociadoId() { return associadoId; }
    public OpcaoVoto getVoto() { return voto; }
    public Instant getRegistradoEm() { return registradoEm; }
}
