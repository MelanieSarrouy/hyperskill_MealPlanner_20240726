package mealplanner;

public class DayPlan {
    String day;
    String breakfastMeal;
    String lunchMeal;
    String dinnerMeal;

    public DayPlan(String day) {
        this.day = day;
    }

    public void setBreakfastMeal(String breakfastMeal) {
        this.breakfastMeal = breakfastMeal;
    }

    public void setLunchMeal(String lunchMeal) {
        this.lunchMeal = lunchMeal;
    }

    public void setDinnerMeal(String dinnerMeal) {
        this.dinnerMeal = dinnerMeal;
    }

    public String toString() {
        return String.format("""
                %s
                Breakfast: %s 
                Lunch: %s 
                Dinner: %s               
                """, this.day, this.breakfastMeal, this.lunchMeal, this.dinnerMeal);
    }

}