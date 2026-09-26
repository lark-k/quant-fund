import java.nio.file.*;
import java.sql.*;
import java.math.*;
import java.util.*;

/** Run from the repo root using the existing MySQL JDBC driver. Never prints credentials. */
public class CumulativeProfitMigration {
    private static final Map<String, String> SEEDS = Map.of(
        "017811", "-15.10", "016874", "40.65", "025833", "-46.83", "013403", "-93.59",
        "012922", "-109.09", "021180", "-174.10", "021528", "-135.32");
    private static final String VERSION = "user-confirmed-cumulative-2026-09-26";
    record Holding(long id, long user, long account, String code, String name, BigDecimal shares,
                   BigDecimal nav, java.sql.Date navDate) {}
    public static void main(String[] args) throws Exception {
        Map<String,String> env = new HashMap<>();
        for (String line : Files.readAllLines(Path.of("quant-fund-server/.env"))) {
            int split = line.indexOf('=');
            if (split > 0 && !line.stripLeading().startsWith("#")) {
                String value = line.substring(split+1).trim();
                if (value.length()>1 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1,value.length()-1);
                env.put(line.substring(0,split).trim(),value);
            }
        }
        try (Connection c = DriverManager.getConnection(env.get("QUANTFUND_DB_URL"), env.get("QUANTFUND_DB_USERNAME"), env.get("QUANTFUND_DB_PASSWORD"))) {
            List<Holding> holdings = new ArrayList<>();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("""
                SELECT h.id,h.user_id,h.account_id,h.fund_code,h.fund_name,h.holding_share,n.unit_nav,n.nav_date
                FROM fund_holding h LEFT JOIN fund_nav_daily n ON n.fund_code=h.fund_code AND n.deleted=0
                AND n.nav_date=(SELECT MAX(n2.nav_date) FROM fund_nav_daily n2 WHERE n2.fund_code=h.fund_code AND n2.deleted=0 AND n2.unit_nav>0)
                WHERE h.deleted=0 ORDER BY h.user_id,h.account_id,h.fund_code
                """)) {
                while(rs.next()) {
                    Holding h = new Holding(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4),rs.getString(5),rs.getBigDecimal(6),rs.getBigDecimal(7),rs.getDate(8));
                    holdings.add(h);
                    System.out.printf("holding=%d user=%d account=%d code=%s name=%s shares=%s officialNav=%s date=%s%n",h.id,h.user,h.account,h.code,h.name,h.shares,h.nav,h.navDate);
                }
            }
            if (Arrays.asList(args).contains("--verify")) {
                try (Statement s=c.createStatement(); ResultSet rs=s.executeQuery("SELECT f.fund_code,f.profit FROM cumulative_profit_fund f JOIN cumulative_profit_user u ON u.user_id=f.user_id WHERE u.seed_version='"+VERSION+"' ORDER BY f.fund_code")) {
                    while(rs.next()) System.out.println("CUMULATIVE "+rs.getString(1)+" = "+rs.getBigDecimal(2));
                }
                try (Statement s=c.createStatement(); ResultSet rs=s.executeQuery("SELECT u.opening_history,SUM(f.profit),u.opening_history+SUM(f.profit) FROM cumulative_profit_user u JOIN cumulative_profit_fund f ON f.user_id=u.user_id WHERE u.seed_version='"+VERSION+"' GROUP BY u.user_id,u.opening_history")) {
                    while(rs.next()) System.out.println("VERIFIED history="+rs.getBigDecimal(1)+" funds="+rs.getBigDecimal(2)+" total="+rs.getBigDecimal(3));
                }
            }
            if (!Arrays.asList(args).contains("--apply")) return;
            Map<String,List<Holding>> groups = new HashMap<>();
            for (Holding h : holdings) groups.computeIfAbsent(h.user+":"+h.account,k->new ArrayList<>()).add(h);
            List<List<Holding>> candidates = groups.values().stream().filter(g -> g.size()==7 && g.stream().map(Holding::code).collect(java.util.stream.Collectors.toSet()).equals(SEEDS.keySet())).toList();
            if(candidates.size()!=1) throw new IllegalStateException("Expected exactly one unambiguous account with the seven specified funds; no seed was applied.");
            List<Holding> target=candidates.getFirst();
            if(target.stream().anyMatch(h->h.nav==null || h.nav.signum()<=0 || h.navDate==null)) throw new IllegalStateException("Missing official NAV anchor; no seed was applied.");
            for(String sql:Files.readString(Path.of("docs/sql/012_cumulative_profit.sql")).split(";")) {
                if(!sql.isBlank()) try(Statement s=c.createStatement()){s.execute(sql);}
            }
            c.setAutoCommit(false);
            try {
                long user=target.getFirst().user;
                String before=fingerprint(c,user);
                execute(c,"INSERT INTO cumulative_profit_user(user_id) VALUES (?) ON DUPLICATE KEY UPDATE user_id=user_id",user);
                try(PreparedStatement s=c.prepareStatement("SELECT seed_version FROM cumulative_profit_user WHERE user_id=? FOR UPDATE")){
                    s.setLong(1,user);try(ResultSet rs=s.executeQuery()){rs.next();if(VERSION.equals(rs.getString(1))){c.rollback();System.out.println("Seed already applied; all current balances preserved.");return;}}
                }
                try(PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM cumulative_profit_event WHERE user_id=?")){
                    s.setLong(1,user);try(ResultSet rs=s.executeQuery()){rs.next();if(rs.getInt(1)>0)throw new IllegalStateException("Existing ledger activity: refusing to overwrite balances.");}
                }
                for(Holding h:target){
                    execute(c,"INSERT INTO cumulative_profit_fund(user_id,account_id,fund_code,profit) VALUES (?,?,?,?)",h.user,h.account,h.code,new BigDecimal(SEEDS.get(h.code)));
                    execute(c,"INSERT INTO cumulative_profit_position(holding_id,user_id,account_id,fund_code,shares,book_value,nav_date,unit_nav,minimum_nav_date) VALUES (?,?,?,?,?,?,?,?,?)",h.id,h.user,h.account,h.code,h.shares,h.shares.multiply(h.nav),h.navDate,h.nav,h.navDate);
                    execute(c,"INSERT INTO cumulative_profit_event(event_key,user_id,account_id,fund_code,holding_id,event_type,nav_date,profit_delta,shares_after,book_value_after) VALUES (?,?,?,?,?,'SEED',?,?,?,?)","seed:"+h.id,h.user,h.account,h.code,h.id,h.navDate,new BigDecimal(SEEDS.get(h.code)),h.shares,h.shares.multiply(h.nav));
                }
                execute(c,"UPDATE cumulative_profit_user SET opening_history=?,seed_version=? WHERE user_id=?",new BigDecimal("-232.13"),VERSION,user);
                String after=fingerprint(c,user);
                if(!before.equals(after)) throw new IllegalStateException("Existing financial data changed during migration; seed rolled back for review.");
                c.commit();
                System.out.println("Seed applied once: seven funds=-533.38; opening history=-232.13; total=-765.51. Existing financial tables unchanged.");
                Path report=Path.of("quant-fund-web/qa-artifacts/cumulative-seed-verification.txt");
                Files.createDirectories(report.getParent());
                Files.writeString(report,"Seed: "+VERSION+"\nUser: "+user+"\nBefore financial SHA256: "+before+"\nAfter financial SHA256: "+after+"\nFund sum: -533.38\nOpening history: -232.13\nTotal: -765.51\n"+target.toString());
            }catch(Exception error){c.rollback();throw error;}
        }
    }
    private static void execute(Connection c,String sql,Object...args)throws SQLException{
        try(PreparedStatement s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}
    }
    private static String fingerprint(Connection c,long user)throws Exception{
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        for(String table:List.of("fund_holding","portfolio_account","trade_record","holding_snapshot")){
            digest.update(table.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try(PreparedStatement s=c.prepareStatement("SELECT * FROM "+table+" WHERE user_id=? ORDER BY id")){
                s.setLong(1,user);try(ResultSet rs=s.executeQuery()){
                    while(rs.next())for(int i=1;i<=rs.getMetaData().getColumnCount();i++)digest.update((Objects.toString(rs.getObject(i),"<null>")+"\u0000").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
