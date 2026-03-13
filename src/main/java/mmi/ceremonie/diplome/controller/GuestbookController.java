package mmi.ceremonie.diplome.controller;

import lombok.RequiredArgsConstructor;
import mmi.ceremonie.diplome.model.GuestbookMessage;
import mmi.ceremonie.diplome.repository.GuestbookRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GuestbookController {

    private final GuestbookRepository repository;

    @GetMapping("/guestbook")
    @PreAuthorize("hasRole('ADMIN')")
    public List<GuestbookMessage> getAllMessagesForAdmin() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/public/guestbook")
    public List<GuestbookMessage> getAllMessages() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/public/guestbook")
    public GuestbookMessage postMessage(@RequestBody GuestbookMessage message) {
        message.setCreatedAt(LocalDateTime.now());
        return repository.save(message);
    }

    @DeleteMapping("/guestbook/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteMessage(@PathVariable Long id) {
        repository.deleteById(id);
    }
}
