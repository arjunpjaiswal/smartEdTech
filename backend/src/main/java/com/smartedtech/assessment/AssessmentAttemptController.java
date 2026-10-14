package com.smartedtech.assessment;

import com.smartedtech.analytics.dto.RemediationPlanDto;
import com.smartedtech.analytics.dto.WeakConceptDto;
import com.smartedtech.assessment.dto.AssessmentAttemptDto;
import com.smartedtech.assessment.dto.SaveAnswerRequest;
import com.smartedtech.assessment.dto.StudentAnswerDto;
import com.smartedtech.assessment.dto.SubmitAttemptRequest;
import com.smartedtech.evaluation.dto.EvaluationDto;
import com.smartedtech.evaluation.dto.UpdateEvaluationRequest;
import com.smartedtech.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AssessmentAttemptController {

    private final AssessmentAttemptService attemptService;

    public AssessmentAttemptController(AssessmentAttemptService attemptService) {
        this.attemptService = attemptService;
    }

    @PostMapping("/assessments/{assessmentId}/start")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<AssessmentAttemptDto> startAttempt(
            @PathVariable Long assessmentId,
            @AuthenticationPrincipal UserPrincipal principal) {

        AssessmentAttemptDto dto = attemptService.startAttempt(assessmentId, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/attempts/{attemptId}/answers")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<StudentAnswerDto> saveAnswer(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SaveAnswerRequest request) {

        StudentAnswerDto dto = attemptService.saveAnswer(attemptId, request, principal.getUser());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<AssessmentAttemptDto> submitAttempt(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) SubmitAttemptRequest request) {

        AssessmentAttemptDto dto = attemptService.submitAttempt(attemptId, request, principal.getUser());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/attempts/{attemptId}")
    public ResponseEntity<AssessmentAttemptDto> getAttempt(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal UserPrincipal principal) {

        AssessmentAttemptDto dto = attemptService.getAttempt(attemptId, principal.getUser());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/assessments/{assessmentId}/attempts")
    public ResponseEntity<List<AssessmentAttemptDto>> listAttempts(
            @PathVariable Long assessmentId,
            @AuthenticationPrincipal UserPrincipal principal) {

        List<AssessmentAttemptDto> list = attemptService.listAttempts(assessmentId, principal.getUser());
        return ResponseEntity.ok(list);
    }

    @PutMapping("/evaluations/{evaluationId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<EvaluationDto> updateEvaluation(
            @PathVariable Long evaluationId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateEvaluationRequest request) {

        EvaluationDto dto = attemptService.updateEvaluation(evaluationId, request, principal.getUser());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/attempts/{attemptId}/remediation")
    public ResponseEntity<RemediationPlanDto> getRemediationPlan(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal UserPrincipal principal) {

        RemediationPlanDto dto = attemptService.getRemediationPlan(attemptId, principal.getUser());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/students/{studentId}/weak-concepts")
    public ResponseEntity<List<WeakConceptDto>> getWeakConcepts(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long classroomId,
            @AuthenticationPrincipal UserPrincipal principal) {

        List<WeakConceptDto> list = attemptService.getWeakConceptsForStudent(studentId, classroomId, principal.getUser());
        return ResponseEntity.ok(list);
    }
}
