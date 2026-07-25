package com.upcraft.stay.service;

import com.upcraft.stay.domain.entity.*;
import com.upcraft.stay.domain.enums.QueryStatus;
import com.upcraft.stay.dto.*;
import com.upcraft.stay.repository.MemberQueryRepository;
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
public class QueryService {

    private final MemberQueryRepository queryRepository;
    private final UserRepository userRepository;

    @Transactional
    public QueryDto create(String memberEmail, CreateQueryRequest req) {
        User member = userRepository.findByEmail(memberEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        MemberQuery query = MemberQuery.builder()
                .subject(req.getSubject())
                .description(req.getDescription())
                .category(req.getCategory())
                .member(member)
                .build();

        return toDto(queryRepository.save(query));
    }

    @Transactional(readOnly = true)
    public List<QueryDto> getMyQueries(String email) {
        User member = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        return queryRepository.findByMemberOrderByCreatedAtDesc(member)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QueryDto> getAllQueries() {
        return queryRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public QueryDto addReply(UUID queryId, ReplyRequest req, String senderEmail) {
        MemberQuery query = queryRepository.findById(queryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Query not found"));
        if (query.getStatus() == QueryStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query is already closed");
        }

        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        QueryReply reply = QueryReply.builder()
                .message(req.getMessage())
                .senderName(sender.getName())
                .senderRole(sender.getRole())
                .query(query)
                .build();
        query.getReplies().add(reply);
        query.setStatus(QueryStatus.REPLIED);

        return toDto(queryRepository.save(query));
    }

    @Transactional
    public QueryDto closeQuery(UUID queryId) {
        MemberQuery query = queryRepository.findById(queryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Query not found"));
        query.setStatus(QueryStatus.CLOSED);
        return toDto(queryRepository.save(query));
    }

    public QueryDto toDto(MemberQuery q) {
        return QueryDto.builder()
                .id(q.getId())
                .subject(q.getSubject())
                .description(q.getDescription())
                .category(q.getCategory())
                .status(q.getStatus())
                .memberName(q.getMember().getName())
                .memberEmail(q.getMember().getEmail())
                .replies(q.getReplies().stream()
                        .map(r -> QueryDto.ReplyDto.builder()
                                .id(r.getId())
                                .message(r.getMessage())
                                .senderName(r.getSenderName())
                                .senderRole(r.getSenderRole())
                                .at(r.getAt())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(q.getCreatedAt())
                .build();
    }
}
