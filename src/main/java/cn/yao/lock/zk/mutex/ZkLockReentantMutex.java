package cn.yao.lock.zk.mutex;

import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * @Description 分布式锁-基于zookeeper实现
 * 可重入锁，并且是没有羊群效应
 * <p>
 * 实现原理：
 * 可重入锁需要维护每次锁的线程信息，每当有获取锁请求时先判断请求线程是不是已经拿到了锁
 * 如果已经拿到了锁直接返回获取锁成功，否则进入阻塞等待状态
 * <p>
 * 实现步骤：
 * 前提：需要全局保存获取到锁的线程Thread和重入的次数reetrantCount
 * 获取锁：
 * 1. 请求获取锁，先判断当前reetrantCount是否等于0，如果为0，表示没有线程获取锁，则去抢占锁
 * 2. 如果大于0，则表示已经有锁存在，则判断拿到锁的线程是不是当前请求的线程，如果是当前请求线程则直接获取锁，reentrantCount+1
 * 3. 如果第2步判断拿到锁的线程不是当前线程，则当前线程进入阻塞等待
 * <p>
 * 释放锁：
 * 1. 获取到当前线程是不是拿到锁的线程，如果是reentrantCount--
 * 2. 再判断reentrantCount是否为0，如果是，删除锁对应的节点
 * @Author yaojun
 * @Date 2021-02-20
 */
public class ZkLockReentantMutex implements DistributeLock {
    private String lockPath;
    private String parentPath;
    private ZkClient zkClient;
    private String currentNodeFullPath;
    private String beforeNode;//上一个节点
    private Thread currentThread;

    private static volatile Thread threadOfLock; //获取到锁的线程
    private static volatile int reetrantCount = 0; //线程获取锁的次数

    public ZkLockReentantMutex(String lockPath, ZkClient zkClient) {
        this.lockPath = lockPath;
        String parentPathTmp = lockPath.substring(0, lockPath.lastIndexOf("/"));
        if (parentPathTmp == null || parentPathTmp == "") {
            this.parentPath = "/";
        } else {
            this.parentPath = parentPathTmp;
        }
        this.zkClient = zkClient;
        currentThread = Thread.currentThread();
        beforeCreateParentPath(lockPath);
    }

    @Override
    public void lock() throws Exception {
        if (reetrantCount == 0) {//没有线程获取到锁
            // 进入抢锁
            if (tryLock()) {
                threadOfLock = currentThread;
                reetrantCount++;
                System.out.println("===第一次拿到锁线程=" + threadOfLock.getName());
            } else {
                System.out.println("####wait[1]等待释放锁" + currentThread.getName());
                waitForLock();
                lock();
            }
        } else {
            //已经有线程拿到锁，判断是不是等于当前线程
            if (currentThread == threadOfLock) {
                //避免再次创建线程
                reetrantCount++;
                System.out.println("$$$再次拿到锁-------");
            } else {
                // 当前线程不是获取锁的线程，理论是进入等待
                if (tryLock()) {
                    System.out.println("===新线程抢锁成功=="+currentThread.getName());
                    threadOfLock = currentThread;
                    reetrantCount++;
                } else {
                    System.out.println("####wait[2]等待释放锁" + currentThread.getName());
                    waitForLock();
                    lock();
                }
            }
        }
    }

    private boolean tryLock() {
        if (currentNodeFullPath == null) {
            currentNodeFullPath = zkClient.createEphemeralSequential(lockPath, "k2");
            System.out.println("###创建节点"+currentNodeFullPath+",thread:"+currentThread.getName());
        }
        List<String> childNodes = zkClient.getChildren(parentPath);
        Collections.sort(childNodes);
        beforeNode = getLastNode(childNodes, currentNodeFullPath);//找到比自己小的节点

        if (beforeNode == null) {
            return true;
        } else {
            return false;
        }
    }

    private void waitForLock() throws Exception {
        //监听上一个节点
        CountDownLatch cdl = new CountDownLatch(1);

        String beforeNodePath = parentPath + "/" + beforeNode;

        IZkDataListener listener = new IZkDataListener() {
            @Override
            public void handleDataChange(String dataPath, Object data) throws Exception {
            }

            @Override
            public void handleDataDeleted(String dataPath) throws Exception {
                System.out.println("#########节点被删除#### path:" + dataPath);
                cdl.countDown();
            }
        };

        boolean exists = zkClient.exists(beforeNodePath);
        if (exists) {
            zkClient.subscribeDataChanges(beforeNodePath, listener);

            System.out.println("===监听上一个节点==path:" + beforeNodePath + ", thread:" + currentThread.getName());
            cdl.await();

            zkClient.unsubscribeDataChanges(beforeNodePath, listener);
        }
    }

    @Override
    public void unlock() {
        try {
            if (currentThread == threadOfLock) {
                reetrantCount--;
                System.out.println("=====释放锁-----减1");
                if (reetrantCount <= 0) {
                    System.out.println("^^^^开始删除锁节点, path=" + currentNodeFullPath);
                    zkClient.delete(currentNodeFullPath);
                }
            }
        } catch (Exception e) {
            System.err.println("删除节点失败,err:" + e.getMessage());
        }
    }

    @Override
    public void releaseResource() {
        if (zkClient != null) {
            zkClient.close();
        }
    }

    private void beforeCreateParentPath(String lockPath) {
        if (!parentPath.equals("/") && !zkClient.exists(parentPath)) {
            zkClient.createPersistent(parentPath, true);
        }
    }

    private String getLastNode(List<String> childNodes, String currentNodePath) {
        String currentNodeName = currentNodePath.substring(currentNodePath.lastIndexOf("/") + 1);
        String lastNode = null;
        for (String childNode : childNodes) {
            if (childNode.equals(currentNodeName)) {
                return lastNode;
            } else {
                lastNode = childNode;
            }
        }

        return lastNode;
    }


}
