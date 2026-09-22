package br.com.db.votacao.pauta;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "pauta")
public class Pauta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 140)
    private String titulo;

    @Column(length = 2000)
    private String descricao;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @OneToOne(mappedBy = "pauta", fetch = FetchType.LAZY)
    private Sessao sessao;

    protected Pauta() { }

    public Pauta(String titulo, String descricao, Instant criadaEm) {
        this.titulo = titulo;
        this.descricao = descricao;
        this.criadaEm = criadaEm;
    }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getDescricao() { return descricao; }
    public Instant getCriadaEm() { return criadaEm; }
    public Sessao getSessao() { return sessao; }
}
