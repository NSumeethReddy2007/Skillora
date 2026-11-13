package service;

import model.Skill;
import model.Student;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class SkillMatcher {
    public List<SkillSearchResult> searchSkills(
            List<Student> students, String skillName, String category) {
        String nameFilter = normalize(skillName);
        String categoryFilter = normalize(category);
        if (nameFilter.isEmpty() && categoryFilter.isEmpty()) {
            throw new IllegalArgumentException("Enter a skill name or category to search.");
        }

        List<SkillSearchResult> results = new ArrayList<>();
        for (Student student : students) {
            List<Skill> matchingSkills = student.getSkillsToTeach().stream()
                    .filter(skill -> matches(skill.getName(), nameFilter))
                    .filter(skill -> matches(skill.getCategory(), categoryFilter))
                    .toList();
            if (!matchingSkills.isEmpty()) {
                results.add(new SkillSearchResult(student, matchingSkills));
            }
        }
        return List.copyOf(results);
    }

    public List<MatchResult> findMatches(
            Student learner, List<Student> students, String skillName, String category) {
        if (learner == null) {
            throw new IllegalArgumentException("Select a student who wants to learn a skill.");
        }

        List<Skill> wantedSkills = learner.getSkillsToLearn();
        if (wantedSkills.isEmpty()) {
            return List.of();
        }

        String nameFilter = normalize(skillName);
        String categoryFilter = normalize(category);
        Set<String> wantedNames = wantedSkills.stream()
                .map(skill -> normalize(skill.getName()))
                .collect(Collectors.toSet());

        List<MatchResult> results = new ArrayList<>();
        for (Student provider : students) {
            if (provider.getId().equalsIgnoreCase(learner.getId())) {
                continue;
            }

            List<Skill> matchingSkills = provider.getSkillsToTeach().stream()
                    .filter(skill -> wantedNames.contains(normalize(skill.getName())))
                    .filter(skill -> matches(skill.getName(), nameFilter))
                    .filter(skill -> matches(skill.getCategory(), categoryFilter))
                    .toList();
            if (!matchingSkills.isEmpty()) {
                double percentage = matchingSkills.size() * 100.0 / wantedNames.size();
                results.add(new MatchResult(provider, matchingSkills, percentage));
            }
        }

        results.sort(Comparator.comparingDouble(MatchResult::matchPercentage).reversed()
                .thenComparing(result -> result.student().getName(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(results);
    }

    private static boolean matches(String value, String filter) {
        return filter.isEmpty() || normalize(value).contains(filter);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record SkillSearchResult(Student student, List<Skill> matchingSkills) {
        public SkillSearchResult {
            matchingSkills = List.copyOf(matchingSkills);
        }
    }

    public record MatchResult(Student student, List<Skill> matchingSkills, double matchPercentage) {
        public MatchResult {
            matchingSkills = List.copyOf(matchingSkills);
        }
    }
}
