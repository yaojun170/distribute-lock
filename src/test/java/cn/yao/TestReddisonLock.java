package cn.yao;

import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TestReddisonLock {
    public static int left = 10000;
    public static void main(String[] args) {
        ExecutorService es = Executors.newFixedThreadPool(100);
        Config config = new Config();
        config.setLockWatchdogTimeout(1000);
        config.useSingleServer().setAddress("redis://43.142.81.153:6379").setPassword("123456").setConnectionMinimumIdleSize(2);
        RedissonClient redissonClient = Redisson.create(config);
        RLock lock = redissonClient.getLock("lockkey101");
        for (int i = 0; i < 100; i++) {
            es.submit(new MyTask(lock));
        }

        System.out.println("--end--:"+left);
    }

    static class MyTask implements Runnable{
        private RLock lock;
        private MyTask(RLock lock){
            this.lock = lock;
        }
        @Override
        public void run() {
            System.out.println(Thread.currentThread().getName()+":in....");
            lock.lock();
            System.out.println(Thread.currentThread().getName()+":get Lock---->");
            try{
                for (int i = 0; i < 100; i++) {
                    left--;
                }
                System.out.println(Thread.currentThread().getName()+":"+left);
            }finally {
                lock.unlock();
            }
        }
    }
}
