package co.za.tveco.bff.repository;

import co.za.tveco.bff.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {
    Optional<Client> findByEmailIgnoreCase(String email);
    Optional<Client> findByPhone(String phone);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);
}
