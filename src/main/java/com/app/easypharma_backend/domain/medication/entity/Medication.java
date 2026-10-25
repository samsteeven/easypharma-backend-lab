package com.app.easypharma_backend.domain.medication.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "medications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Medication {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "generic_name", length = 200)
    private String genericName;

    @Enumerated(EnumType.STRING)
    @Column(name = "therapeutic_class", nullable = false, length = 50)
    private TherapeuticClass therapeuticClass;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "description")
    private String description;

    @Column(name = "dosage", length = 100)
    private String dosage;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "notice_pdf_url", length = 500)
    private String noticePdfUrl;

    @Builder.Default
    @Column(name = "requires_prescription", nullable = false)
    private Boolean requiresPrescription = false;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "symptoms")
    private String symptoms;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}