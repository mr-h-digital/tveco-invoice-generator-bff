package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.ClientDto;
import co.za.tveco.bff.dto.ClientRequest;
import co.za.tveco.bff.entity.Client;
import co.za.tveco.bff.exception.ConflictException;
import co.za.tveco.bff.exception.ResourceNotFoundException;
import co.za.tveco.bff.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    public List<ClientDto> getAll() {
        return clientRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientDto getById(UUID id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public ClientDto create(ClientRequest req) {
        String email = normaliseEmail(req.email());
        if (!email.isEmpty() && clientRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ConflictException("A client with email '" + email + "' already exists");
        }
        Client client = Client.builder()
                .companyName(req.companyName())
                .contactName(req.contactName() == null ? "" : req.contactName())
                .email(email)
                .phone(req.phone() == null ? "" : req.phone())
                .address(req.address() == null ? "" : req.address())
                .build();
        return toDto(clientRepository.save(client));
    }

    @Transactional
    public ClientDto update(UUID id, ClientRequest req) {
        Client client = findOrThrow(id);
        String email = normaliseEmail(req.email());
        if (!email.isEmpty() && clientRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ConflictException("Another client with email '" + email + "' already exists");
        }
        client.setCompanyName(req.companyName());
        client.setContactName(req.contactName() == null ? "" : req.contactName());
        client.setEmail(email);
        client.setPhone(req.phone() == null ? "" : req.phone());
        client.setAddress(req.address() == null ? "" : req.address());
        return toDto(clientRepository.save(client));
    }

    @Transactional
    public void delete(UUID id) {
        if (!clientRepository.existsById(id)) {
            throw new ResourceNotFoundException("Client not found: " + id);
        }
        clientRepository.deleteById(id);
    }

    private Client findOrThrow(UUID id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + id));
    }

    private String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private ClientDto toDto(Client c) {
        return new ClientDto(
                c.getId(),
                c.getCompanyName(),
                c.getContactName(),
                c.getEmail(),
                c.getPhone(),
                c.getAddress(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
