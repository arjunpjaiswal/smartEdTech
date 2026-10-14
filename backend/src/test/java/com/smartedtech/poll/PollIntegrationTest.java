package com.smartedtech.poll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedtech.TestDatabaseCleaner;
import com.smartedtech.classroom.Classroom;
import com.smartedtech.classroom.ClassroomEnrollment;
import com.smartedtech.classroom.ClassroomEnrollmentRepository;
import com.smartedtech.classroom.ClassroomRepository;
import com.smartedtech.poll.dto.CastVoteRequest;
import com.smartedtech.poll.dto.CreatePollRequest;
import com.smartedtech.security.JwtService;
import com.smartedtech.session.LiveSession;
import com.smartedtech.session.LiveSessionRepository;
import com.smartedtech.user.Role;
import com.smartedtech.user.User;
import com.smartedtech.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PollIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomEnrollmentRepository enrollmentRepository;

    @Autowired
    private LiveSessionRepository sessionRepository;

    @Autowired
    private PollRepository pollRepository;

    @Autowired
    private PollOptionRepository pollOptionRepository;

    @Autowired
    private PollVoteRepository pollVoteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    private User teacher;
    private User student1;
    private User student2;
    private User unenrolledStudent;
    private String teacherToken;
    private String student1Token;
    private String student2Token;
    private String unenrolledToken;
    private Classroom classroom;
    private LiveSession session;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();

        teacher = userRepository.save(new User(
                "teacher.poll@school.edu",
                passwordEncoder.encode("Password123!"),
                "Teacher Poll",
                Role.TEACHER
        ));
        teacherToken = jwtService.generateAccessToken(teacher);

        student1 = userRepository.save(new User(
                "student1.poll@school.edu",
                passwordEncoder.encode("Password123!"),
                "Student One",
                Role.STUDENT
        ));
        student1Token = jwtService.generateAccessToken(student1);

        student2 = userRepository.save(new User(
                "student2.poll@school.edu",
                passwordEncoder.encode("Password123!"),
                "Student Two",
                Role.STUDENT
        ));
        student2Token = jwtService.generateAccessToken(student2);

        unenrolledStudent = userRepository.save(new User(
                "unenrolled.poll@school.edu",
                passwordEncoder.encode("Password123!"),
                "Unenrolled Student",
                Role.STUDENT
        ));
        unenrolledToken = jwtService.generateAccessToken(unenrolledStudent);

        classroom = classroomRepository.save(new Classroom(
                "Physics 101",
                "Mechanics and Dynamics",
                "POLL1234",
                teacher
        ));

        enrollmentRepository.save(new ClassroomEnrollment(classroom, student1));
        enrollmentRepository.save(new ClassroomEnrollment(classroom, student2));

        Instant now = Instant.now();
        session = sessionRepository.save(new LiveSession(
                classroom,
                "Poll Mechanics Lecture",
                "Live lecture with polls",
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(55, ChronoUnit.MINUTES),
                "room-poll-101"
        ));
        session.setStatus("ACTIVE");
        sessionRepository.save(session);
    }

    @Test
    @DisplayName("Teacher can create poll with options, student cannot create poll (403)")
    void teacherCanCreatePoll_studentCannot() throws Exception {
        CreatePollRequest request = new CreatePollRequest(
                "What is the SI unit of force?",
                List.of("Joule", "Newton", "Watt", "Pascal"),
                false
        );

        // Student attempt -> 403
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/polls")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // Teacher attempt -> 201 Created
        MvcResult result = mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/polls")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.question").value("What is the SI unit of force?"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.options.length()").value(4))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        long pollId = root.get("id").asLong();
        assertThat(pollRepository.findById(pollId)).isPresent();
    }

    @Test
    @DisplayName("Poll lifecycle: create (DRAFT) -> open (OPEN) -> student vote -> close (CLOSED)")
    void pollLifecycleAndVoting() throws Exception {
        CreatePollRequest createReq = new CreatePollRequest(
                "Is momentum conserved in an elastic collision?",
                List.of("Yes", "No", "Depends on frame of reference"),
                false
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/polls")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode pollJson = objectMapper.readTree(createRes.getResponse().getContentAsString());
        long pollId = pollJson.get("id").asLong();
        long optYesId = pollJson.get("options").get(0).get("id").asLong();
        long optNoId = pollJson.get("options").get(1).get("id").asLong();

        // 1. Voting on DRAFT poll returns 400 Bad Request
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/polls/" + pollId + "/vote")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CastVoteRequest(optYesId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        // 2. Teacher opens poll
        mockMvc.perform(patch("/api/v1/sessions/" + session.getId() + "/polls/" + pollId + "/open")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        // 3. Student 1 votes "Yes"
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/polls/" + pollId + "/vote")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CastVoteRequest(optYesId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userVotedOptionId").value(optYesId));

        // 4. Student 2 votes "No"
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/polls/" + pollId + "/vote")
                        .header("Authorization", "Bearer " + student2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CastVoteRequest(optNoId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userVotedOptionId").value(optNoId));

        // 5. Duplicate vote by student 1 returns 409 Conflict
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/polls/" + pollId + "/vote")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CastVoteRequest(optYesId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // 6. Teacher closes poll
        mockMvc.perform(patch("/api/v1/sessions/" + session.getId() + "/polls/" + pollId + "/close")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.totalVotes").value(2));

        // 7. Voting on CLOSED poll returns 400 Bad Request
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/polls/" + pollId + "/vote")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CastVoteRequest(optYesId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Results visibility rules enforced: hidden during OPEN poll for students, revealed when closed or visibility enabled")
    void resultsVisibilityRules() throws Exception {
        CreatePollRequest createReq = new CreatePollRequest(
                "Speed of light in vacuum?",
                List.of("3e8 m/s", "3e6 m/s"),
                false // results not visible to students initially
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/polls")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long pollId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();
        long optId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("options").get(0).get("id").asLong();

        // Open poll
        mockMvc.perform(patch("/api/v1/sessions/" + session.getId() + "/polls/" + pollId + "/open")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        // Student 1 votes
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/polls/" + pollId + "/vote")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CastVoteRequest(optId))))
                .andExpect(status().isOk());

        // Student 2 queries polls: totalVotes and option voteCount MUST be null (hidden)
        MvcResult studentQuery = mockMvc.perform(get("/api/v1/sessions/" + session.getId() + "/polls")
                        .header("Authorization", "Bearer " + student2Token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode studentPolls = objectMapper.readTree(studentQuery.getResponse().getContentAsString());
        JsonNode studentPoll = studentPolls.get(0);
        assertThat(studentPoll.get("totalVotes").isNull()).isTrue();
        assertThat(studentPoll.get("options").get(0).get("voteCount").isNull()).isTrue();

        // Teacher queries polls: totalVotes and option voteCount MUST be visible
        MvcResult teacherQuery = mockMvc.perform(get("/api/v1/sessions/" + session.getId() + "/polls")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode teacherPolls = objectMapper.readTree(teacherQuery.getResponse().getContentAsString());
        JsonNode teacherPoll = teacherPolls.get(0);
        assertThat(teacherPoll.get("totalVotes").asLong()).isEqualTo(1);
        assertThat(teacherPoll.get("options").get(0).get("voteCount").asLong()).isEqualTo(1);

        // Teacher sets resultsVisibleToStudents = true
        mockMvc.perform(patch("/api/v1/sessions/" + session.getId() + "/polls/" + pollId + "/visibility")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("visible", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultsVisibleToStudents").value(true));

        // Now student 2 queries polls: results ARE revealed!
        MvcResult studentQueryAfterReveal = mockMvc.perform(get("/api/v1/sessions/" + session.getId() + "/polls")
                        .header("Authorization", "Bearer " + student2Token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode studentPollAfter = objectMapper.readTree(studentQueryAfterReveal.getResponse().getContentAsString()).get(0);
        assertThat(studentPollAfter.get("totalVotes").asLong()).isEqualTo(1);
        assertThat(studentPollAfter.get("options").get(0).get("voteCount").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("Unenrolled student cannot access polls (403 Forbidden)")
    void unenrolledStudentCannotAccessPolls() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/" + session.getId() + "/polls")
                        .header("Authorization", "Bearer " + unenrolledToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
