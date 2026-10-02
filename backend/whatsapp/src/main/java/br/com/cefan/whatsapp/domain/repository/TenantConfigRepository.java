package br.com.cefan.whatsapp.domain.repository;

import br.com.cefan.whatsapp.domain.model.TenantConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TenantConfigRepository extends JpaRepository<TenantConfig, UUID> {
}
