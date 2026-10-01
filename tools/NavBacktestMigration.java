import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Additive migration only; reads existing credentials without logging them. */
public class NavBacktestMigration {
    public static void main(String[] args) throws Exception {
        Map<String,String> env = new HashMap<>();
        for (String line : Files.readAllLines(Path.of("quant-fund-server/.env"))) {
            int split = line.indexOf('=');
            if (split <= 0 || line.stripLeading().startsWith("#")) continue;
            String value = line.substring(split+1).trim();
            if (value.length()>1 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1,value.length()-1);
            env.put(line.substring(0,split).trim(), value);
        }
        try (Connection c = DriverManager.getConnection(env.get("QUANTFUND_DB_URL"), env.get("QUANTFUND_DB_USERNAME"), env.get("QUANTFUND_DB_PASSWORD"))) {
            if (Arrays.asList(args).contains("--apply")) {
                for (String sql : Files.readString(Path.of("docs/sql/013_nav_technical_backtest.sql")).split(";")) {
                    if (!sql.isBlank()) try (Statement s = c.createStatement()) { s.execute(sql); }
                }
            }
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM nav_technical_backtest")) {
                r.next(); System.out.println("NAV backtest table ready; saved result count=" + r.getLong(1));
            }
        }
    }
}
