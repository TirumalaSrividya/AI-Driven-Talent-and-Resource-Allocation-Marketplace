package com.talentallocation.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "employee_skills",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "skill_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency_level", nullable = false)
    private ProficiencyLevel proficiencyLevel;

    @Column(name = "years_experience", precision = 4, scale = 1)
    private BigDecimal yearsExperience;

    @Column(name = "last_validated_at")
    private LocalDate lastValidatedAt;

    @Column(name = "certified")
    private boolean certified;

    public enum ProficiencyLevel {
        BEGINNER(1), INTERMEDIATE(2), ADVANCED(3), EXPERT(4);

        private final int numericValue;

        ProficiencyLevel(int value) {
            this.numericValue = value;
        }

        public int getNumericValue() {
            return numericValue;
        }

        public boolean meetsOrExceeds(ProficiencyLevel required) {
            return this.numericValue >= required.numericValue;
        }
    }
}
