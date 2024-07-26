package mealplanner;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static final Pattern PATTERN = Pattern.compile("^[a-zÀ-ÿ]+(['a-zÀ-ÿ]+)*(\\s[a-zÀ-ÿ]+(['a-zÀ-ÿ]+)*)*$", Pattern.CASE_INSENSITIVE);

    public static boolean isValid(String str) {
        Matcher matcher = PATTERN.matcher(str);
        return matcher.matches();
    }

    public static <E extends Enum<E>> boolean isValidEnumValue(Class<E> enumClass, String input) {
        for (E enumValue : enumClass.getEnumConstants()) {
            if (enumValue.name().equals(input)) {
                return true;
            }
        }
        return false;
    }

    // clear TABLE plan if not empty
    public static void clearTableIfNotEmpty (Connection connection, String tableName) {
        Statement statement;
        ResultSet resultSet;
        try {
            statement = connection.createStatement();
            String countQuery = "SELECT COUNT(*) AS rowcount FROM " + tableName;
            resultSet = statement.executeQuery(countQuery);
            resultSet.next();
            int count = resultSet.getInt("rowcount");
            if (count > 0) {
                String truncateQuery = "DELETE FROM " + tableName;
                statement.executeUpdate(truncateQuery);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}