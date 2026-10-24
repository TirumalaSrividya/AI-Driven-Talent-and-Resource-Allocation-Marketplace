package com.talentallocation.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "demand_required_skills",
       uniqueConstraints = @UniqueConstraint(columnNames = {"demand_id", "skill_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandRequiredSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demand_id", nullable = false)
    private ProjectDemand demand;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "minimum_proficiency", nullable = false)
    private EmployeeSkill.ProficiencyLevel minimumProficiency;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory;
}
