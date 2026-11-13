package model;

import java.util.Objects;
import java.util.Locale;

public class Skill {
    private final String name;
    private final String category;

    public Skill(String name, String category) {
        this.name = requireText(name, "Skill name");
        this.category = requireText(category, "Skill category");
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Skill skill)) {
            return false;
        }
        return name.equalsIgnoreCase(skill.name)
                && category.equalsIgnoreCase(skill.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name.toLowerCase(Locale.ROOT), category.toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return name + " (" + category + ")";
    }
}
