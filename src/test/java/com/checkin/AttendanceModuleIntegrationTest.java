package com.checkin;

import com.checkin.entity.*;
import com.checkin.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AttendanceModuleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private AttendanceSessionRepository sessionRepository;

    @Autowired
    private AttendanceRecordRepository recordRepository;

    private static Long teacherId1;
    private static Long teacherId2;
    private static Long studentId1;
    private static Long studentId2;
    private static Long courseId1;
    private static Long courseId2;
    private static MockHttpSession teacherSession1;
    private static MockHttpSession teacherSession2;
    private static MockHttpSession studentSession1;
    private static MockHttpSession studentSession2;
    private static String deviceId1;
    private static String deviceId2;
    private static final String SAME_SUBNET_IP = "192.168.1.50";
    private static final String DIFFERENT_SUBNET_IP = "10.0.0.50";

    @BeforeEach
    void setUp() {
        recordRepository.deleteAll();
        sessionRepository.deleteAll();
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
        studentRepository.deleteAll();
        teacherRepository.deleteAll();

        Teacher teacher1 = new Teacher();
        teacher1.setTeacherNo("T001");
        teacher1.setName("张老师");
        teacher1.setPasswordHash("$2a$10$dummy");
        teacherId1 = teacherRepository.save(teacher1).getId();

        Teacher teacher2 = new Teacher();
        teacher2.setTeacherNo("T002");
        teacher2.setName("李老师");
        teacher2.setPasswordHash("$2a$10$dummy");
        teacherId2 = teacherRepository.save(teacher2).getId();

        Student student1 = new Student();
        student1.setStudentNo("S001");
        student1.setName("张三");
        student1.setPasswordHash("$2a$10$dummy");
        student1.setClassName("计科1班");
        studentId1 = studentRepository.save(student1).getId();

        Student student2 = new Student();
        student2.setStudentNo("S002");
        student2.setName("李四");
        student2.setPasswordHash("$2a$10$dummy");
        student2.setClassName("计科1班");
        studentId2 = studentRepository.save(student2).getId();

        Course course1 = new Course();
        course1.setCourseName("软件工程");
        course1.setTeacherId(teacherId1);
        courseId1 = courseRepository.save(course1).getId();

        Course course2 = new Course();
        course2.setCourseName("数据结构");
        course2.setTeacherId(teacherId2);
        courseId2 = courseRepository.save(course2).getId();

        Enrollment enrollment1 = new Enrollment();
        enrollment1.setCourseId(courseId1);
        enrollment1.setStudentId(studentId1);
        enrollmentRepository.save(enrollment1);

        Enrollment enrollment2 = new Enrollment();
        enrollment2.setCourseId(courseId1);
        enrollment2.setStudentId(studentId2);
        enrollmentRepository.save(enrollment2);

        teacherSession1 = new MockHttpSession();
        teacherSession1.setAttribute("userId", teacherId1);
        teacherSession1.setAttribute("userType", "TEACHER");

        teacherSession2 = new MockHttpSession();
        teacherSession2.setAttribute("userId", teacherId2);
        teacherSession2.setAttribute("userType", "TEACHER");

        studentSession1 = new MockHttpSession();
        studentSession1.setAttribute("userId", studentId1);
        studentSession1.setAttribute("userType", "STUDENT");

        studentSession2 = new MockHttpSession();
        studentSession2.setAttribute("userId", studentId2);
        studentSession2.setAttribute("userType", "STUDENT");

        deviceId1 = UUID.randomUUID().toString();
        deviceId2 = UUID.randomUUID().toString();
    }

    private Long openSession(MockHttpSession teacherSession, Long courseId) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("durationMinutes", 10);

        MvcResult result = mockMvc.perform(post("/api/teacher/course/" + courseId + "/sessions/open")
                .session(teacherSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        var jsonNode = objectMapper.readTree(responseBody);
        return jsonNode.get("data").get("id").asLong();
    }

    private MvcResult signIn(MockHttpSession studentSession, Long sessionId, String clientIp, String deviceId) throws Exception {
        return mockMvc.perform(post("/api/student/session/" + sessionId + "/sign")
                .session(studentSession)
                .header("X-Forwarded-For", clientIp)
                .cookie(new Cookie("CHECKIN_DEVICE_ID", deviceId)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void closeSession(MockHttpSession teacherSession, Long sessionId) throws Exception {
        mockMvc.perform(post("/api/teacher/sessions/" + sessionId + "/close")
                .session(teacherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(1)
    @DisplayName("场景1：局域网验证通过签到成功")
    void should_sign_in_successfully_when_lan_verification_passed() throws Exception {
        Long sessionId = openSession(teacherSession1, courseId1);

        MvcResult result = signIn(studentSession1, sessionId, SAME_SUBNET_IP, deviceId1);

        String responseBody = result.getResponse().getContentAsString();
        var jsonNode = objectMapper.readTree(responseBody);

        assertEquals(0, jsonNode.get("code").asInt());
        assertEquals("签到成功", jsonNode.get("message").asText());
        assertNotNull(jsonNode.get("data").get("record"));
        assertEquals(studentId1, jsonNode.get("data").get("record").get("studentId").asLong());

        assertTrue(recordRepository.existsBySessionIdAndStudentId(sessionId, studentId1));
    }

    @Test
    @Order(2)
    @DisplayName("场景2：非同网段 IP 被拒绝")
    void should_reject_sign_in_when_ip_not_in_same_subnet() throws Exception {
        Long sessionId = openSession(teacherSession1, courseId1);

        MvcResult result = signIn(studentSession1, sessionId, DIFFERENT_SUBNET_IP, deviceId1);

        String responseBody = result.getResponse().getContentAsString();
        var jsonNode = objectMapper.readTree(responseBody);

        assertEquals(403, jsonNode.get("code").asInt());
        assertEquals("非局域网访问，禁止签到", jsonNode.get("message").asText());

        assertFalse(recordRepository.existsBySessionIdAndStudentId(sessionId, studentId1));
    }

    @Test
    @Order(3)
    @DisplayName("场景3：同一设备对同一 session 重复签到被拦截")
    void should_reject_sign_in_when_same_device_used_for_same_session() throws Exception {
        Long sessionId = openSession(teacherSession1, courseId1);

        MvcResult result1 = signIn(studentSession1, sessionId, SAME_SUBNET_IP, deviceId1);
        String responseBody1 = result1.getResponse().getContentAsString();
        var jsonNode1 = objectMapper.readTree(responseBody1);
        assertEquals(0, jsonNode1.get("code").asInt());

        MvcResult result2 = signIn(studentSession2, sessionId, SAME_SUBNET_IP, deviceId1);
        String responseBody2 = result2.getResponse().getContentAsString();
        var jsonNode2 = objectMapper.readTree(responseBody2);

        assertEquals(409, jsonNode2.get("code").asInt());
        assertEquals("该设备已被使用签到，请使用其他设备", jsonNode2.get("message").asText());

        assertTrue(recordRepository.existsBySessionIdAndStudentId(sessionId, studentId1));
        assertFalse(recordRepository.existsBySessionIdAndStudentId(sessionId, studentId2));
    }

    @Test
    @Order(4)
    @DisplayName("场景4：同一学生重复签到被拦截")
    void should_reject_sign_in_when_same_student_signs_again() throws Exception {
        Long sessionId = openSession(teacherSession1, courseId1);

        MvcResult result1 = signIn(studentSession1, sessionId, SAME_SUBNET_IP, deviceId1);
        String responseBody1 = result1.getResponse().getContentAsString();
        var jsonNode1 = objectMapper.readTree(responseBody1);
        assertEquals(0, jsonNode1.get("code").asInt());

        MvcResult result2 = signIn(studentSession1, sessionId, SAME_SUBNET_IP, deviceId2);
        String responseBody2 = result2.getResponse().getContentAsString();
        var jsonNode2 = objectMapper.readTree(responseBody2);

        assertEquals(409, jsonNode2.get("code").asInt());
        assertEquals("您已签到，请勿重复签到", jsonNode2.get("message").asText());

        assertEquals(1, recordRepository.countBySessionId(sessionId));
    }

    @Test
    @Order(5)
    @DisplayName("场景5：签到活动未开启或已关闭时签到失败")
    void should_reject_sign_in_when_session_not_open_or_already_closed() throws Exception {
        Long sessionId = openSession(teacherSession1, courseId1);

        closeSession(teacherSession1, sessionId);

        MvcResult result = signIn(studentSession1, sessionId, SAME_SUBNET_IP, deviceId1);
        String responseBody = result.getResponse().getContentAsString();
        var jsonNode = objectMapper.readTree(responseBody);

        assertEquals(1, jsonNode.get("code").asInt());
        assertEquals("签到活动已关闭", jsonNode.get("message").asText());

        assertFalse(recordRepository.existsBySessionIdAndStudentId(sessionId, studentId1));
    }

    @Test
    @Order(6)
    @DisplayName("场景6：教师关闭非自己课程的 session 被拒绝")
    void should_reject_close_session_when_teacher_not_owning_course() throws Exception {
        Long sessionId = openSession(teacherSession1, courseId1);

        MvcResult result = mockMvc.perform(post("/api/teacher/sessions/" + sessionId + "/close")
                .session(teacherSession2))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        var jsonNode = objectMapper.readTree(responseBody);

        assertEquals(403, jsonNode.get("code").asInt());
        assertEquals("无权限操作此签到活动", jsonNode.get("message").asText());

        var session = sessionRepository.findById(sessionId).orElseThrow();
        assertEquals("OPEN", session.getStatus());
    }

    @Test
    @Order(7)
    @DisplayName("教师开启签到活动接口测试")
    void should_open_session_successfully_when_valid_request() throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("durationMinutes", 15);

        MvcResult result = mockMvc.perform(post("/api/teacher/course/" + courseId1 + "/sessions/open")
                .session(teacherSession1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        var jsonNode = objectMapper.readTree(responseBody);
        Long sessionId = jsonNode.get("data").get("id").asLong();

        assertTrue(sessionRepository.findById(sessionId).isPresent());
        assertEquals("OPEN", sessionRepository.findById(sessionId).get().getStatus());
    }

    @Test
    @Order(8)
    @DisplayName("教师实时统计接口测试")
    void should_get_realtime_data_successfully_when_valid_session() throws Exception {
        Long sessionId = openSession(teacherSession1, courseId1);
        signIn(studentSession1, sessionId, SAME_SUBNET_IP, deviceId1);

        MvcResult result = mockMvc.perform(get("/api/teacher/sessions/" + sessionId + "/realtime")
                .session(teacherSession1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.signedCount").value(1))
                .andExpect(jsonPath("$.data.unsignedCount").value(1))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        var jsonNode = objectMapper.readTree(responseBody);

        assertEquals(sessionId, jsonNode.get("data").get("sessionId").asLong());
        assertEquals("OPEN", jsonNode.get("data").get("status").asText());
    }

    @Test
    @Order(9)
    @DisplayName("教师历史查询接口测试")
    void should_get_history_sessions_successfully_when_valid_request() throws Exception {
        Long sessionId = openSession(teacherSession1, courseId1);
        closeSession(teacherSession1, sessionId);

        mockMvc.perform(get("/api/teacher/sessions/history")
                .session(teacherSession1)
                .param("courseId", String.valueOf(courseId1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @Order(10)
    @DisplayName("获取活动签到活动接口测试")
    void should_get_active_session_successfully_when_exists() throws Exception {
        Long sessionId = openSession(teacherSession1, courseId1);

        mockMvc.perform(get("/api/student/course/" + courseId1 + "/active-session")
                .session(studentSession1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(sessionId))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    @Order(11)
    @DisplayName("检查签到状态接口测试")
    void should_check_sign_status_successfully_when_session_exists() throws Exception {
        Long sessionId = openSession(teacherSession1, courseId1);

        mockMvc.perform(get("/api/student/session/" + sessionId + "/check")
                .session(studentSession1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hasSigned").value(false));

        signIn(studentSession1, sessionId, SAME_SUBNET_IP, deviceId1);

        mockMvc.perform(get("/api/student/session/" + sessionId + "/check")
                .session(studentSession1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hasSigned").value(true));
    }
}
