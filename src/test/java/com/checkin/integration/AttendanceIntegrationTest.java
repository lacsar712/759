package com.checkin.integration;

import com.checkin.entity.AttendanceSession;
import com.checkin.entity.Course;
import com.checkin.entity.Enrollment;
import com.checkin.entity.Student;
import com.checkin.entity.Teacher;
import com.checkin.repository.AttendanceRecordRepository;
import com.checkin.repository.AttendanceSessionRepository;
import com.checkin.repository.CourseRepository;
import com.checkin.repository.EnrollmentRepository;
import com.checkin.repository.StudentRepository;
import com.checkin.repository.TeacherRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AttendanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    private static final String SERVER_IP = "192.168.1.100";
    private static final String SAME_SUBNET_IP = "192.168.1.50";
    private static final String DIFFERENT_SUBNET_IP = "10.0.0.1";

    private Long ownerTeacherId;
    private Long otherTeacherId;
    private Long courseId;
    private Long studentId;
    private Long otherStudentId;

    @BeforeEach
    void setUp() {
        Teacher ownerTeacher = new Teacher();
        ownerTeacher.setTeacherNo("T001");
        ownerTeacher.setName("张老师");
        ownerTeacher.setPasswordHash("hashed");
        ownerTeacher = teacherRepository.save(ownerTeacher);
        ownerTeacherId = ownerTeacher.getId();

        Teacher otherTeacher = new Teacher();
        otherTeacher.setTeacherNo("T002");
        otherTeacher.setName("李老师");
        otherTeacher.setPasswordHash("hashed");
        otherTeacher = teacherRepository.save(otherTeacher);
        otherTeacherId = otherTeacher.getId();

        Course course = new Course();
        course.setCourseName("高等数学");
        course.setTeacherId(ownerTeacherId);
        course = courseRepository.save(course);
        courseId = course.getId();

        Student student = new Student();
        student.setStudentNo("S001");
        student.setName("学生甲");
        student.setPasswordHash("hashed");
        student = studentRepository.save(student);
        studentId = student.getId();

        Student otherStudent = new Student();
        otherStudent.setStudentNo("S002");
        otherStudent.setName("学生乙");
        otherStudent.setPasswordHash("hashed");
        otherStudent = studentRepository.save(otherStudent);
        otherStudentId = otherStudent.getId();

        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(courseId);
        enrollment.setStudentId(studentId);
        enrollmentRepository.save(enrollment);

        Enrollment enrollment2 = new Enrollment();
        enrollment2.setCourseId(courseId);
        enrollment2.setStudentId(otherStudentId);
        enrollmentRepository.save(enrollment2);
    }

    private MockHttpSession createStudentSession(Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userType", "STUDENT");
        session.setAttribute("userId", userId);
        return session;
    }

    private MockHttpSession createTeacherSession(Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userType", "TEACHER");
        session.setAttribute("userId", userId);
        return session;
    }

    private RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private long openSession(MockHttpSession teacherSession) throws Exception {
        String response = mockMvc.perform(
                post("/api/teacher/course/{courseId}/sessions/open", courseId)
                    .session(teacherSession)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.id").isNumber())
            .andReturn().getResponse().getContentAsString();

        return parseSessionId(response);
    }

    private long parseSessionId(String jsonBody) {
        int idx = jsonBody.indexOf("\"id\":") + 5;
        StringBuilder sb = new StringBuilder();
        char c;
        while (idx < jsonBody.length() && Character.isDigit(c = jsonBody.charAt(idx))) {
            sb.append(c);
            idx++;
        }
        return Long.parseLong(sb.toString());
    }

    @Test
    @DisplayName("场景1：局域网验证通过签到成功")
    void should_signInSuccessfully_when_clientIpIsInTheSameSubnet() throws Exception {
        MockHttpSession teacherSession = createTeacherSession(ownerTeacherId);
        long sid = openSession(teacherSession);

        MockHttpSession studentSession = createStudentSession(studentId);

        mockMvc.perform(
                post("/api/student/session/{sessionId}/sign", sid)
                    .session(studentSession)
                    .with(remoteAddr(SAME_SUBNET_IP))
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.message").value("签到成功"))
            .andExpect(jsonPath("$.data.record.sessionId").value(sid))
            .andExpect(jsonPath("$.data.record.studentId").value(studentId))
            .andExpect(jsonPath("$.data.record.clientIp").value(SAME_SUBNET_IP));

        Assertions.assertTrue(
            attendanceRecordRepository.existsBySessionIdAndStudentId(sid, studentId),
            "应存在签到记录"
        );
    }

    @Test
    @DisplayName("场景2：非同网段 IP 被拒绝")
    void should_rejectSignIn_when_clientIpIsFromDifferentSubnet() throws Exception {
        MockHttpSession teacherSession = createTeacherSession(ownerTeacherId);
        long sid = openSession(teacherSession);

        MockHttpSession studentSession = createStudentSession(studentId);

        mockMvc.perform(
                post("/api/student/session/{sessionId}/sign", sid)
                    .session(studentSession)
                    .with(remoteAddr(DIFFERENT_SUBNET_IP))
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("非局域网访问，禁止签到"));

        Assertions.assertFalse(
            attendanceRecordRepository.existsBySessionIdAndStudentId(sid, studentId),
            "不应存在签到记录"
        );
    }

    @Test
    @DisplayName("场景3：同一设备对同一 session 重复签到被拦截")
    void should_blockDuplicateSignIn_when_sameDeviceSameSession() throws Exception {
        MockHttpSession teacherSession = createTeacherSession(ownerTeacherId);
        long sid = openSession(teacherSession);

        MockHttpSession studentSession = createStudentSession(studentId);

        MvcResult firstResult = mockMvc.perform(
                post("/api/student/session/{sessionId}/sign", sid)
                    .session(studentSession)
                    .with(remoteAddr(SAME_SUBNET_IP))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();

        String deviceCookie = firstResult.getResponse().getHeader("Set-Cookie");
        String deviceId = null;
        if (deviceCookie != null) {
            for (String part : deviceCookie.split(";")) {
                part = part.trim();
                if (part.startsWith("CHECKIN_DEVICE_ID=")) {
                    deviceId = part.substring("CHECKIN_DEVICE_ID=".length());
                    break;
                }
            }
        }

        MockHttpSession anotherStudentSession = createStudentSession(otherStudentId);

        mockMvc.perform(
                post("/api/student/session/{sessionId}/sign", sid)
                    .session(anotherStudentSession)
                    .with(remoteAddr(SAME_SUBNET_IP))
                    .cookie(new javax.servlet.http.Cookie("CHECKIN_DEVICE_ID", deviceId))
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.message").value("该设备已被使用签到，请使用其他设备"));
    }

    @Test
    @DisplayName("场景4：同一学生重复签到被拦截")
    void should_blockDuplicateSignIn_when_sameStudentSignsAgain() throws Exception {
        MockHttpSession teacherSession = createTeacherSession(ownerTeacherId);
        long sid = openSession(teacherSession);

        MockHttpSession studentSession = createStudentSession(studentId);

        mockMvc.perform(
                post("/api/student/session/{sessionId}/sign", sid)
                    .session(studentSession)
                    .with(remoteAddr(SAME_SUBNET_IP))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        MockHttpSession freshStudentSession = createStudentSession(studentId);

        mockMvc.perform(
                post("/api/student/session/{sessionId}/sign", sid)
                    .session(freshStudentSession)
                    .with(remoteAddr("192.168.1.51"))
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.message").value("您已签到，请勿重复签到"));
    }

    @Test
    @DisplayName("场景5：签到活动未开启或已关闭时签到失败")
    void should_failSignIn_when_sessionNotOpenOrAlreadyClosed() throws Exception {
        MockHttpSession teacherSession = createTeacherSession(ownerTeacherId);
        long sid = openSession(teacherSession);

        AttendanceSession session = attendanceSessionRepository.findById(sid).orElseThrow();
        session.setStatus("CLOSED");
        attendanceSessionRepository.save(session);

        MockHttpSession studentSession = createStudentSession(studentId);

        mockMvc.perform(
                post("/api/student/session/{sessionId}/sign", sid)
                    .session(studentSession)
                    .with(remoteAddr(SAME_SUBNET_IP))
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(1))
            .andExpect(jsonPath("$.message").value("签到活动已关闭"));
    }

    @Test
    @DisplayName("场景6：教师关闭非自己课程的 session 被拒绝")
    void should_rejectClose_when_teacherClosesAnotherTeachersSession() throws Exception {
        MockHttpSession ownerSession = createTeacherSession(ownerTeacherId);
        long sid = openSession(ownerSession);

        MockHttpSession otherTeacherSession = createTeacherSession(otherTeacherId);

        mockMvc.perform(
                post("/api/teacher/sessions/{sessionId}/close", sid)
                    .session(otherTeacherSession)
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403));
    }
}
