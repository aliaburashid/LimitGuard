package com.example.limitguard.model;

import com.example.limitguard.enums.CounterpartyStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "counterparties")
public class Counterparty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // stores the name of the counterparty
    @Column(nullable = false, unique = true)
    private String name;

    // stores the current status of the counterparty
    // every new counterparty starts as ACTIVE
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CounterpartyStatus status = CounterpartyStatus.ACTIVE;

    // automatically stores when the counterparty was created
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // automatically stores when the counterparty was last updated
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
