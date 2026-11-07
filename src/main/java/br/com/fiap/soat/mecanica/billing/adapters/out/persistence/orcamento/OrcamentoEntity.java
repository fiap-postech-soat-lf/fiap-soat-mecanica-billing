package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.orcamento;

import br.com.fiap.soat.mecanica.billing.domain.orcamento.StatusOrcamento;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orcamentos")
public class OrcamentoEntity {

    @Id
    private UUID id;

    @Column(name = "os_id", nullable = false)
    private UUID osId;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusOrcamento status;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    @Column(name = "decidido_por")
    private UUID decididoPor;

    @Column(columnDefinition = "text")
    private String motivo;

    @Version
    @Column(nullable = false)
    private Long versao;

    @OneToMany(mappedBy = "orcamento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem ASC")
    private List<ItemOrcamentoEntity> itens = new ArrayList<>();

    public void adicionarItem(ItemOrcamentoEntity item) {
        item.setOrcamento(this);
        itens.add(item);
    }
}
