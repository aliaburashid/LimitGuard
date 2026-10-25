package com.example.limitguard.model;

import jakarta.persistence.*;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "corporate_group_memberships")

// A membership connects a corporate group to a counterparty
// One corporate group can have multiple counterparties,
// and a counterparty can belong to only one corporate groups
public class CorporateGroupMembership {

    // primary key: a unique ID for each membership
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // one corporate group can have many counterparties
    // each membership belongs to one corporate group
    @ManyToOne
    @JoinColumn(name = "corporate_group_id", nullable = false)
    private CorporateGroup corporateGroup;

    // each counterparty can belong to only one corporate group
    // unique = true prevents assigning the same counterparty to multiple groups
    @OneToOne
    @JoinColumn(name = "counterparty_id", nullable = false, unique = true)
    private Counterparty counterparty;
}