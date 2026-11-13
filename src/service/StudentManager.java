package service;

import model.Student;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public class StudentManager {
    private final ArrayList<Student> students = new ArrayList<>();

    public void registerStudent(Student student) {
        if (student == null) {
            throw new IllegalArgumentException("Student details are required.");
        }
        if (findStudentById(student.getId()).isPresent()) {
            throw new IllegalArgumentException("A student with this ID is already registered.");
        }
        students.add(student);
    }

    public List<Student> getAllStudents() {
        return List.copyOf(students);
    }

    public void loadStudents(List<Student> loadedStudents) {
        if (loadedStudents == null) {
            throw new IllegalArgumentException("Student data is required.");
        }

        ArrayList<Student> validatedStudents = new ArrayList<>();
        for (Student student : loadedStudents) {
            if (student == null) {
                throw new IllegalArgumentException("Student data cannot contain null profiles.");
            }
            if (validatedStudents.stream()
                    .anyMatch(existing -> existing.getId().equalsIgnoreCase(student.getId()))) {
                throw new IllegalArgumentException("Student data contains duplicate IDs.");
            }
            validatedStudents.add(student);
        }

        students.clear();
        students.addAll(validatedStudents);
    }

    public Optional<Student> findStudentById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String searchId = id.trim();
        return students.stream()
                .filter(student -> student.getId().equalsIgnoreCase(searchId))
                .findFirst();
    }

    public void updateStudent(String currentId, Student updatedStudent) {
        if (updatedStudent == null) {
            throw new IllegalArgumentException("Updated student details are required.");
        }

        int studentIndex = findStudentIndex(currentId);
        if (studentIndex < 0) {
            throw new NoSuchElementException("The student profile no longer exists.");
        }

        Optional<Student> duplicate = findStudentById(updatedStudent.getId());
        if (duplicate.isPresent() && duplicate.get() != students.get(studentIndex)) {
            throw new IllegalArgumentException("A student with this ID is already registered.");
        }

        Student existingStudent = students.get(studentIndex);
        existingStudent.setId(updatedStudent.getId());
        existingStudent.setName(updatedStudent.getName());
        existingStudent.setEmail(updatedStudent.getEmail());
        existingStudent.setDepartment(updatedStudent.getDepartment());
        existingStudent.setYearOfStudy(updatedStudent.getYearOfStudy());
    }

    public void deleteStudent(String id) {
        int studentIndex = findStudentIndex(id);
        if (studentIndex < 0) {
            throw new NoSuchElementException("Student ID was not found.");
        }
        students.remove(studentIndex);
    }

    private int findStudentIndex(String id) {
        if (id == null || id.isBlank()) {
            return -1;
        }

        String searchId = id.trim();
        for (int i = 0; i < students.size(); i++) {
            if (students.get(i).getId().equalsIgnoreCase(searchId)) {
                return i;
            }
        }
        return -1;
    }
}
