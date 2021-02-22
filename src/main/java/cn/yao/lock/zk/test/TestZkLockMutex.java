package cn.yao.lock.zk.test;

import cn.yao.lock.zk.common.ZkUtil;
import cn.yao.lock.zk.mutex.ZkLockMutex;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.ZkLock;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Description
 * @Author yaojun
 * @Date 2021-02-20
 */
public class TestZkLockMutex {

    static int count=0;
    static AtomicInteger ai = new AtomicInteger(0);

    public static void main(String[] args) throws Exception{

        String lockPath = "/distribute-lock/zk-lock-mutex";


        System.out.println("---开始累加--");
        System.out.println();

        long s1 = System.currentTimeMillis();

        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                ZkClient zkClient = ZkUtil.getZkClient();
                ZkLockMutex lock = new ZkLockMutex(lockPath, zkClient);
                try {
                    lock.lock();
                    System.out.println("====[开始执行业务逻辑]===="+Thread.currentThread().getName());
                    for (int j = 0; j < 10000; j++) {
                        count = count+1;
                        ai.incrementAndGet();
                    }
//                    Thread.sleep(new Random().nextInt(2000)+200);
                    System.out.println("====[完成业务,准备释放锁]===="+Thread.currentThread().getName());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }

                latch.countDown();
            }).start();
        }


        latch.await();
        System.out.println("-出结果了--");
        System.out.println("耗时："+(System.currentTimeMillis()-s1));

        Thread.sleep(1000L);
        System.out.println("COUNTX:"+count);
        System.out.println("Atomic:"+ai);
    }
}
