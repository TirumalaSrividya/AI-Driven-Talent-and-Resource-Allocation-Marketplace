-- V2__seed_data.sql
-- Synthetic seed data for development and demonstration

-- Skills
INSERT INTO skills (name, category, description) VALUES
('Java', 'Programming', 'Java programming language'),
('Spring Boot', 'Framework', 'Spring Boot microservices framework'),
('Python', 'Programming', 'Python programming language'),
('React', 'Frontend', 'React JavaScript library'),
('Angular', 'Frontend', 'Angular TypeScript framework'),
('Vue.js', 'Frontend', 'Vue.js progressive framework'),
('MySQL', 'Database', 'MySQL relational database'),
('PostgreSQL', 'Database', 'PostgreSQL relational database'),
('MongoDB', 'Database', 'MongoDB NoSQL database'),
('AWS', 'Cloud', 'Amazon Web Services'),
('Azure', 'Cloud', 'Microsoft Azure cloud platform'),
('Docker', 'DevOps', 'Docker containerization'),
('Kubernetes', 'DevOps', 'Kubernetes orchestration'),
('Machine Learning', 'AI/ML', 'Machine learning concepts and models'),
('Deep Learning', 'AI/ML', 'Neural network and deep learning'),
('Data Analysis', 'Analytics', 'Data analysis and visualization'),
('Agile/Scrum', 'Methodology', 'Agile and Scrum project management'),
('REST API Design', 'Architecture', 'RESTful API design patterns'),
('Microservices', 'Architecture', 'Microservices architecture'),
('SQL', 'Database', 'Structured Query Language'),
('TypeScript', 'Programming', 'TypeScript language'),
('Node.js', 'Backend', 'Node.js server-side JavaScript'),
('CI/CD', 'DevOps', 'Continuous Integration and Deployment'),
('Kafka', 'Messaging', 'Apache Kafka messaging platform'),
('Redis', 'Caching', 'Redis in-memory data store');

-- Employees
INSERT INTO employees (employee_code, name, email, location, employment_status, department, seniority_level) VALUES
('EMP001', 'Alice Johnson', 'alice.johnson@company.com', 'New York', 'ACTIVE', 'Engineering', 'Senior'),
('EMP002', 'Bob Smith', 'bob.smith@company.com', 'San Francisco', 'ACTIVE', 'Engineering', 'Mid-level'),
('EMP003', 'Carol Williams', 'carol.williams@company.com', 'Chicago', 'ACTIVE', 'Data Science', 'Senior'),
('EMP004', 'David Brown', 'david.brown@company.com', 'New York', 'ACTIVE', 'Frontend', 'Junior'),
('EMP005', 'Eva Martinez', 'eva.martinez@company.com', 'Austin', 'ACTIVE', 'Engineering', 'Lead'),
('EMP006', 'Frank Lee', 'frank.lee@company.com', 'San Francisco', 'ACTIVE', 'DevOps', 'Senior'),
('EMP007', 'Grace Chen', 'grace.chen@company.com', 'Chicago', 'ACTIVE', 'Data Science', 'Mid-level'),
('EMP008', 'Henry Wilson', 'henry.wilson@company.com', 'New York', 'ACTIVE', 'Engineering', 'Senior'),
('EMP009', 'Iris Patel', 'iris.patel@company.com', 'Austin', 'ACTIVE', 'Frontend', 'Senior'),
('EMP010', 'James Davis', 'james.davis@company.com', 'Chicago', 'ACTIVE', 'Engineering', 'Mid-level'),
('EMP011', 'Karen Moore', 'karen.moore@company.com', 'New York', 'ON_LEAVE', 'Engineering', 'Senior'),
('EMP012', 'Liam Taylor', 'liam.taylor@company.com', 'San Francisco', 'ACTIVE', 'DevOps', 'Mid-level');

-- Employee skills
-- Alice Johnson (Java backend expert)
INSERT INTO employee_skills (employee_id, skill_id, proficiency_level, years_experience, last_validated_at, certified) VALUES
(1, 1, 'EXPERT', 8.0, '2024-01-15', TRUE),
(1, 2, 'EXPERT', 6.0, '2024-01-15', TRUE),
(1, 18, 'ADVANCED', 5.0, '2023-09-01', FALSE),
(1, 19, 'ADVANCED', 4.0, '2023-09-01', FALSE),
(1, 7, 'INTERMEDIATE', 3.0, '2023-06-01', FALSE),
(1, 20, 'ADVANCED', 6.0, '2024-01-15', FALSE);

-- Bob Smith (Full-stack)
INSERT INTO employee_skills (employee_id, skill_id, proficiency_level, years_experience, last_validated_at, certified) VALUES
(2, 1, 'INTERMEDIATE', 3.0, '2023-12-01', FALSE),
(2, 2, 'INTERMEDIATE', 2.5, '2023-12-01', FALSE),
(2, 4, 'ADVANCED', 4.0, '2024-02-01', FALSE),
(2, 21, 'ADVANCED', 3.0, '2024-02-01', FALSE),
(2, 7, 'INTERMEDIATE', 2.0, '2023-08-01', FALSE),
(2, 17, 'ADVANCED', 3.0, '2023-10-01', FALSE);

-- Carol Williams (Data scientist)
INSERT INTO employee_skills (employee_id, skill_id, proficiency_level, years_experience, last_validated_at, certified) VALUES
(3, 3, 'EXPERT', 7.0, '2024-03-01', TRUE),
(3, 14, 'EXPERT', 5.0, '2024-03-01', TRUE),
(3, 15, 'ADVANCED', 4.0, '2024-01-01', FALSE),
(3, 16, 'EXPERT', 6.0, '2024-03-01', FALSE),
(3, 20, 'ADVANCED', 5.0, '2023-11-01', FALSE);

-- David Brown (Junior frontend)
INSERT INTO employee_skills (employee_id, skill_id, proficiency_level, years_experience, last_validated_at, certified) VALUES
(4, 4, 'INTERMEDIATE', 1.5, '2024-01-01', FALSE),
(4, 21, 'BEGINNER', 1.0, '2024-01-01', FALSE),
(4, 6, 'BEGINNER', 0.5, '2023-09-01', FALSE),
(4, 20, 'INTERMEDIATE', 1.5, '2024-01-01', FALSE);

-- Eva Martinez (Lead engineer)
INSERT INTO employee_skills (employee_id, skill_id, proficiency_level, years_experience, last_validated_at, certified) VALUES
(5, 1, 'EXPERT', 10.0, '2024-02-15', TRUE),
(5, 2, 'EXPERT', 8.0, '2024-02-15', TRUE),
(5, 19, 'EXPERT', 7.0, '2024-02-15', TRUE),
(5, 18, 'EXPERT', 8.0, '2024-02-15', TRUE),
(5, 10, 'ADVANCED', 4.0, '2023-12-01', TRUE),
(5, 12, 'ADVANCED', 3.0, '2023-12-01', FALSE),
(5, 17, 'EXPERT', 8.0, '2024-02-15', FALSE);

-- Frank Lee (DevOps)
INSERT INTO employee_skills (employee_id, skill_id, proficiency_level, years_experience, last_validated_at, certified) VALUES
(6, 12, 'EXPERT', 6.0, '2024-03-01', TRUE),
(6, 13, 'EXPERT', 5.0, '2024-03-01', TRUE),
(6, 10, 'EXPERT', 6.0, '2024-03-01', TRUE),
(6, 23, 'ADVANCED', 4.0, '2024-01-01', FALSE),
(6, 11, 'INTERMEDIATE', 2.0, '2023-10-01', FALSE);

-- Grace Chen (Data analyst)
INSERT INTO employee_skills (employee_id, skill_id, proficiency_level, years_experience, last_validated_at, certified) VALUES
(7, 3, 'ADVANCED', 4.0, '2023-11-01', FALSE),
(7, 16, 'ADVANCED', 4.0, '2023-11-01', FALSE),
(7, 14, 'INTERMEDIATE', 2.0, '2023-08-01', FALSE),
(7, 20, 'ADVANCED', 3.0, '2023-11-01', FALSE),
(7, 7, 'INTERMEDIATE', 2.0, '2023-06-01', FALSE);

-- Henry Wilson (Java/Cloud)
INSERT INTO employee_skills (employee_id, skill_id, proficiency_level, years_experience, last_validated_at, certified) VALUES
(8, 1, 'EXPERT', 9.0, '2024-01-01', TRUE),
(8, 2, 'ADVANCED', 5.0, '2024-01-01', FALSE),
(8, 10, 'ADVANCED', 3.0, '2023-12-01', TRUE),
(8, 24, 'INTERMEDIATE', 2.0, '2023-09-01', FALSE),
(8, 19, 'ADVANCED', 5.0, '2024-01-01', FALSE);

-- Iris Patel (Senior frontend)
INSERT INTO employee_skills (employee_id, skill_id, proficiency_level, years_experience, last_validated_at, certified) VALUES
(9, 4, 'EXPERT', 6.0, '2024-02-01', FALSE),
(9, 5, 'ADVANCED', 4.0, '2024-02-01', FALSE),
(9, 21, 'EXPERT', 5.0, '2024-02-01', FALSE),
(9, 22, 'ADVANCED', 3.0, '2023-12-01', FALSE),
(9, 17, 'ADVANCED', 4.0, '2023-10-01', FALSE);

-- James Davis (Mid-level backend)
INSERT INTO employee_skills (employee_id, skill_id, proficiency_level, years_experience, last_validated_at, certified) VALUES
(10, 1, 'ADVANCED', 4.0, '2023-10-01', FALSE),
(10, 2, 'ADVANCED', 3.5, '2023-10-01', FALSE),
(10, 7, 'ADVANCED', 3.0, '2023-10-01', FALSE),
(10, 20, 'ADVANCED', 4.0, '2023-10-01', FALSE),
(10, 9, 'INTERMEDIATE', 1.5, '2023-07-01', FALSE);

-- Liam Taylor (DevOps mid)
INSERT INTO employee_skills (employee_id, skill_id, proficiency_level, years_experience, last_validated_at, certified) VALUES
(12, 12, 'ADVANCED', 3.0, '2023-11-01', FALSE),
(12, 13, 'INTERMEDIATE', 2.0, '2023-11-01', FALSE),
(12, 23, 'ADVANCED', 3.0, '2023-11-01', FALSE),
(12, 10, 'INTERMEDIATE', 2.0, '2023-08-01', FALSE);

-- Sample project demands
INSERT INTO project_demands (project_name, role, description, start_date, end_date, location, allocation_percent, priority, status, created_by) VALUES
('Project Phoenix', 'Senior Java Developer', 'Backend microservices for Phoenix platform', '2026-11-01', '2027-03-31', 'New York', 100, 'HIGH', 'OPEN', 'admin'),
('DataVault Analytics', 'Data Scientist', 'ML pipeline development for DataVault', '2026-11-15', '2027-06-30', 'Chicago', 80, 'CRITICAL', 'OPEN', 'admin'),
('CloudMigrate 360', 'DevOps Engineer', 'Cloud migration and CI/CD setup', '2026-12-01', '2027-05-31', 'San Francisco', 100, 'HIGH', 'OPEN', 'admin'),
('UX Revamp', 'Frontend Developer', 'React-based UI redesign', '2026-11-01', '2027-01-31', 'New York', 50, 'MEDIUM', 'OPEN', 'admin'),
('API Gateway Project', 'Lead Architect', 'Microservices API gateway design', '2027-01-01', '2027-06-30', 'Austin', 100, 'CRITICAL', 'OPEN', 'admin');

-- Demand required skills
-- Project Phoenix: Java, Spring Boot, Microservices, REST API
INSERT INTO demand_required_skills (demand_id, skill_id, minimum_proficiency, is_mandatory) VALUES
(1, 1, 'ADVANCED', TRUE),
(1, 2, 'ADVANCED', TRUE),
(1, 19, 'INTERMEDIATE', TRUE),
(1, 18, 'INTERMEDIATE', FALSE),
(1, 20, 'INTERMEDIATE', FALSE);

-- DataVault: Python, ML, Data Analysis
INSERT INTO demand_required_skills (demand_id, skill_id, minimum_proficiency, is_mandatory) VALUES
(2, 3, 'ADVANCED', TRUE),
(2, 14, 'ADVANCED', TRUE),
(2, 16, 'INTERMEDIATE', TRUE),
(2, 15, 'INTERMEDIATE', FALSE);

-- CloudMigrate: Docker, K8s, AWS, CI/CD
INSERT INTO demand_required_skills (demand_id, skill_id, minimum_proficiency, is_mandatory) VALUES
(3, 12, 'ADVANCED', TRUE),
(3, 13, 'INTERMEDIATE', TRUE),
(3, 10, 'INTERMEDIATE', TRUE),
(3, 23, 'INTERMEDIATE', FALSE);

-- UX Revamp: React, TypeScript
INSERT INTO demand_required_skills (demand_id, skill_id, minimum_proficiency, is_mandatory) VALUES
(4, 4, 'INTERMEDIATE', TRUE),
(4, 21, 'BEGINNER', FALSE);

-- API Gateway: Java, Spring Boot, Microservices, REST, AWS
INSERT INTO demand_required_skills (demand_id, skill_id, minimum_proficiency, is_mandatory) VALUES
(5, 1, 'EXPERT', TRUE),
(5, 2, 'EXPERT', TRUE),
(5, 19, 'EXPERT', TRUE),
(5, 18, 'EXPERT', TRUE),
(5, 10, 'ADVANCED', FALSE);
