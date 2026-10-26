import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

// 부하 테스트 중 일정 간격으로 커넥션 풀과 DB 잠금 대기 수를 CSV로 기록한다. (k6/collect-metrics.sh가 실행)
// - 커넥션 풀: JMX로 Hikari를 직접 읽는다. HTTP·로그인 필터·DB 연결을 거치지 않아서 서버가 붐벼도 멈추지 않는다.
//   (Actuator로 읽으면 로그인 필터가 DB를 조회하느라 참여 요청과 같은 연결 줄에 서서 멈춘다)
// - 잠금 대기: PostgreSQL에 직접 물어본다. 서버를 거치지 않는다.
// 서버는 JMX 옵션과 loadtest 프로필(register-mbeans)로 켜져 있어야 한다.
public class MetricsCollector {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String LOCK_WAITS_SQL =
        "select count(*) from pg_stat_activity where wait_event_type = 'Lock'";

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "metrics.csv";
        long intervalMillis = Math.round(Double.parseDouble(env("INTERVAL", "0.2")) * 1000);
        String jmxPort = env("JMX_PORT", "9010");
        String dbContainer = env("DB_CONTAINER", "naegi-postgres");

        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://127.0.0.1:" + jmxPort + "/jmxrmi");
        try (JMXConnector connector = JMXConnectorFactory.connect(url);
             PrintWriter csv = new PrintWriter(new FileWriter(out), true)) {
            MBeanServerConnection server = connector.getMBeanServerConnection();
            ObjectName pool = new ObjectName("com.zaxxer.hikari:type=Pool (HikariPool-1)");

            csv.println("time,hikari_active,hikari_pending,db_lock_waits");
            System.out.println("기록 시작: " + out + " (Ctrl+C로 멈춤)");
            while (true) {
                String time = LocalTime.now().format(TIME);
                Object active = server.getAttribute(pool, "ActiveConnections");
                Object pending = server.getAttribute(pool, "ThreadsAwaitingConnection");
                csv.println(time + "," + active + "," + pending + "," + lockWaits(dbContainer));
                Thread.sleep(intervalMillis);
            }
        }
    }

    private static String lockWaits(String dbContainer) throws Exception {
        Process process = new ProcessBuilder(
            "docker", "exec", dbContainer, "psql", "-U", "naegi", "-d", "naegi", "-tAc", LOCK_WAITS_SQL
        ).start();
        String result = new String(process.getInputStream().readAllBytes()).trim();
        process.waitFor();
        return result;
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
