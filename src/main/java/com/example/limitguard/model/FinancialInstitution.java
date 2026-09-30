package com.example.limitguard.model;

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
//Tells Spring that this class will be stored in the database
@Entity
// The table in PostgreSQL will be called financial_institutions
@Table(name = "financial_institutions")
public class FinancialInstitution {

    // The unique ID for each financial institution
    @Id
    // PostgreSQL automatically generates the ID for us.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The name of the financial institution
    // nullable: means the name cannot be empty/null in the database.
    @Column(nullable = false, unique = true)
    private String name;

    // Shows if the financial institution is active or inactive
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinancialInstitutionStatus status = FinancialInstitutionStatus.ACTIVE;

    // Saves when the financial institution was created
    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    // Saves when the financial institution was last updated
    // no null because it's not necessary updated
    @Column
    @UpdateTimestamp
    private LocalDateTime updatedAt;

}

