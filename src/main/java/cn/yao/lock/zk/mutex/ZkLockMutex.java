package cn.yao.lock.zk.mutex;

import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;

import java.util.concurrent.CountDownLatch;

/**
 * @Description 分布式锁-基于zookeeper实现
 * 实现原理：利用zk的创建节点不能重名的特性
 * 如何避免死锁：创建的是临时节点，当服务宕机会话关闭临时节点将会被删除，锁自动释放
 * 实现步骤：
 * （1）尝试获取锁，尝试创建临时节点，zk会保证只有一个客户端创建成功
 * （2）如果创建节点成功则获取锁成功，继续执行业务逻辑
 * （3）如果创建节点失败，节点已经被其他客户端创建，则获取锁失败，监听该节点的删除事件，这期间线程出于阻塞等待状态
 * （4）第2步的线程执行完业务逻辑，释放锁，即删除节点，监听该节点的线程会收到通知，退出等待，可以再次尝试获取锁，递归第1步，递归：获取锁的过程是一个递归的操作，获取锁->监听->获取锁
 *
 * 缺点：
 *  （1）不可重入
 *  （2）有羊群效应
 *  （3）非公平锁
 * @Author yaojun
 * @Date 2021-02-20
 */
public class ZkLockMutex implements DistributeLock{
    private String lockPath;
    private ZkClient zkClient;

    public ZkLockMutex(String lockPath, ZkClient zkClient){
        this.lockPath = lockPath;
        this.zkClient = zkClient;
    }

    private void createParentPath(ZkClient zkClient, String lockPath){
        String parentPath = lockPath.substring(0, lockPath.lastIndexOf("/"));
        if(!parentPath.equals("/")){
            boolean exists = zkClient.exists(parentPath);
            if (!exists) {
                zkClient.createPersistent(parentPath,true);
            }
        }
    }

    @Override
    public void lock() throws Exception{
        String threadName = Thread.currentThread().getName();
        System.out.println("++++开始抢占锁:"+threadName);
        try {
            //创建临时节点
            createParentPath(zkClient,lockPath);

            zkClient.createEphemeral(lockPath,"lock");
            //创建临时节点成功，表示已获取锁
            System.out.println("--获取锁成功--thread:"+threadName);
        } catch (Exception e) {
            //创建节点失败，阻塞等待
            System.err.println(e.getMessage()+",THREAD:"+threadName);
            CountDownLatch cdl = new CountDownLatch(1);

            IZkDataListener listener = new IZkDataListener() {
                @Override
                public void handleDataChange(String dataPath, Object data) throws Exception {
                }

                @Override
                public void handleDataDeleted(String dataPath) throws Exception {
                    //监听到节点被删除，唤醒线程
                    cdl.countDown();
                }
            };

            if (zkClient.exists(lockPath)) {
                zkClient.subscribeDataChanges(lockPath, listener);//监听节点

                cdl.await();//阻塞，直到监听到节点被删除，监听逻辑中唤醒
                zkClient.unsubscribeDataChanges(lockPath, listener);
            }

            lock();//再次尝试获取锁
        }
    }

    @Override
    public void unlock() {
        try {
            zkClient.delete(lockPath);
        } catch (Exception e) {
            System.err.println("删除节点失败,err:"+e.getMessage());
        }
    }

    @Override
    public void releaseResource() {
        if (zkClient != null) {
            zkClient.close();
        }
    }
}
