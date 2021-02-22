package cn.yao.lock.zk.test;

import cn.yao.lock.zk.common.SleepUtil;
import cn.yao.lock.zk.common.ZkUtil;
import cn.yao.lock.zk.mutex.ZkLockReadWrite;
import org.I0Itec.zkclient.ZkClient;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

/**
 * @Description 测试读写锁
 * @Author yaojun
 * @Date 2021-02-22
 */
public class TestZkLockReadWrite {

    private String lockBasePath = "/d-rw-lock5";

    @Test
    public void testWriteOnly() throws Exception{
        String lockBasePath = "/d-rw-lock";
        ZkClient zkClient = ZkUtil.getZkClient();
        ZkLockReadWrite lock = new ZkLockReadWrite(lockBasePath, zkClient);
        lock.lockWrite();
        for (int i = 1; i <= 5; i++) {
            Thread.sleep(1000);
            System.out.println(i);
        }

        lock.unLock();
    }

    @Test
    public void test3() throws Exception{
        CountDownLatch cdl = new CountDownLatch(2);
        System.out.println("--main启动--");
        new Thread(new ReadThread(cdl)).start();
        new Thread(new WriteThread(cdl)).start();

        cdl.await();
        SleepUtil.sleep(1000L);
    }

    @Test
    public void testReadWrite() throws Exception{
        CountDownLatch cdl = new CountDownLatch(10);
        System.out.println("--main启动--");
        new Thread(new ReadThread(cdl)).start();
        new Thread(new ReadThread(cdl)).start();
        new Thread(new ReadThread(cdl)).start();
        new Thread(new ReadThread(cdl)).start();
        new Thread(new ReadThread(cdl)).start();

        new Thread(new WriteThread(cdl)).start();
        new Thread(new WriteThread(cdl)).start();
        new Thread(new WriteThread(cdl)).start();
        new Thread(new WriteThread(cdl)).start();
        new Thread(new WriteThread(cdl)).start();

        cdl.await();
        SleepUtil.sleep(1000L);
    }

    class ReadThread implements Runnable{
        private CountDownLatch cdl;
        public ReadThread(CountDownLatch cdl){
            this.cdl = cdl;
        }

        @Override
        public void run() {
            Thread currentThread = Thread.currentThread();
            System.out.println("--[read]线程启动--"+currentThread.getName());

            ZkClient zkClientOfRead = ZkUtil.getZkClient();
            ZkLockReadWrite lockRead = new ZkLockReadWrite(lockBasePath, zkClientOfRead);

            try {
                lockRead.lockRead();
            } catch (Exception e) {
                e.printStackTrace();
            }

            for (int i = 0; i < 5; i++) {
                System.out.println("###["+currentThread.getName()+"=====读："+i);
                SleepUtil.sleep(300);
            }

            lockRead.unLock();
            cdl.countDown();
        }
    }

    class WriteThread implements Runnable{
        private CountDownLatch cdl;
        public WriteThread(CountDownLatch cdl){
            this.cdl = cdl;
        }
        @Override
        public void run() {
            Thread currentThread = Thread.currentThread();
            System.out.println("--[write]线程启动--"+currentThread.getName());

            ZkClient zkClientOfWrite = ZkUtil.getZkClient();
            ZkLockReadWrite lockWrite = new ZkLockReadWrite(lockBasePath, zkClientOfWrite);

            try {
                lockWrite.lockWrite();
            } catch (Exception e) {
                e.printStackTrace();
            }

            for (int i = 0; i < 5; i++) {
                System.out.println("###["+currentThread.getName()+"=====写："+i);
                SleepUtil.sleep(300);
            }

            lockWrite.unLock();
            cdl.countDown();
        }
    }
}
