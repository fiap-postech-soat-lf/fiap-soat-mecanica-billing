package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.saga;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ComandoAtendidoJpaRepository extends JpaRepository<ComandoAtendidoEntity, ComandoAtendidoId> {
}
