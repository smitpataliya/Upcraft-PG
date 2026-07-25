package com.upcraft.stay.domain.entity;

import com.upcraft.stay.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "query_replies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryReply {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private String senderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role senderRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "query_id", nullable = false)
    private MemberQuery query;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime at;
}
