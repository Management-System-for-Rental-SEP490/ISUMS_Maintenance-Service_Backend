package com.isums.maintainservice.domains.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plan_houses",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"plan_id","house_id"})
        })
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class PlanHouse {

    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    private UUID planId;

    private UUID houseId;

    private Instant createdAt;
}
