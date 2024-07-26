package mealplanner;

public class Ingredient {
    String name;
    int quantity;

    public Ingredient(String name, int quantity) {
        this.name = name;
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        if (quantity > 1) {
            return name + " x" + quantity + "\n";
        } else {
            return name + "\n";
        }
    }
}