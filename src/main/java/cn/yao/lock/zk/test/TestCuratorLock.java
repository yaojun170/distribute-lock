package cn.yao.lock.zk.test;

import cn.yao.lock.zk.common.ZkUtil;
import cn.yao.lock.zk.mutex.ZkLockMutex;
import org.I0Itec.zkclient.ZkClient;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Description
 * @Author yaojun
 * @Date 2021-02-21
 */
public class TestCuratorLock {
    private String lockPath = "/d-lock-curator/lock";
    private int threadCount = 10;
    private int count = 0;
    private AtomicInteger ai = new AtomicInteger(0);

    @Test
    public void test1() throws Exception{
        //测试非阻塞锁

        System.out.println("---start....---");
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                InterProcessMutex lock = getLock();
                try {
                    lock.acquire();
                    System.out.println("====[开始执行业务逻辑]===="+Thread.currentThread().getName());
                    count = count+1;
                    ai.incrementAndGet();
                    /*for (int j = 0; j < 10000; j++) {

                    }*/
                    System.out.println("====[完成业务,准备释放锁]===="+Thread.currentThread().getName());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        lock.release();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }


        Thread.sleep(10000L);
        System.out.println("-出结果了--");
        System.out.println("COUNTX:"+count);
        System.out.println("Atomic:"+ai);
    }

    private CuratorFramework getCuratorClient(){
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000,3);
        CuratorFramework client = CuratorFrameworkFactory.newClient(ZkUtil.ZKCONNECT,retryPolicy);
        client.start();
        return client;
    }

    private InterProcessMutex getLock(){
        CuratorFramework client = getCuratorClient();

        InterProcessMutex interProcessMutex = new InterProcessMutex(client, lockPath);
        return interProcessMutex;
    }

}
