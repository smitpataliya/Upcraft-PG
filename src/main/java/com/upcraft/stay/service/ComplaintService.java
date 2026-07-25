package com.upcraft.stay.service;

import com.upcraft.stay.domain.entity.*;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.dto.*;
import com.upcraft.stay.repository.ComplaintRepository;
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
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;

    @Transactional
    public ComplaintDto raise(String memberEmail, CreateComplaintRequest req) {
        User member = userRepository.findByEmail(memberEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        Complaint complaint = Complaint.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .category(req.getCategory())
                .priority(req.getPriority())
                .member(member)
                .build();

        ComplaintTimeline initial = ComplaintTimeline.builder()
                .label("Complaint raised")
                .updatedBy(member.getName())
                .complaint(complaint)
                .build();
        complaint.getTimeline().add(initial);

        return toDto(complaintRepository.save(complaint));
    }

    @Transactional(readOnly = true)
    public List<ComplaintDto> getMyComplaints(String email) {
        User member = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        return complaintRepository.findByMemberOrderByCreatedAtDesc(member)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ComplaintDto> getAllComplaints() {
        return complaintRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public ComplaintDto updateStatus(UUID id, UpdateComplaintStatusRequest req, String ownerEmail) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found"));

        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));

        complaint.setStatus(req.getStatus());

        String note = req.getNote() != null ? req.getNote() : "Status changed to " + req.getStatus();
        ComplaintTimeline timeline = ComplaintTimeline.builder()
                .label(note)
                .updatedBy(owner.getName())
                .complaint(complaint)
                .build();
        complaint.getTimeline().add(timeline);

        return toDto(complaintRepository.save(complaint));
    }

    public ComplaintDto toDto(Complaint c) {
        return ComplaintDto.builder()
                .id(c.getId())
                .title(c.getTitle())
                .description(c.getDescription())
                .category(c.getCategory())
                .priority(c.getPriority())
                .status(c.getStatus())
                .memberName(c.getMember().getName())
                .memberEmail(c.getMember().getEmail())
                .timeline(c.getTimeline().stream()
                        .map(t -> ComplaintDto.TimelineDto.builder()
                                .label(t.getLabel())
                                .updatedBy(t.getUpdatedBy())
                                .at(t.getAt())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(c.getCreatedAt())
                .build();
    }
}
