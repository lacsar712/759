package com.checkin.service;

import com.checkin.entity.Student;
import com.checkin.exception.BusinessException;
import com.checkin.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 学生服务
 */
@Service
public class StudentService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 创建学生
     */
    @Transactional
    public Student createStudent(String studentNo, String name, String password, String className) {
        if (studentRepository.existsByStudentNo(studentNo)) {
            throw new BusinessException("学号已存在");
        }

        Student student = new Student();
        student.setStudentNo(studentNo);
        student.setName(name);
        student.setPasswordHash(passwordEncoder.encode(password));
        student.setClassName(className);
        return studentRepository.save(student);
    }

    /**
     * 更新学生
     */
    @Transactional
    public Student updateStudent(Long studentId, String studentNo, String name, String password, String className) {
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new BusinessException("学生不存在"));

        // 检查学号是否被其他学生占用
        if (!student.getStudentNo().equals(studentNo) && studentRepository.existsByStudentNo(studentNo)) {
            throw new BusinessException("学号已存在");
        }

        student.setStudentNo(studentNo);
        student.setName(name);
        if (password != null && !password.isEmpty()) {
            student.setPasswordHash(passwordEncoder.encode(password));
        }
        student.setClassName(className);
        return studentRepository.save(student);
    }

    /**
     * 删除学生
     */
    @Transactional
    public void deleteStudent(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new BusinessException("学生不存在");
        }
        studentRepository.deleteById(studentId);
    }

    /**
     * 分页查询学生
     */
    public Page<Student> searchStudents(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isEmpty()) {
            return studentRepository.findAll(pageable);
        }
        return studentRepository.searchByKeyword(keyword, pageable);
    }

    /**
     * 根据ID列表获取学生
     */
    public List<Student> getStudentsByIds(List<Long> ids) {
        return studentRepository.findByIdIn(ids);
    }

    /**
     * 获取学生详情
     */
    public Student getStudent(Long studentId) {
        return studentRepository.findById(studentId)
            .orElseThrow(() -> new BusinessException("学生不存在"));
    }
}
