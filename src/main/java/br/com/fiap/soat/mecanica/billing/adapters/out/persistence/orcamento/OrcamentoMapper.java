package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.orcamento;

import br.com.fiap.soat.mecanica.billing.domain.orcamento.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;

import java.util.List;

final class OrcamentoMapper {

    private OrcamentoMapper() {
    }

    static OrcamentoEntity paraEntity(Orcamento o) {
        OrcamentoEntity e = new OrcamentoEntity();
        e.setId(o.getId());
        e.setOsId(o.getOsId());
        e.setSagaId(o.getSagaId());
        e.setClienteId(o.getClienteId());
        e.setStatus(o.getStatus());
        e.setValorTotal(o.getValorTotal());
        e.setExpiraEm(o.getExpiraEm());
        e.setCriadoEm(o.getCriadoEm());
        e.setDecididoEm(o.getDecididoEm());
        e.setDecididoPor(o.getDecididoPor());
        e.setMotivo(o.getMotivo());
        e.setVersao(o.getVersao());
        List<ItemOrcamento> itens = o.getItens();
        for (int i = 0; i < itens.size(); i++) {
            ItemOrcamento item = itens.get(i);
            ItemOrcamentoEntity ie = new ItemOrcamentoEntity();
            ie.setId(item.id());
            ie.setOrdem(i);
            ie.setTipo(item.tipo());
            ie.setReferenciaId(item.referenciaId());
            ie.setDescricao(item.descricao());
            ie.setQuantidade(item.quantidade());
            ie.setValorUnitario(item.valorUnitario());
            ie.setValorTotal(item.valorTotal());
            e.adicionarItem(ie);
        }
        return e;
    }

    static Orcamento paraDominio(OrcamentoEntity e) {
        List<ItemOrcamento> itens = e.getItens().stream()
                .map(i -> new ItemOrcamento(i.getId(), i.getTipo(), i.getReferenciaId(), i.getDescricao(),
                        i.getQuantidade(), i.getValorUnitario()))
                .toList();
        return new Orcamento(e.getId(), e.getOsId(), e.getSagaId(), e.getClienteId(), itens, e.getValorTotal(),
                e.getExpiraEm(), e.getCriadoEm(), e.getStatus(), e.getDecididoEm(), e.getDecididoPor(),
                e.getMotivo(), e.getVersao());
    }
}
