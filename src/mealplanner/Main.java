package mealplanner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class Main {

    public static final String[] daysOfWeek = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static boolean isScannerClosed = false;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        final String DB_URL = "jdbc:postgresql://localhost:5432/meals_db";
        final String USER = "postgres";
        final String PASS = "1111";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASS)) {
            if (connection != null) {
                Statement statement = connection.createStatement();
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS meals (" +           // TABLE meals
                        "meal_id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY," +       // meal_id PRIMARY KEY
                        "category VARCHAR(10) NOT NULL," +                                  // category
                        "meal VARCHAR(255) NOT NULL" +                                      // meal(name)
                        ")");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS ingredients (" +     // TABLE ingredients
                        "ingredient_id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY," + // ingredient_id PRIMARY KEY
                        "ingredient VARCHAR(255) NOT NULL," +                               // ingredient(name)
                        "meal_id INTEGER NOT NULL REFERENCES meals(meal_id)" +              // meal_id FOREIGN KEY (TABLE meals)
                        ")");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS plan (" +            // TABLE plan
                        "plan_id VARCHAR(10)," +                                            // plan_id
                        "category VARCHAR(10) NOT NULL," +                                  // category
                        "meal_id INTEGER NOT NULL REFERENCES meals(meal_id)" +              // meal_id FOREIGN KEY (TABLE meals)
                        ")");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS shoppingList (" +    // TABLE shoppingList
                        "ingredient VARCHAR(255) PRIMARY KEY," +                            // ingredient(name)
                        "quantity INT" +                                                    // quantity
                        ")");

                start(scanner, connection);
            } else {
                System.out.println("Failed to make connection!");
            }
        } catch (SQLException e) {
            System.out.println("PostgreSQL connection failure.");
            e.printStackTrace();
        }
    }

    //command EXIT : exit, close scanner & close connection
    private static void exit(Scanner scanner, Connection connection) {
        System.out.println("Bye!");
        scanner.close();
        isScannerClosed = true;
        try {
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // start : select command
    private static void start(Scanner scanner, Connection connection){
        Command command;
        while (true) {
            if (isScannerClosed) {
                return;
            }
            System.out.println("What would you like to do (add, show, plan, save, exit)?");
            String input = scanner.nextLine().toUpperCase();
            if (Utils.isValidEnumValue(Command.class, input)) {
                try {
                    command = Command.valueOf(input);
                    switch (command) {
                        case ADD -> addMeal(scanner, connection); // add meal by category
                        case SHOW -> showMeals(scanner, connection); // show meals by category
                        case PLAN -> makePlan(scanner, connection); // create plan for a week (one day = 3 categories) and create a shopping list
                        case SAVE -> saveShoppingList(scanner, connection);
                        case EXIT -> { // stop app.
                            exit(scanner, connection);
                            return;
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            } else {
                start(scanner, connection);
            }
        }
    }

    // command ADD : add meal (category, name(meal)) in meals TABLE
    private static void addMeal(Scanner scanner, Connection connection) throws SQLException {
        Meal meal = new Meal(getCategory(scanner),
                getName(scanner),
                getIngredients(scanner)); // create new Meal
        String insertMealSQL = "INSERT INTO meals (category, meal) VALUES (?, ?) RETURNING meal_id";
        try (PreparedStatement preparedStatement = connection.prepareStatement(insertMealSQL)) {
            preparedStatement.setString(1, String.valueOf(meal.category));
            preparedStatement.setString(2, meal.name);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    int mealId = resultSet.getInt(1);
                    addIngredients(meal, mealId, connection);
                }
            }
            System.out.println("The meal has been added!");
            start(scanner, connection);
        }
    }

    // command SHOW : get meals in meals TABLE & get ingredients by meal_id in ingredients TABLE
    private static void showMeals(Scanner scanner, Connection connection) throws SQLException {
        List<Meal> meals = getMealsWithIngredients(connection, null);
        if (meals.isEmpty()) {
            System.out.println("No meals saved. Add a meal first.");
        } else {
            System.out.println("Which category do you want to print (breakfast, lunch, dinner)?"); // select category to display meals by category
            selectCategory(scanner, connection);
        }
        start(scanner, connection);
    }

    // command PLAN : make plan day by day, category by category
    private static void makePlan(Scanner scanner, Connection connection) throws SQLException {
        clearTables(connection);
        Category[] categories = Category.values();
        Map<Category, Map<String, Integer>> mealsByCategory = getMealsByCategory(connection);
        for (String day : daysOfWeek) {
            planMealsForDay(scanner, connection, categories, mealsByCategory, day);
        }
        displayPlanDay(connection); // get read and display plan
        start(scanner, connection);
    }

    // clear TABLE plan && ingredients when plan process start
    private static void clearTables(Connection connection) {
        Utils.clearTableIfNotEmpty(connection, "plan");
        Utils.clearTableIfNotEmpty(connection, "shoppingList");
    }

    // get meals by category
    private static Map<Category, Map<String, Integer>> getMealsByCategory(Connection connection) throws SQLException {
        Map<Category, Map<String, Integer>> mealsByCategory = new HashMap<>();
        mealsByCategory.put(Category.BREAKFAST, getMealIdAndMealByCategory(connection, Category.BREAKFAST));
        mealsByCategory.put(Category.LUNCH, getMealIdAndMealByCategory(connection, Category.LUNCH));
        mealsByCategory.put(Category.DINNER, getMealIdAndMealByCategory(connection, Category.DINNER));
        return mealsByCategory;
    }

    // make plan by day
    private static void planMealsForDay(Scanner scanner, Connection connection, Category[] categories,
                                        Map<Category, Map<String, Integer>> mealsByCategory, String day) throws SQLException {
        System.out.println(day);
        for (Category cat : categories) {
            displayMeals(mealsByCategory.get(cat));
            int mealId = chooseMeal(scanner, mealsByCategory.get(cat), cat, day);
            addPlan(connection, day, cat, mealId);
        }
        System.out.printf("Yeah! We planned the meals for %s.%n%n", day);
    }

    // display meals
    private static void displayMeals(Map<String, Integer> meals) {
        for (Map.Entry<String, Integer> entry : meals.entrySet()) {
            System.out.println(entry.getKey());
        }
    }

    // choose meal
    private static int chooseMeal(Scanner scanner, Map<String, Integer> meals, Category category, String day) {
        while (true) {
            String str = category.toString().toLowerCase();
            System.out.printf("Choose the %s for %s from the list above:%n", str, day);
            String choice = scanner.nextLine();
            if (meals.containsKey(choice)) {
                return meals.get(choice);
            } else {
                System.out.println("This meal doesn’t exist. Choose a meal from the list above.");
            }
        }
    }

    // add tuple plan in plan TABLE (plan_id, category, meal_id)
    private static void addPlan(Connection connection, String day, Category cat, int mealId) throws SQLException {
        String insertMealSQL = "INSERT INTO plan (plan_id, category, meal_id) VALUES (?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(insertMealSQL)) {
            preparedStatement.setString(1, day);
            preparedStatement.setString(2, String.valueOf(cat));
            preparedStatement.setInt(3, mealId);
            try {
                preparedStatement.executeUpdate();
                List<String> ingredients = getIngredientsByMealId(connection, mealId);
                for (String ingredient : ingredients) {
                    addIngredientInShoppingList(connection, ingredient); // add ingredient in shoppingListTable
                }
            } catch (SQLException e) {
                System.out.println("Error executing update: " + e.getMessage());
            }

        }
    }

    // add ingredient in shoppingList TABLE -> if ingredient exist increment quantity else add tuple
    private static void addIngredientInShoppingList(Connection connection, String name) {
        String sql = "INSERT INTO shoppingList (ingredient, quantity) VALUES (?, ?) " +
                "ON CONFLICT (ingredient) DO UPDATE SET quantity = shoppingList.quantity + EXCLUDED.quantity";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, name);
            preparedStatement.setInt(2, 1);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    // command SAVE : save shoppingList
    private static void saveShoppingList(Scanner scanner, Connection connection) {
        String sqlPlan = "SELECT COUNT(*) AS rowcount FROM plan";
        String sqlShoppingList = "SELECT * FROM shoppingList";
        try (PreparedStatement prepPlan = connection.prepareStatement(sqlPlan)) {
            ResultSet resultPlan = prepPlan.executeQuery();
            if (resultPlan.next() && resultPlan.getInt("rowcount") > 0 ) {
                try (PreparedStatement prepShoppingList = connection.prepareStatement(sqlShoppingList);
                     ResultSet resultShoppingList = prepShoppingList.executeQuery()) {
                    saveFile(scanner, resultShoppingList);
                } catch (SQLException | IOException e) {
                    System.out.println("An error occurred while saving the shopping list: " + e.getMessage());
                }
            } else {
                System.out.println("Unable to save. Plan your meals first.");
            }
        } catch (SQLException e) {
            System.out.println("An error occurred while checking the plan table: " + e.getMessage());
        }
    }

    // saveFile
    private static void saveFile(Scanner scanner, ResultSet rs) throws IOException, SQLException {
        System.out.println("Input a filename:");
        String fileName = scanner.nextLine();
        File file = new File("./" + fileName);
        try (FileWriter writer = new FileWriter(file)) {
            while (rs.next()) {
                Ingredient ingredient = new Ingredient(rs.getString("ingredient"), rs.getInt("quantity"));
                writer.write(String.valueOf(ingredient));
            }
            System.out.println("Saved!");
        } catch (IOException e) {
            System.out.printf("An exception occurred %s", e.getMessage());
        }
    }

    // add tuple ingredient in ingredients TABLE (ingredient, meal_id)
    private static void addIngredients(Meal meal, int mealId, Connection connection) throws SQLException {
        String insertIngredientSQL = "INSERT INTO ingredients (ingredient, meal_id) VALUES (?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(insertIngredientSQL)) {
            for (String ingredient : meal.ingredients) {
                preparedStatement.setString(1, ingredient);
                preparedStatement.setInt(2, mealId);
                preparedStatement.executeUpdate();
            }
        }
    }

    // create List<Meal> meals -> GET meals by given category -> GET ingredients by meal_id ->
    // -> for each meal create new Meal and add meal in List<Meal> meals
    private static List<Meal> getMealsWithIngredients(Connection connection, String category) throws SQLException {
        List<Meal> meals = new ArrayList<>();
        String mealQuery = category == null ? "SELECT * FROM meals" : "SELECT * FROM meals WHERE category = ?";
        try (PreparedStatement mealStatement = connection.prepareStatement(mealQuery)) {
            if (category != null) {
                mealStatement.setString(1, category);
            }
            try (ResultSet mealResultSet = mealStatement.executeQuery()) {
                while (mealResultSet.next()) {
                    Category mealCategory = Category.valueOf(mealResultSet.getString("category"));
                    String mealName = mealResultSet.getString("meal");
                    int mealId = mealResultSet.getInt("meal_id");
                    List<String> ingredients = getIngredientsByMealId(connection, mealId);
                    meals.add(new Meal(mealCategory, mealName, ingredients));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error while fetching meals and ingredients: " + e.getMessage());
            throw e;
        }
        return meals;
    }

    private static List<String> getIngredientsByMealId(Connection connection, int mealId) throws SQLException {
        String ingredientQuery = "SELECT ingredient FROM ingredients WHERE meal_id = ?";
        List<String> ingredients = new ArrayList<>();
        try (PreparedStatement ingredientStatement = connection.prepareStatement(ingredientQuery)) {
            ingredientStatement.setInt(1, mealId);
            try (ResultSet ingredientResultSet = ingredientStatement.executeQuery()) {
                while (ingredientResultSet.next()) {
                    ingredients.add(ingredientResultSet.getString("ingredient"));
                }
            }
        }
        return ingredients;
    }

    // fetch plan day by day, category by category
    private static void displayPlanDay(Connection connection) throws SQLException {
        String planQuery = "SELECT * FROM plan WHERE plan_id = ? ";
        String mealIdQuery = "SELECT meal FROM meals WHERE meal_id = ?";
        try (PreparedStatement mealStatement = connection.prepareStatement(mealIdQuery)) {
            for (String day : daysOfWeek) {
                DayPlan dayPlan = new DayPlan(day);
                populateDayPlan(connection, planQuery, mealStatement, day, dayPlan);
                System.out.println(dayPlan); // display plan day by day, category by category
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
            throw e;
        }
    }

    private static void populateDayPlan(Connection connection, String planQuery,
                                        PreparedStatement mealStatement, String day, DayPlan dayPlan) throws SQLException {
        try (PreparedStatement planStatement = connection.prepareStatement(planQuery)) {
            planStatement.setString(1, day);
            try (ResultSet planResultSet = planStatement.executeQuery()) {
                while (planResultSet.next()) {
                    Category mealCategory = Category.valueOf(planResultSet.getString("category"));
                    int mealId = planResultSet.getInt("meal_id");
                    String meal = getMealById(mealStatement, mealId);
                    if (meal != null) {
                        assignMealToCategory(dayPlan, mealCategory, meal);
                    }
                }
            }
        }
    }

    private static void assignMealToCategory(DayPlan dayPlan, Category mealCategory, String meal) {
        switch (mealCategory) {
            case BREAKFAST -> dayPlan.setBreakfastMeal(meal);
            case LUNCH -> dayPlan.setLunchMeal(meal);
            case DINNER -> dayPlan.setDinnerMeal(meal);
        }
    }

    private static String getMealById(PreparedStatement mealStatement, int mealId) throws SQLException {
        mealStatement.setInt(1, mealId);
        try (ResultSet mealResultSet = mealStatement.executeQuery()) {
            if (mealResultSet.next()) {
                return mealResultSet.getString("meal");
            }
        }
        return null;
    }

    // get meal_id from meals by category
    private static Map<String, Integer> getMealIdAndMealByCategory(Connection connection, Category category) throws SQLException {
        Map<String, Integer> meals = new TreeMap<>();
        String mealQuery = "SELECT meal_id, meal FROM meals WHERE category = ?";
        try (PreparedStatement mealStatement = connection.prepareStatement(mealQuery)) {
            mealStatement.setString(1, String.valueOf(category));
            try (ResultSet mealResultSet = mealStatement.executeQuery()) {
                while (mealResultSet.next()) {
                    int mealId = mealResultSet.getInt("meal_id");
                    String mealName = mealResultSet.getString("meal");
                    meals.put(mealName, mealId);
                }
            }
        }
        return meals;
    }

    // CREATE List<Meal> meals fetching meals with ingredients by category
    private static void selectCategory(Scanner scanner, Connection connection) {
        List<Meal> meals = new ArrayList<>();
        Category category = null;
        while (category == null) {
            String input = scanner.nextLine().toUpperCase();
            if (Utils.isValidEnumValue(Category.class, input)) {
                try {
                    category = Category.valueOf(input);
                    meals = getMealsWithIngredients(connection, input);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            } else {
                System.out.println("Wrong meal category! Choose from: breakfast, lunch, dinner.");
            }
        }
        // DISPLAY meals with ingredients by category
        if (!meals.isEmpty()) {
            System.out.printf("Category: %s\n\n", category);
            for (Meal meal : meals) {
                System.out.println(meal);
            }
        } else {
            System.out.println("No meals found.");
            start(scanner, connection);
        }
    }

    // INPUT : get category
    private static Category getCategory(Scanner scanner) {
        Category category = null;
        System.out.println("Which meal do you want to add (breakfast, lunch, dinner)?");
        while (category == null) {
            String input = scanner.nextLine().toUpperCase();
            try {
                category = Category.valueOf(input);
            } catch (IllegalArgumentException e) {
                System.out.println("Wrong meal category! Choose from: breakfast, lunch, dinner.");
            }
        }
        return category;
    }

    // INPUT : get name (meal)
    private static String getName(Scanner scanner) {
        String input = null;
        System.out.println("Input the meal's name:");

        while (input == null) {
            input = scanner.nextLine();
            if (!Utils.isValid(input)) {
                System.out.println("Wrong format. Use letters only!");
                input = null;
            }
        }

        return input;
    }

    // INPUT : get ingredients
    private static List getIngredients(Scanner scanner) {
        List<String> ingredients = new ArrayList<>();
        System.out.println("Input the ingredients:");
        while (ingredients.isEmpty()) {
            String input = scanner.nextLine();
            String[] strings = input.split(",");
            for (String str : strings) {
                if (Utils.isValid(str.trim())) {
                    ingredients.add(str.trim());
                } else {
                    System.out.println("Wrong format. Use letters only!");
                    ingredients.clear();
                    break;
                }
            }
        }
        return ingredients;
    }

}