package com.smartedtech.material;

import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.common.BadRequestException;
import com.smartedtech.common.ForbiddenException;
import com.smartedtech.common.ResourceNotFoundException;
import com.smartedtech.material.dto.MaterialDto;
import com.smartedtech.material.dto.UpdateMaterialRequest;
import com.smartedtech.notification.NotificationService;
import com.smartedtech.security.UserPrincipal;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import com.smartedtech.user.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
public class MaterialService {

    private final StudyMaterialRepository materialRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomEnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final FileStorage fileStorage;
    private final FileTypeValidator fileTypeValidator;
    private final NotificationService notificationService;

    public MaterialService(
            StudyMaterialRepository materialRepository,
            ClassroomRepository classroomRepository,
            ClassroomEnrollmentRepository enrollmentRepository,
            UserRepository userRepository,
            FileStorage fileStorage,
            FileTypeValidator fileTypeValidator,
            NotificationService notificationService) {
        this.materialRepository = materialRepository;
        this.classroomRepository = classroomRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.fileStorage = fileStorage;
        this.fileTypeValidator = fileTypeValidator;
        this.notificationService = notificationService;
    }

    @Transactional
    public MaterialDto uploadMaterial(Long classroomId, String title, String description,
                                      MultipartFile file, UserPrincipal principal) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }

        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));

        checkTeacherOrAdminAccess(classroom, principal);

        User uploader = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Could not read uploaded file");
        }

        // Validate extension and magic content bytes
        fileTypeValidator.validate(file.getOriginalFilename(), file.getSize(), new ByteArrayInputStream(fileBytes));

        // Store file safely
        String storedFilename = fileStorage.store(
                new ByteArrayInputStream(fileBytes),
                file.getOriginalFilename(),
                file.getContentType()
        );

        String cleanTitle = (title != null && !title.isBlank()) ? title.trim() : file.getOriginalFilename();
        StudyMaterial material = new StudyMaterial(
                classroom,
                uploader,
                cleanTitle,
                description != null ? description.trim() : null,
                file.getOriginalFilename(),
                storedFilename,
                file.getSize(),
                file.getContentType() != null ? file.getContentType() : "application/octet-stream"
        );

        material = materialRepository.save(material);

        notificationService.notifyClassroomStudents(
                classroom,
                "MATERIAL_UPLOADED",
                "New Study Material: " + material.getTitle(),
                uploader.getFullName() + " uploaded a new study material in " + classroom.getName()
        );

        return MaterialDto.from(material);
    }

    @Transactional(readOnly = true)
    public Page<MaterialDto> getClassroomMaterials(Long classroomId, UserPrincipal principal, Pageable pageable) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));

        checkMemberOrAdminAccess(classroom, principal);

        return materialRepository.findByClassroomIdOrderByCreatedAtDesc(classroomId, pageable)
                .map(MaterialDto::from);
    }

    @Transactional(readOnly = true)
    public MaterialDto getMaterial(Long materialId, UserPrincipal principal) {
        StudyMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found"));

        checkMemberOrAdminAccess(material.getClassroom(), principal);

        return MaterialDto.from(material);
    }

    public record DownloadInfo(Resource resource, String originalFilename, String contentType) {}

    @Transactional(readOnly = true)
    public DownloadInfo downloadMaterial(Long materialId, UserPrincipal principal) {
        StudyMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found"));

        checkMemberOrAdminAccess(material.getClassroom(), principal);

        Resource resource = fileStorage.loadAsResource(material.getStoredFilename());
        return new DownloadInfo(resource, material.getOriginalFilename(), material.getContentType());
    }

    @Transactional
    public MaterialDto updateMaterial(Long materialId, UpdateMaterialRequest request, UserPrincipal principal) {
        StudyMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found"));

        checkTeacherOrAdminAccess(material.getClassroom(), principal);

        material.setTitle(request.title().trim());
        material.setDescription(request.description() != null ? request.description().trim() : null);

        material = materialRepository.save(material);
        return MaterialDto.from(material);
    }

    @Transactional
    public void deleteMaterial(Long materialId, UserPrincipal principal) {
        StudyMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found"));

        checkTeacherOrAdminAccess(material.getClassroom(), principal);

        String storedFilename = material.getStoredFilename();
        materialRepository.delete(material);
        materialRepository.flush();
        fileStorage.delete(storedFilename);
    }

    private void checkTeacherOrAdminAccess(Classroom classroom, UserPrincipal principal) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (principal.getRole() != Role.TEACHER || !classroom.getTeacher().getId().equals(principal.getId())) {
            throw new ForbiddenException("You do not have permission to manage materials for this classroom");
        }
    }

    private void checkMemberOrAdminAccess(Classroom classroom, UserPrincipal principal) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (principal.getRole() == Role.TEACHER && classroom.getTeacher().getId().equals(principal.getId())) {
            return;
        }
        if (principal.getRole() == Role.STUDENT &&
                enrollmentRepository.existsByClassroomIdAndStudentId(classroom.getId(), principal.getId())) {
            return;
        }
        throw new ForbiddenException("You are not enrolled in or managing this classroom");
    }
}
