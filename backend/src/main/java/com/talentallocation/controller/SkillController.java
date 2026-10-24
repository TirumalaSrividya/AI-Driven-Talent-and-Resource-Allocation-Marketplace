package com.talentallocation.controller;

import com.talentallocation.domain.Skill;
import com.talentallocation.dto.*;
import com.talentallocation.exception.ResourceNotFoundException;
import com.talentallocation.repository.SkillRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SkillController {

    private final SkillService skillService;

    @PostMapping
    public ResponseEntity<SkillDto.Response> createSkill(@Valid @RequestBody SkillDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(skillService.createSkill(request));
    }

    @GetMapping
    public ResponseEntity<List<SkillDto.Response>> getAllSkills(
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(skillService.getSkills(category));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SkillDto.Response> getSkill(@PathVariable Long id) {
        return ResponseEntity.ok(skillService.getSkill(id));
    }

    @Service
    @RequiredArgsConstructor
    @Transactional
    public static class SkillService {
        private final SkillRepository skillRepository;

        public SkillDto.Response createSkill(SkillDto.Request request) {
            Skill skill = Skill.builder()
                .name(request.getName())
                .category(request.getCategory())
                .description(request.getDescription())
                .build();
            Skill saved = skillRepository.save(skill);
            return toResponse(saved);
        }

        @Transactional(readOnly = true)
        public List<SkillDto.Response> getSkills(String category) {
            List<Skill> skills = category != null
                ? skillRepository.findByCategoryOrderByName(category)
                : skillRepository.findAll();
            return skills.stream().map(this::toResponse).collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public SkillDto.Response getSkill(Long id) {
            return toResponse(skillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Skill", id)));
        }

        public SkillDto.Response toResponse(Skill s) {
            return SkillDto.Response.builder()
                .id(s.getId())
                .name(s.getName())
                .category(s.getCategory())
                .description(s.getDescription())
                .build();
        }
    }
}
