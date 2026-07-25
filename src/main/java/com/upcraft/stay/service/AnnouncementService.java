package com.upcraft.stay.service;

import com.upcraft.stay.domain.entity.Announcement;
import com.upcraft.stay.dto.AnnouncementDto;
import com.upcraft.stay.dto.CreateAnnouncementRequest;
import com.upcraft.stay.repository.AnnouncementRepository;
import com.upcraft.stay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;

    @Transactional
    public AnnouncementDto create(String ownerEmail, CreateAnnouncementRequest req) {
        var owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));

        Announcement announcement = Announcement.builder()
                .title(req.getTitle())
                .body(req.getBody())
                .category(req.getCategory())
                .pinned(req.isPinned())
                .author(owner.getName())
                .build();

        return toDto(announcementRepository.save(announcement));
    }

    @Transactional
    public AnnouncementDto update(UUID id, CreateAnnouncementRequest req) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Announcement not found"));
        announcement.setTitle(req.getTitle());
        announcement.setBody(req.getBody());
        announcement.setCategory(req.getCategory());
        announcement.setPinned(req.isPinned());
        return toDto(announcementRepository.save(announcement));
    }

    @Transactional(readOnly = true)
    public List<AnnouncementDto> getAll() {
        return announcementRepository.findAllByOrderByPinnedDescCreatedAtDesc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public void delete(UUID id) {
        announcementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Announcement not found"));
        announcementRepository.deleteById(id);
    }

    public AnnouncementDto toDto(Announcement a) {
        return AnnouncementDto.builder()
                .id(a.getId())
                .title(a.getTitle())
                .body(a.getBody())
                .category(a.getCategory())
                .pinned(a.isPinned())
                .author(a.getAuthor())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
