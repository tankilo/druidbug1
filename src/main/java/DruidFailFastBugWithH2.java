import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.h2.tools.Server;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.druid.pool.DruidAbstractDataSource;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;

/**
 * 这里没发现h2怎么限制server连接数
 * 生产环境是mysql连接数过多以后，突然有些连接释放出来时正好被直接调用createPhysicalConnection把createError给清理了
 * 所以这里需要把在server.start那一行打断点，断点到了以后把两个CreateConnectionTask断点停在for循环入口 模拟创建失败的情况
 * 不然CreateConnectionTask会在直接调用createPhysicalConnection前 通过emptyWait return那个语句
 */
public class DruidFailFastBugWithH2 {
    private static final Logger logger = LoggerFactory.getLogger("bugtest");

    public static void main(String[] args) throws SQLException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        Server server = Server.createTcpServer(null).start();
        logger.info("Start Server at: " + server.getURL());

        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUrl("jdbc:h2:" + server.getURL() + "/mem:gptestdb");
        druidDataSource.setUsername("");
        druidDataSource.setPassword("");
        druidDataSource.setMinIdle(10);
        druidDataSource.setMaxActive(30);
        druidDataSource.setFailFast(true);
        druidDataSource.setTimeBetweenConnectErrorMillis(3000);
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(5);
        druidDataSource.setCreateScheduler(scheduledExecutorService);

        // 让连接池初始化
        DruidPooledConnection connection1;
        try {
            connection1 = druidDataSource.getConnection();
            Assert.assertNotNull(connection1);
        } catch (SQLException e) {
            Assert.fail();
        }

        // 模拟数据库宕机，然后获取两次连接让连接池的failContinuous变成true
        server.stop();
        Field field = DruidAbstractDataSource.class.getDeclaredField("failContinuous");
        field.setAccessible(true);

        while (!((AtomicBoolean) field.get(druidDataSource)).get()) {
            try {
                druidDataSource.getConnection();
                Assert.fail();
            } catch (SQLException e) {
            }
        }
        // 下面这行请断点
        // 这里断点放过前 把另外两个运行CreateConnectionTask的线程停在for循环入口
        // 开启数据库，
        server.start();
        // createPhysicalConnection清掉连接池的createError
        DruidAbstractDataSource.PhysicalConnectionInfo connection2 = druidDataSource.createPhysicalConnection();
        Assert.assertFalse(connection2.getPhysicalConnection().isClosed());

        logger.error("=======================Begin to test!======================================");
        Assert.assertEquals(0, druidDataSource.getPoolingCount());
        while (true) {
            Thread.sleep(500);
            try {
                DruidPooledConnection connection = druidDataSource.getConnection(1000);
                druidDataSource.logStats();
                Assert.fail();
            } catch (SQLException e) {
                logger.error("Bug reappeared again!", e);
            }
        }
    }
}
