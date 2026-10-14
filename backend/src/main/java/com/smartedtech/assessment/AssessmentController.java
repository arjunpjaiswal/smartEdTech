package com.smartedtech.assessment;

import com.smartedtech.assessment.dto.*;
import com.smartedtech.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @PostMapping("/classrooms/{classroomId}/assessments")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<AssessmentDto> createAssessment(
            @PathVariable Long classroomId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateAssessmentRequest request) {

        AssessmentDto dto = assessmentService.createAssessment(classroomId, request, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/classrooms/{classroomId}/assessments")
    public ResponseEntity<Page<AssessmentDto>> listAssessments(
            @PathVariable Long classroomId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<AssessmentDto> page = assessmentService.listAssessments(classroomId, principal.getUser(), pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/assessments/{id}")
    public ResponseEntity<AssessmentDto> getAssessment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        AssessmentDto dto = assessmentService.getAssessment(id, principal.getUser());
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/assessments/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<AssessmentDto> updateAssessment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateAssessmentRequest request) {

        AssessmentDto dto = assessmentService.updateAssessment(id, request, principal.getUser());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/assessments/{id}/questions")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<QuestionDto> addQuestion(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateQuestionRequest request) {

        QuestionDto dto = assessmentService.addQuestion(id, request, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/assessments/{id}/questions/{questionId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<QuestionDto> updateQuestion(
            @PathVariable Long id,
            @PathVariable Long questionId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateQuestionRequest request) {

        QuestionDto dto = assessmentService.updateQuestion(id, questionId, request, principal.getUser());
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/assessments/{id}/questions/{questionId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable Long id,
            @PathVariable Long questionId,
            @AuthenticationPrincipal UserPrincipal principal) {

        assessmentService.deleteQuestion(id, questionId, principal.getUser());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/assessments/{id}/questions/reorder")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<Void> reorderQuestions(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ReorderQuestionsRequest request) {

        assessmentService.reorderQuestions(id, request, principal.getUser());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/assessments/{id}/apply-generated-questions")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<AssessmentDto> applyGeneratedQuestions(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ApplyGeneratedQuestionsRequest request) {

        AssessmentDto dto = assessmentService.applyGeneratedQuestions(id, request, principal.getUser());
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/assessments/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<AssessmentDto> publishAssessment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        AssessmentDto dto = assessmentService.publishAssessment(id, principal.getUser());
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/assessments/{id}/close")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<AssessmentDto> closeAssessment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        AssessmentDto dto = assessmentService.closeAssessment(id, principal.getUser());
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/assessments/{id}/archive")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<AssessmentDto> archiveAssessment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        AssessmentDto dto = assessmentService.archiveAssessment(id, principal.getUser());
        return ResponseEntity.ok(dto);
    }
}
