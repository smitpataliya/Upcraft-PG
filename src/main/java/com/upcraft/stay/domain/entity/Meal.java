package com.upcraft.stay.domain.entity;

import com.upcraft.stay.domain.enums.MealType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MealType type;

    private String servingTime;

    @Builder.Default
    @Column(nullable = false)
    private boolean veg = true;

    @Builder.Default
    @Column(nullable = false)
    private boolean available = true;

    @Column(nullable = false)
    private LocalDate dateScheduled;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
