package com.smartedtech.material;

import com.smartedtech.material.dto.MaterialDto;
import com.smartedtech.material.dto.UpdateMaterialRequest;
import com.smartedtech.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @PostMapping(value = "/classrooms/{classroomId}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<MaterialDto> uploadMaterial(
            @PathVariable Long classroomId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        MaterialDto material = materialService.uploadMaterial(classroomId, title, description, file, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(material);
    }

    @GetMapping("/classrooms/{classroomId}/materials")
    public ResponseEntity<Page<MaterialDto>> getClassroomMaterials(
            @PathVariable Long classroomId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<MaterialDto> materials = materialService.getClassroomMaterials(classroomId, principal, pageable);
        return ResponseEntity.ok(materials);
    }

    @GetMapping("/materials/{id}")
    public ResponseEntity<MaterialDto> getMaterial(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        MaterialDto material = materialService.getMaterial(id, principal);
        return ResponseEntity.ok(material);
    }

    @GetMapping("/materials/{id}/download")
    public ResponseEntity<Resource> downloadMaterial(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        MaterialService.DownloadInfo downloadInfo = materialService.downloadMaterial(id, principal);

        String encodedFilename = URLEncoder.encode(downloadInfo.originalFilename(), StandardCharsets.UTF_8).replace("+", "%20");
        String contentDisposition = "attachment; filename=\"" + downloadInfo.originalFilename() + "\"; filename*=UTF-8''" + encodedFilename;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(downloadInfo.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(downloadInfo.resource());
    }

    @PutMapping("/materials/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<MaterialDto> updateMaterial(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMaterialRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        MaterialDto material = materialService.updateMaterial(id, request, principal);
        return ResponseEntity.ok(material);
    }

    @DeleteMapping("/materials/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<Void> deleteMaterial(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        materialService.deleteMaterial(id, principal);
        return ResponseEntity.noContent().build();
    }
}
