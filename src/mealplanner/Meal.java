package mealplanner;

import java.util.List;
import java.util.TreeSet;

public class Meal {
    mealplanner.Category category;
    String name;
    List<String> ingredients;

    public mealplanner.Category getCategory() {
        return category;
    }

    public void setCategory(mealplanner.Category category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getIngredients() {
        return ingredients;
    }

    public void setIngredients(List<String> ingredients) {
        this.ingredients = ingredients;
    }

    public Meal(mealplanner.Category category, String name, List<String> ingredients) {
        this.category = category;
        this.name = name;
        this.ingredients = ingredients;
    }

    @Override
    public String toString() {
        return String.format("""
                Name: %s
                Ingredients:
                %s                
                """, this.name, String.join("\n", ingredients));
    }
}