package com.checkin.service;

import com.checkin.entity.Course;
import com.checkin.entity.Enrollment;
import com.checkin.exception.BusinessException;
import com.checkin.repository.CourseRepository;
import com.checkin.repository.EnrollmentRepository;
import com.checkin.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 选课服务
 */
@Service
public class EnrollmentService {

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StudentRepository studentRepository;

    /**
     * 学生加入课程
     */
    @Transactional
    public Enrollment enrollStudent(Long courseId, Long studentId, Long teacherId) {
        // 验证课程是否存在且属于该教师
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限操作此课程");
        }

        // 验证学生是否存在
        if (!studentRepository.existsById(studentId)) {
            throw new BusinessException("学生不存在");
        }

        // 检查是否已选课
        if (enrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)) {
            throw new BusinessException("学生已选此课程");
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(courseId);
        enrollment.setStudentId(studentId);
        return enrollmentRepository.save(enrollment);
    }

    /**
     * 移除学生
     */
    @Transactional
    public void removeStudent(Long courseId, Long studentId, Long teacherId) {
        // 验证课程是否存在且属于该教师
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限操作此课程");
        }

        if (!enrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)) {
            throw new BusinessException("学生未选此课程");
        }
        enrollmentRepository.deleteByCourseIdAndStudentId(courseId, studentId);
    }

    /**
     * 获取课程的所有学生ID（内部使用，不校验权限）
     */
    public List<Long> getCourseStudentIds(Long courseId) {
        return enrollmentRepository.findStudentIdsByCourseId(courseId);
    }

    /**
     * 获取课程的所有学生ID（带权限校验）
     */
    public List<Long> getCourseStudentIds(Long courseId, Long teacherId) {
        // 验证课程是否存在且属于该教师
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限查看此课程学生");
        }

        return enrollmentRepository.findStudentIdsByCourseId(courseId);
    }

    /**
     * 获取学生的所有课程
     */
    public List<Enrollment> getStudentCourses(Long studentId) {
        return enrollmentRepository.findByStudentId(studentId);
    }
}
