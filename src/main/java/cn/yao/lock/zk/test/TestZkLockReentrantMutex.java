package cn.yao.lock.zk.test;

import cn.yao.lock.zk.common.ZkUtil;
import cn.yao.lock.zk.mutex.ZkLockReentantMutex;
import org.I0Itec.zkclient.ZkClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Description
 * @Author yaojun
 * @Date 2021-02-20
 */
public class TestZkLockReentrantMutex {
    static String lockPath = "/d-lock-reentrant5/zk-lock-reetrant-";

    static int count=0;
    static AtomicInteger ai = new AtomicInteger(0);

    public static void main(String[] args) throws Exception{
        System.out.println("---开始累加--");

        long s1 = System.currentTimeMillis();
        System.out.println();

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                ZkClient zkClient = ZkUtil.getZkClient();
                ZkLockReentantMutex lock = new ZkLockReentantMutex(lockPath, zkClient);
                try {
                    lock.lock();
                    System.out.println("====[开始执行业务逻辑]===="+Thread.currentThread().getName());
//                    Thread.sleep(new Random().nextInt(200)+100);
                    biz();
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

    private static void biz(){
        System.out.println("====[开始执行业务逻辑2]===="+Thread.currentThread().getName());
        ZkClient zkClient = ZkUtil.getZkClient();
        ZkLockReentantMutex lock = new ZkLockReentantMutex(lockPath, zkClient);
        try {
            lock.lock();
            System.out.println("====[开始执行业务逻辑]22222$$$$$$===="+Thread.currentThread().getName());
            for (int j = 0; j < 1; j++) {
                count = count+1;
                ai.incrementAndGet();
            }

//           Thread.sleep(new Random().nextInt(1000)+100);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
}
