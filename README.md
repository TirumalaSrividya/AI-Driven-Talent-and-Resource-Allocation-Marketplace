# AI-Driven Talent and Resource Allocation Marketplace

A resource allocation platform that matches employees to project demands using verified skills, availability, location, and timeline constraints, assisted by a transparent agentic AI staffing workflow.

--- 

## 1. Project Overview

The platform helps staffing managers identify suitable employees for project demands while preventing allocation conflicts and over-allocation.

The system provides:
- Employee and skill management
- Project demand management
- Deterministic candidate eligibility and scoring
- Date-range availability checking
- Allocation and capacity management
- Utilization and skill-gap reports
- AI-assisted staffing recommendations
The core business rules are implemented in the Java backend, while the AI layer is used to explain and assist with staffing decisions.

---

## 2. Technology Stack

| Layer              | Technology                                              |
| ------------------ | ------------------------------------------------------- |
| Backend            | Java 17, Spring Boot 3.2.5                              |
| Persistence        | Spring Data JPA, Hibernate                              |
| Database           | MySQL 8                                                 |
| Database Migration | Flyway                                                  |
| Frontend           | HTML5, CSS3, Vanilla JavaScript (ES6)                   |
| Testing            | JUnit 5, Mockito, Spring Boot Integration Tests         |
| Build              | Maven Wrapper                                           |
| Version Control    | Git                                                     |
| AI                 | Agentic AI simulation with controlled backend workflows |

---

## 3. Architecture

The system follows a layered architecture where business rules and capacity governance are strictly enforced by Java backend services:

```text
Browser / Frontend
       |
       | HTTP / REST
       v
REST Controllers
       |
       v
Service Layer
       |
       +---- Recommendation / AI Staffing Workflow
       |
       v
JPA Repositories
       |
       v
MySQL Database
```

**AI Workflow Placement**: The AI Staffing Assistant operates as an orchestration layer above core Java services. It invokes controlled backend tools to retrieve live database facts, evaluates candidates against established business rules, and requires human authorization before any allocation is committed.

---

## 4. Key Features

- **Demand Management** - Create and manage project demands with roles, dates, allocation percentage, priority, and location.
- **Employee & Skill Management** - Maintain employee profiles and skill/proficiency information.
- **Candidate Matching** - Identify candidates based on mandatory skills, proficiency, availability, location, and experience.
- **Availability Checking** - Check employee capacity for a requested date range.
- **Allocation Management** - Create, approve, and cancel allocations.
- **Conflict Prevention** - Prevent allocations that exceed the configured capacity.
- **Utilization Reporting** - View employee utilization and department-level information.
- **Skill-Gap Reporting** - Identify gaps between required skills and available qualified resources.
- **Skills Matrix** - Group active resources by skill and proficiency level.
- **AI Staffing Assistant** - Provide evidence-based staffing recommendations and explanations.
- **Training Recommendations** - Suggest learning actions for identified skill gaps.

---

## 5. REST APIs

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/demands` | Create a project demand |
| GET | `/api/demands` | Retrieve demands with filters |
| GET | `/api/demands/{id}` | Retrieve a specific demand |
| GET | `/api/demands/open-by-priority` | Open demands ordered by priority |
| GET | `/api/demands/unallocated` | Open demands without active/pending allocations |
| POST | `/api/demands/{id}/recommendations` | Generate candidate recommendations |
| GET | `/api/employees` | List employees |
| POST | `/api/employees` | Create an employee |
| GET | `/api/employees/{id}` | Retrieve employee details |
| POST | `/api/employees/{id}/skills` | Add a skill to an employee |
| GET | `/api/skills` | Retrieve available skills |
| GET | `/api/resources/available` | Find available resources |
| POST | `/api/allocations` | Create an allocation |
| GET | `/api/allocations` | Retrieve allocations |
| PUT | `/api/allocations/{id}/approve` | Approve an allocation |
| PUT | `/api/allocations/{id}/cancel` | Cancel an allocation |
| GET | `/api/reports/utilization` | Employee utilization report |
| GET | `/api/reports/skill-gaps` | Skill-gap report |
| GET | `/api/reports/skills-matrix` | Resources grouped by skill/proficiency |
| POST | `/api/ai/staffing-advice` | AI staffing assistance |
| POST | `/api/ai/approve-allocation` | Approve an AI-generated allocation recommendation |

---

## 6. Business Rules

### Demand Validation

A demand must contain the following valid fields:

- Project name
- Role
- Start date
- End date
- Allocation percentage between 1% and 100%

The end date cannot be earlier than the start date.

### Candidate Eligibility

Candidates are evaluated based on:

- Active employment status
- Mandatory skills
- Required proficiency
- Available capacity
- Requested date range

A candidate missing a mandatory skill is not considered eligible.

### Allocation Capacity

- An employee cannot be allocated beyond **100% capacity** for overlapping allocation periods.
- Conflicting allocations are rejected by the backend business logic.

### Candidate Scoring
Candidate scores are calculated using four weighted factors:

```text
Score =
    40% Skill Match
  + 30% Availability
  + 15% Location Fit
  + 15% Experience
```
The scoring factors are calculated from stored employee, skill, demand, and allocation data.

### Approval
- Allocations use a pending/approval flow before becoming active.
- Capacity is rechecked when an allocation is approved.

### Missing Data
The AI workflow uses available stored information. Missing skill or availability information is reported rather than invented.

---

## 7. Database

```text
Database: resource_allocation
```

### Main Tables

- `employees`
- `skills`
- `employee_skills`
- `project_demands`
- `demand_required_skills`
- `allocations`
- `training_recommendations`
- `audit_log`

**1. Database/tables**

<img src="Screenshot 2026-10-24 011641.png" width="700" height = "700">

**2. Employee data**

<img src="Screenshot 2026-10-24 011714.png" width="700" height = "700">

**3. Project demand data**

<img src="Screenshot 2026-10-24 011849.png" width="700" height = "700">


### Database Migrations

Flyway is used for database initialization:

```text
V1__init_schema.sql
V2__seed_data.sql
```

The project uses synthetic seed data for employees, skills, demands, and related records.

---

## 8. SQL Requirements

| # | Requirement | Implementation |
|---|---|---|
| 1 | Open demands by role and priority | `findOpenDemandsOrderedByPriority()` |
| 2 | Resources grouped by skill and proficiency | `findResourcesGroupedBySkillAndProficiency()` |
| 3 | Available resources for a date range | `findAvailableBySkillAndDates()` |
| 4 | Utilization by employee and department | `findUtilizationByEmployeeAndDepartment()` |
| 5 | Open/unallocated demands | `findOpenDemandsWithNoAllocation()` |
| 6 | Skill gaps grouped by category | `findSkillGapsGroupedByCategory()` |

### Queries

**Employees Grouped by Department**
<img src="Screenshot 2026-10-24 165307.png" width="700" height = "700">

**Resources Grouped by Skill and Proficiency**
<img src="Screenshot 2026-10-24 012204.png" width="400" height = "700">

---

## 9. Setup & Run

### Prerequisites
- Java 17+ (JDK)
- MySQL 8.x

### 1. Database Setup
Create the database in Mysql Workbench

```
CREATE DATABASE resource_allocation;
```

Configure the MySQL connection in the backend application properties.

Flyway applies the database migrations when the backend starts.

### 2. Start Backend


```powershell
.\mvnw.cmd spring-boot:run
```

The Spring Boot backend will start on port 8080.
```
http://localhost:8080
```

### 3. Start Frontend

```powershell
python -m http.server 5500 --directory "frontend"
```

- **Web UI URL**: `http://localhost:5500`

---

## 10. Testing

Execute the automated test suite using Maven:

```powershell
.\mvnw.cmd test
```

### Coverage
Run the automated test suite from the backend directory

```
.\mvnw.cmd test
```

The tests cover areas including:
- Demand validation
- Candidate recommendation and scoring
- Allocation rules
- Capacity checks
- Date-range availability
- Allocation conflicts
- Cancellation
- SQL/reporting functionality
- REST/API integration behavior
---

Verified Result
The current verified test run completed successfully

```
Tests run: 34
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

## 11. AI Staffing Workflow

The project includes an Agentic AI staffing workflow implemented as a controlled simulation.


### AI Workflow
```
Staffing Request
      ↓
Identify Staffing Goal
      ↓
Retrieve Backend Data
      ↓
Check Candidate Eligibility
      ↓
Calculate Candidate Scores
      ↓
Generate AI Explanation
      ↓
Identify Skill Gaps / Training Suggestions
      ↓
Approval
      ↓
Backend Revalidation
      ↓
Allocation
```

The backend remains responsible for enforcing eligibility, capacity, and allocation rules.

This prototype uses an AI simulation rather than a live external LLM integration, the workflow is deterministic and suitable for local demonstration.

---

## 12. Project Structure

```text
Resource Allocation/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/talentallocation/
│   │   │   │   ├── controller/         
│   │   │   │   ├── domain/             
│   │   │   │   ├── dto/                
│   │   │   │   ├── exception/          
│   │   │   │   ├── repository/         
│   │   │   │   └── service/             
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── db/migration/       
│   │   └── test/java/com/talentallocation/
│   │       ├── integration/            
│   │       └── service/                
│   ├── pom.xml                        
│   └── mvnw.cmd                        
├── frontend/
│   ├── css/                           
│   ├── js/                            
│   └── index.html                     
├── postman/
│   └── talent_allocation_marketplace.postman_collection.json  
└── README.md                           
```

---

## 13. AI Usage & Limitations

### Development Assistance
AI tools were used during development for:
- Initial code scaffolding
- JPA and SQL query development
- Test generation
- Debugging and troubleshooting
- Documentation assistance
All implemented functionality was validated through automated tests and manual API/database verification.

### Defects Identified and Fixed
During candidate ranking validation, a comparator issue could cause eligibility ordering to be inverted when multiple reversed comparators were chained.

The ranking logic was corrected so that eligible candidates are prioritized before comparing their scores.

### Current Limitation
The AI staffing assistant currently uses a deterministic Java simulation rather than a live external LLM API.

A production implementation could replace the simulation with an actual LLM while retaining the existing controlled backend tools, validation, business rules, and approval flow.

