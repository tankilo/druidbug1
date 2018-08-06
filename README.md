https://github.com/alibaba/druid/issues/2741

# 使用步骤
下面的断点都是仅stop当前线程
1. 在server.start();那行断点，让程序运行到该行
2. 在com.alibaba.druid.pool.DruidDataSource.CreateConnectionTask的for循环入口断点，让两个线程停住
3. 在Begin to test!那行断点，放过1中的断点继续执行
4. 等到执行到3中的断点后，放过2中的断点让代码继续执行

可以观察到控制台一直输出com.alibaba.druid.pool.DataSourceNotAvailableException: null，连接池已经无法创建连接
