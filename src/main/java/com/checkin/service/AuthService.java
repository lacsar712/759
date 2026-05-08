package com.checkin.service;

import com.checkin.entity.Student;
import com.checkin.entity.Teacher;
import com.checkin.exception.BusinessException;
import com.checkin.repository.StudentRepository;
import com.checkin.repository.TeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * 认证服务
 */
@Service
public class AuthService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 学生登录
     */
    public Student studentLogin(String studentNo, String password, HttpSession session) {
        Student student = studentRepository.findByStudentNo(studentNo)
            .orElseThrow(() -> new BusinessException("学号或密码错误"));

        if (!passwordEncoder.matches(password, student.getPasswordHash())) {
            throw new BusinessException("学号或密码错误");
        }

        // 设置 Session
        session.setAttribute("userType", "STUDENT");
        session.setAttribute("userId", student.getId());
        session.setAttribute("userName", student.getName());

        return student;
    }

    /**
     * 教师登录
     */
    public Teacher teacherLogin(String teacherNo, String password, HttpSession session) {
        Teacher teacher = teacherRepository.findByTeacherNo(teacherNo)
            .orElseThrow(() -> new BusinessException("工号或密码错误"));

        if (!passwordEncoder.matches(password, teacher.getPasswordHash())) {
            throw new BusinessException("工号或密码错误");
        }

        // 设置 Session
        session.setAttribute("userType", "TEACHER");
        session.setAttribute("userId", teacher.getId());
        session.setAttribute("userName", teacher.getName());

        return teacher;
    }

    /**
     * 登出
     */
    public void logout(HttpSession session) {
        session.invalidate();
    }
}
