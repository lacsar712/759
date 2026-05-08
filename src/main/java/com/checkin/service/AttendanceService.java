package com.checkin.service;

import com.checkin.entity.AttendanceRecord;
import com.checkin.entity.AttendanceSession;
import com.checkin.entity.Course;
import com.checkin.entity.Student;
import com.checkin.exception.BusinessException;
import com.checkin.repository.AttendanceRecordRepository;
import com.checkin.repository.AttendanceSessionRepository;
import com.checkin.repository.CourseRepository;
import com.checkin.util.GeoUtil;
import com.checkin.util.IPUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 签到服务
 */
@Service
public class AttendanceService {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceService.class);

    @Autowired
    private AttendanceSessionRepository sessionRepository;

    @Autowired
    private AttendanceRecordRepository recordRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private IPUtil ipUtil;

    /**
     * 发起签到活动
     */
    @Transactional
    public AttendanceSession openSession(Long courseId, Integer durationMinutes, Long teacherId) {
        // 验证课程是否存在且属于该教师
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限操作此课程");
        }

        // 检查是否已有开放的签到活动
        sessionRepository.findByCourseIdAndStatus(courseId, "OPEN")
            .ifPresent(s -> {
                throw new BusinessException("该课程已有进行中的签到活动，请先关闭");
            });

        AttendanceSession session = new AttendanceSession();
        session.setCourseId(courseId);
        session.setStartTime(LocalDateTime.now());
        session.setEndTime(LocalDateTime.now().plusMinutes(durationMinutes));
        session.setStatus("OPEN");

        return sessionRepository.save(session);
    }

    /**
     * 关闭签到活动
     */
    @Transactional
    public void closeSession(Long sessionId, Long teacherId) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));

        // 验证权限
        Course course = courseRepository.findById(session.getCourseId())
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限操作此签到活动");
        }

        session.setStatus("CLOSED");
        sessionRepository.save(session);
    }

    /**
     * 学生签到
     */
    @Transactional
    public AttendanceRecord signIn(Long sessionId, Long studentId, String clientIp, String clientDeviceId) {
        // 1. 验证签到活动是否存在
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));

        // 2. 验证活动状态
        if (!"OPEN".equals(session.getStatus())) {
            throw new BusinessException("签到活动已关闭");
        }

        // 3. 验证活动是否过期
        if (session.getEndTime() != null && LocalDateTime.now().isAfter(session.getEndTime())) {
            throw new BusinessException("签到活动已过期");
        }

        // 4. 验证学生是否选了该课程
        List<Long> enrolledStudentIds = enrollmentService.getCourseStudentIds(session.getCourseId());
        // 使用 HashSet 提高查询性能
        java.util.Set<Long> enrolledStudentIdSet = new java.util.HashSet<>(enrolledStudentIds);
        if (!enrolledStudentIdSet.contains(studentId)) {
            throw new BusinessException("您未选修此课程，无法签到");
        }

        // 5. 验证是否重复签到
        if (recordRepository.existsBySessionIdAndStudentId(sessionId, studentId)) {
            throw new BusinessException(409, "您已签到，请勿重复签到");
        }

        // 6. 验证单设备限制
        if (recordRepository.existsBySessionIdAndClientIpAndClientDeviceId(sessionId, clientIp, clientDeviceId)) {
            throw new BusinessException(409, "该设备已被使用签到，请使用其他设备");
        }

        // 7. 验证局域网
        String serverIp = ipUtil.getServerIP();
        if (!ipUtil.isInSameSubnet(clientIp, serverIp)) {
            logger.warn("非局域网访问: clientIp={}, serverIp={}", clientIp, serverIp);
            throw new BusinessException(403, "非局域网访问，禁止签到");
        }

        // 8. 生成模拟地理位置
        BigDecimal[] location = GeoUtil.generateMockLocation();

        // 9. 创建签到记录
        AttendanceRecord record = new AttendanceRecord();
        record.setSessionId(sessionId);
        record.setCourseId(session.getCourseId());
        record.setStudentId(studentId);
        record.setSignTime(LocalDateTime.now());
        record.setClientIp(clientIp);
        record.setClientDeviceId(clientDeviceId);
        record.setGeoLat(location[0]);
        record.setGeoLng(location[1]);

        return recordRepository.save(record);
    }

    /**
     * 获取课程的活动签到活动
     */
    public AttendanceSession getActiveSession(Long courseId) {
        return sessionRepository.findByCourseIdAndStatus(courseId, "OPEN").orElse(null);
    }

    /**
     * 检查学生是否已签到
     */
    public boolean hasStudentSigned(Long sessionId, Long studentId) {
        return recordRepository.existsBySessionIdAndStudentId(sessionId, studentId);
    }

    /**
     * 获取实时签到数据
     */
    public Map<String, Object> getRealtimeData(Long sessionId, Long teacherId) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));

        // 验证权限
        Course course = courseRepository.findById(session.getCourseId())
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限查看此签到活动数据");
        }

        // 获取课程的所有学生
        List<Long> allStudentIds = enrollmentService.getCourseStudentIds(session.getCourseId());
        int totalCount = allStudentIds.size();

        // 获取已签到的学生ID
        List<Long> signedStudentIds = recordRepository.findStudentIdsBySessionId(sessionId);
        int signedCount = signedStudentIds.size();
        int unsignedCount = totalCount - signedCount;

        // 获取已签到的学生详情
        List<Student> signedStudents = studentService.getStudentsByIds(signedStudentIds);

        // 获取签到记录（包含时间）
        List<AttendanceRecord> records = recordRepository.findBySessionId(sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("courseId", session.getCourseId());
        result.put("status", session.getStatus());
        result.put("startTime", formatDateTime(session.getStartTime()));
        result.put("endTime", session.getEndTime() != null ? formatDateTime(session.getEndTime()) : null);
        result.put("totalCount", totalCount);
        result.put("signedCount", signedCount);
        result.put("unsignedCount", unsignedCount);
        result.put("signedStudents", signedStudents);
        result.put("records", records);

        return result;
    }

    /**
     * 获取历史签到记录
     */
    public Page<AttendanceSession> getHistorySessions(Long courseId, LocalDateTime dateFrom, LocalDateTime dateTo, Pageable pageable, Long teacherId) {
        // 如果指定了课程，验证权限
        if (courseId != null) {
            Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException("课程不存在"));

            if (!course.getTeacherId().equals(teacherId)) {
                throw new BusinessException(403, "无权限查看此课程的签到记录");
            }

            return sessionRepository.findHistorySessions(courseId, dateFrom, dateTo, pageable);
        } else {
            // 如果没有指定课程，只返回该教师的课程的签到记录
            List<Course> teacherCourses = courseRepository.findByTeacherId(teacherId);
            if (teacherCourses.isEmpty()) {
                // 教师没有课程，返回空页面
                return Page.empty(pageable);
            }

            // 获取该教师所有课程的ID列表
            List<Long> courseIds = teacherCourses.stream()
                .map(Course::getId)
                .collect(java.util.stream.Collectors.toList());

            return sessionRepository.findHistorySessionsByCourseIds(courseIds, dateFrom, dateTo, pageable);
        }
    }

    /**
     * 格式化时间
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
