-- V1__init_schema.sql
-- AI-Driven Talent and Resource Allocation Marketplace
-- Initial database schema

-- Skills reference table
CREATE TABLE skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_skills_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Employees table
CREATE TABLE employees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    location VARCHAR(100) NOT NULL,
    employment_status ENUM('ACTIVE','INACTIVE','ON_LEAVE') NOT NULL DEFAULT 'ACTIVE',
    department VARCHAR(100),
    seniority_level VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_employees_status (employment_status),
    INDEX idx_employees_location (location)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Employee skills (many-to-many with proficiency)
CREATE TABLE employee_skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    proficiency_level ENUM('BEGINNER','INTERMEDIATE','ADVANCED','EXPERT') NOT NULL,
    years_experience DECIMAL(4,1),
    last_validated_at DATE,
    certified BOOLEAN DEFAULT FALSE,
    UNIQUE KEY uk_employee_skill (employee_id, skill_id),
    FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE CASCADE,
    INDEX idx_es_skill (skill_id),
    INDEX idx_es_proficiency (proficiency_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Project demands
CREATE TABLE project_demands (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_name VARCHAR(200) NOT NULL,
    role VARCHAR(100) NOT NULL,
    description TEXT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    location VARCHAR(100),
    allocation_percent INT NOT NULL,
    priority ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL DEFAULT 'MEDIUM',
    status ENUM('DRAFT','OPEN','IN_PROGRESS','FULFILLED','CANCELLED') NOT NULL DEFAULT 'OPEN',
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_allocation_percent CHECK (allocation_percent BETWEEN 1 AND 100),
    CONSTRAINT chk_dates CHECK (end_date >= start_date),
    INDEX idx_pd_status (status),
    INDEX idx_pd_role (role),
    INDEX idx_pd_dates (start_date, end_date),
    INDEX idx_pd_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Required skills for a demand (many-to-many)
CREATE TABLE demand_required_skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    demand_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    minimum_proficiency ENUM('BEGINNER','INTERMEDIATE','ADVANCED','EXPERT') NOT NULL DEFAULT 'INTERMEDIATE',
    is_mandatory BOOLEAN DEFAULT TRUE,
    UNIQUE KEY uk_demand_skill (demand_id, skill_id),
    FOREIGN KEY (demand_id) REFERENCES project_demands(id) ON DELETE CASCADE,
    FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Allocations
CREATE TABLE allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    demand_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    allocation_percent INT NOT NULL,
    status ENUM('PENDING','ACTIVE','CANCELLED','COMPLETED') NOT NULL DEFAULT 'PENDING',
    approved_by VARCHAR(100),
    approved_at TIMESTAMP NULL,
    cancelled_by VARCHAR(100),
    cancelled_at TIMESTAMP NULL,
    cancellation_reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_alloc_percent CHECK (allocation_percent BETWEEN 1 AND 100),
    CONSTRAINT chk_alloc_dates CHECK (end_date >= start_date),
    FOREIGN KEY (employee_id) REFERENCES employees(id),
    FOREIGN KEY (demand_id) REFERENCES project_demands(id),
    INDEX idx_alloc_employee (employee_id),
    INDEX idx_alloc_demand (demand_id),
    INDEX idx_alloc_status (status),
    INDEX idx_alloc_dates (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Training recommendations
CREATE TABLE training_recommendations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    demand_id BIGINT,
    reason TEXT NOT NULL,
    suggested_course VARCHAR(300),
    estimated_duration_days INT,
    status ENUM('SUGGESTED','ACCEPTED','IN_PROGRESS','COMPLETED','DECLINED') NOT NULL DEFAULT 'SUGGESTED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    FOREIGN KEY (skill_id) REFERENCES skills(id),
    FOREIGN KEY (demand_id) REFERENCES project_demands(id) ON DELETE SET NULL,
    INDEX idx_tr_employee (employee_id),
    INDEX idx_tr_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Audit log for all AI agent actions
CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    action VARCHAR(100) NOT NULL,
    performed_by VARCHAR(100),
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
