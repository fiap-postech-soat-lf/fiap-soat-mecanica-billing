package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MpStatusAplicadoJpaRepository extends JpaRepository<MpStatusAplicadoEntity, MpStatusAplicadoId> {
}
