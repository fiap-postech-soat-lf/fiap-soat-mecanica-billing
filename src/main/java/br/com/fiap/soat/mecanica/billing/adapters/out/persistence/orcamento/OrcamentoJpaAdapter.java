package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.orcamento;

import br.com.fiap.soat.mecanica.billing.domain.exception.ConflitoConcorrenciaException;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
public class OrcamentoJpaAdapter implements OrcamentoRepository {

    private final OrcamentoJpaRepository repository;

    public OrcamentoJpaAdapter(OrcamentoJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Orcamento salvar(Orcamento orcamento) {
        try {
            return OrcamentoMapper.paraDominio(repository.saveAndFlush(OrcamentoMapper.paraEntity(orcamento)));
        } catch (OptimisticLockingFailureException e) {
            throw new ConflitoConcorrenciaException("Orcamento alterado por outra operacao: " + orcamento.getId(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Orcamento> buscarPorId(UUID id) {
        return repository.findById(id).map(OrcamentoMapper::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Orcamento> buscarVivoPorOs(UUID osId) {
        return repository.buscarVivo(osId).map(OrcamentoMapper::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Orcamento> buscarMaisRecentePorOs(UUID osId) {
        return repository.findFirstByOsIdOrderByCriadoEmDesc(osId).map(OrcamentoMapper::paraDominio);
    }
}
