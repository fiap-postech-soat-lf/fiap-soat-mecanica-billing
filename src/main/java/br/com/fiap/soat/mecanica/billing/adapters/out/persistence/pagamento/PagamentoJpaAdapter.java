package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.pagamento;

import br.com.fiap.soat.mecanica.billing.domain.exception.ConflitoConcorrenciaException;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PagamentoJpaAdapter implements PagamentoRepository {

    private final PagamentoJpaRepository repository;

    public PagamentoJpaAdapter(PagamentoJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Pagamento salvar(Pagamento pagamento) {
        try {
            return paraDominio(repository.saveAndFlush(paraEntity(pagamento)));
        } catch (OptimisticLockingFailureException e) {
            throw new ConflitoConcorrenciaException("Pagamento alterado por outra operacao: " + pagamento.getId(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Pagamento> buscarPorId(UUID id) {
        return repository.findById(id).map(PagamentoJpaAdapter::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Pagamento> buscarVigentePorOs(UUID osId) {
        return repository.findByOsIdAndStatusNot(osId, StatusPagamento.SUBSTITUIDO)
                .map(PagamentoJpaAdapter::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pagamento> listarPorOs(UUID osId) {
        return repository.findByOsIdOrderByCriadoEmDesc(osId).stream().map(PagamentoJpaAdapter::paraDominio).toList();
    }

    private static PagamentoEntity paraEntity(Pagamento p) {
        PagamentoEntity e = new PagamentoEntity();
        e.setId(p.getId());
        e.setOsId(p.getOsId());
        e.setOrcamentoId(p.getOrcamentoId());
        e.setClienteId(p.getClienteId());
        e.setValor(p.getValor());
        e.setStatus(p.getStatus());
        e.setMpPreferenceId(p.getMpPreferenceId());
        e.setLink(p.getLink());
        e.setMpPaymentId(p.getMpPaymentId());
        e.setMotivo(p.getMotivo());
        e.setCriadoEm(p.getCriadoEm());
        e.setAtualizadoEm(p.getAtualizadoEm());
        e.setVersao(p.getVersao());
        return e;
    }

    private static Pagamento paraDominio(PagamentoEntity e) {
        return new Pagamento(e.getId(), e.getOsId(), e.getOrcamentoId(), e.getClienteId(), e.getValor(),
                e.getStatus(), e.getMpPreferenceId(), e.getLink(), e.getMpPaymentId(), e.getMotivo(),
                e.getCriadoEm(), e.getAtualizadoEm(), e.getVersao());
    }
}
