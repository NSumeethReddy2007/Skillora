package model;

import java.util.ArrayList;
import java.util.List;

public class Student {
    private String id;
    private String name;
    private String email;
    private String department;
    private int yearOfStudy;
    private final ArrayList<Skill> skillsToTeach = new ArrayList<>();
    private final ArrayList<Skill> skillsToLearn = new ArrayList<>();

    public Student(String id, String name, String email, String department, int yearOfStudy) {
        setId(id);
        setName(name);
        setEmail(email);
        setDepartment(department);
        setYearOfStudy(yearOfStudy);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = requireText(id, "Student ID");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = requireText(name, "Name");
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        String validEmail = requireText(email, "Email");
        if (!validEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        this.email = validEmail;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = requireText(department, "Department");
    }

    public int getYearOfStudy() {
        return yearOfStudy;
    }

    public void setYearOfStudy(int yearOfStudy) {
        if (yearOfStudy < 1) {
            throw new IllegalArgumentException("Year of study must be a positive number.");
        }
        this.yearOfStudy = yearOfStudy;
    }

    public List<Skill> getSkillsToTeach() {
        return List.copyOf(skillsToTeach);
    }

    public List<Skill> getSkillsToLearn() {
        return List.copyOf(skillsToLearn);
    }

    public void addSkillToTeach(Skill skill) {
        addSkill(skillsToTeach, skill);
    }

    public void addSkillToLearn(Skill skill) {
        addSkill(skillsToLearn, skill);
    }

    private static void addSkill(ArrayList<Skill> skills, Skill skill) {
        if (skill == null) {
            throw new IllegalArgumentException("Skill details are required.");
        }
        boolean alreadyAdded = skills.stream()
                .anyMatch(existing -> existing.getName().equalsIgnoreCase(skill.getName()));
        if (alreadyAdded) {
            throw new IllegalArgumentException("This skill has already been added.");
        }
        skills.add(skill);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }
}
