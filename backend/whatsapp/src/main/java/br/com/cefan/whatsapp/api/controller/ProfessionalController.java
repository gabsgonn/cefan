package br.com.cefan.whatsapp.api.controller;

import br.com.cefan.whatsapp.api.dto.ProfessionalDto;
import br.com.cefan.whatsapp.application.service.ProfessionalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/professionals")
@RequiredArgsConstructor
public class ProfessionalController {

    private final ProfessionalService professionalService;

    @GetMapping
    public ResponseEntity<List<ProfessionalDto>> listAll(
            @RequestParam(defaultValue = "false") boolean all) {
        boolean onlyActive = !all;
        return ResponseEntity.ok(professionalService.listAll(onlyActive));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfessionalDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(professionalService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ProfessionalDto> create(@Valid @RequestBody ProfessionalDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(professionalService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ProfessionalDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProfessionalDto dto) {
        return ResponseEntity.ok(professionalService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        professionalService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
