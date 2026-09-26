package db.migration;

import com.kcalma.food.reference.FoodReferenceCsvParser;
import com.kcalma.food.reference.FoodReferenceCsvRow;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.zip.GZIPInputStream;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Loads the compact USDA reference dataset (SR Legacy + Foundation Foods + FNDDS survey foods)
 * bundled at {@code src/main/resources/usda/food_reference.csv.gz} into {@code
 * app.food_reference} (created by V4__enable_pg_trgm_and_food_reference.sql). See {@code
 * tools/usda/build_food_reference.py} for how that CSV is generated and its exact column order,
 * which {@link FoodReferenceCsvParser} must match.
 *
 * <p>Idempotent: {@code ON CONFLICT (fdc_id) DO NOTHING} means re-running this migration (e.g.
 * after a {@code flyway repair} in a throwaway/dev environment) never creates duplicate rows.
 *
 * <p>Plain JDBC via {@link Context#getConnection()} — Flyway constructs Java migrations itself
 * (not as a Spring bean), so no repository/Spring wiring is available here.
 */
public class V5__LoadFoodReferenceData extends BaseJavaMigration {

    private static final String RESOURCE = "/usda/food_reference.csv.gz";
    private static final int BATCH_SIZE = 500;

    private static final String INSERT_SQL =
            """
            INSERT INTO app.food_reference
                (fdc_id, description, data_type, kcal_per_100, protein_per_100, fat_per_100,
                 carbs_per_100, fiber_per_100, sugar_per_100, sodium_mg_per_100, search_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (fdc_id) DO NOTHING
            """;

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (InputStream raw = getClass().getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            }
            try (GZIPInputStream gzip = new GZIPInputStream(raw);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8));
                    PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
                loadRows(reader, statement);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + RESOURCE, e);
        }
    }

    private static void loadRows(BufferedReader reader, PreparedStatement statement) throws IOException, SQLException {
        int pending = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            bind(statement, FoodReferenceCsvParser.parseLine(line));
            statement.addBatch();
            pending++;
            if (pending == BATCH_SIZE) {
                statement.executeBatch();
                pending = 0;
            }
        }
        if (pending > 0) {
            statement.executeBatch();
        }
    }

    private static void bind(PreparedStatement statement, FoodReferenceCsvRow row) throws SQLException {
        statement.setLong(1, row.fdcId());
        statement.setString(2, row.description());
        statement.setString(3, row.dataType());
        statement.setBigDecimal(4, row.kcalPer100());
        statement.setBigDecimal(5, row.proteinPer100());
        statement.setBigDecimal(6, row.fatPer100());
        statement.setBigDecimal(7, row.carbsPer100());
        statement.setBigDecimal(8, row.fiberPer100());
        statement.setBigDecimal(9, row.sugarPer100());
        statement.setBigDecimal(10, row.sodiumMgPer100());
        statement.setString(11, row.searchName());
    }
}
