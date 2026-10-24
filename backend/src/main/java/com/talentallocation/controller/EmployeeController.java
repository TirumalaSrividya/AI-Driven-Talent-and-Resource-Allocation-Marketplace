package com.talentallocation.controller;

import com.talentallocation.dto.*;
import com.talentallocation.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EmployeeController {

    private final EmployeeService employeeService;

    /** POST /api/employees — Create employee */
    @PostMapping
    public ResponseEntity<EmployeeDto.Response> createEmployee(
            @Valid @RequestBody EmployeeDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(employeeService.createEmployee(request));
    }

    /** GET /api/employees — List all employees */
    @GetMapping
    public ResponseEntity<List<EmployeeDto.Response>> getAllEmployees() {
        return ResponseEntity.ok(employeeService.getAllEmployees());
    }

    /** GET /api/employees/{id} — Get employee by ID */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeDto.Response> getEmployee(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getEmployee(id));
    }

    /** POST /api/employees/{id}/skills — Add skill to employee */
    @PostMapping("/{id}/skills")
    public ResponseEntity<SkillDto.EmployeeSkillResponse> addSkill(
            @PathVariable Long id,
            @Valid @RequestBody SkillDto.AddSkillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(employeeService.addSkill(id, request));
    }

    /** GET /api/employees/{id}/skills — Get employee skills */
    @GetMapping("/{id}/skills")
    public ResponseEntity<List<SkillDto.EmployeeSkillResponse>> getSkills(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getEmployeeSkills(id));
    }
}
