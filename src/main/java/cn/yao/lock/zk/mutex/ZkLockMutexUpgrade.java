package cn.yao.lock.zk.mutex;

import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Description 分布式锁-基于zookeeper实现
 * 在ZkLockMutex的基础上优化升级，解决其羊群效应
 * <p>
 * 实现原理：
 * 获取锁时也是创建临时节点，区别是创建的临时顺序节点，每一个客户端在争夺锁时都会由zk分配一个顺序号(sequence)，客户端按照这个顺序去获取锁
 * <p>
 * 具体流程：
 * 获取锁：
 * （1）客户端尝试获取锁，客户端创建临时顺序节点/distribute-lock/lock2-，返回/distribute-lock2/lock-0000000001
 * （2）拿到sequence，获取节点/distribute-lock2下所有子节点，并排序
 * （3）如果没有比自己小的节点，则获取锁成功
 * （4）如果存在比自己小的节点，则监听上一个比自己小的节点的删除事件，监听期间，线程等待，一旦监听到上一个节点被删除，线程被唤醒，再次尝试获取锁
 * <p>
 * 释放锁：
 * 同其他逻辑一样，删除节点
 * @Author yaojun
 * @Date 2021-02-20
 */
public class ZkLockMutexUpgrade implements DistributeLock {
    private String lockPath;
    private ZkClient zkClient;
    private String currentNodeFullPath;
    private Thread currentThread;
    private String parentPath;

    public ZkLockMutexUpgrade(String lockPath, ZkClient zkClient) {
        this.lockPath = lockPath;
        this.zkClient = zkClient;
        currentThread = Thread.currentThread();

        firstCreateParentPath(lockPath);
    }

    @Override
    public void lock() throws Exception {
        System.out.println("++++开始抢占锁++++" + currentThread.getName());
        try {

            //1. 创建临时顺序节点
            //2. 获取当前节点的所有兄弟节点(也就是父节点下所有子节点)
            //3. 所有子节点进行排序，然后判断当前节点是否最小
            //4. 如果当前节点是最小节点，则表示获取锁成功
            //5. 如果当前节点不是最小节点，则监听watcher上一个比自己小的节点，监听期间阻塞等待，一但监听到的节点被删除，则唤醒当前线程再次获取锁
            currentNodeFullPath = zkClient.createEphemeralSequential(lockPath, "k");
            System.out.println("********线程" + currentThread.getName() + "创建子节点：" + currentNodeFullPath);
            doLock();
        } catch (Exception e) {
            //创建节点失败，阻塞等待
            e.printStackTrace();
            System.err.println(e.getMessage() + ",THREAD:" + currentThread.getName() + ",path:" + lockPath);
        }
    }

    private void doLock() throws Exception {
        String threadName = Thread.currentThread().getName();

        String parentPath = lockPath.substring(0, lockPath.lastIndexOf("/"));
        List<String> childNodes = zkClient.getChildren(parentPath);
        Collections.sort(childNodes);

        String lastNode = getLastNode(childNodes, currentNodeFullPath);//找到比自己小的节点

        if (null == lastNode) {
            System.out.println("--获取锁成功--thread:" + threadName + "，节点：" + currentNodeFullPath);
        } else {
            waitForLock(lastNode);
            //可以不用再次走获取锁流程，因为是直接已经获取锁
//            doLock();
        }
    }

    private void waitForLock(String lastNode) throws Exception{
        //监听上一个节点
        CountDownLatch cdl = new CountDownLatch(1);

        String lastNodeFullPath = parentPath + "/" + lastNode;

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

        boolean exists = zkClient.exists(lastNodeFullPath);
        if (exists) {
            //bug：出现问题是在上一步查询节点还存在，但是快速业务执行完，节点被删除，还没有来得及加监听，导致一直阻塞
            //bug: 可能在下面添加监听的过程中，节点被删除了
            zkClient.subscribeDataChanges(lastNodeFullPath, listener);

            System.out.println("===监听上一个节点==path:" + lastNodeFullPath + ", thread:" + currentThread.getName()+",exist??"+zkClient.exists(lastNodeFullPath));

            if(zkClient.exists(lastNodeFullPath)){//再次检查节点的目的是防止死等已经删除的节点
                cdl.await();
            }

            zkClient.unsubscribeDataChanges(lastNodeFullPath, listener);
        }
    }

    @Override
    public void unlock() {
        try {
            System.out.println("^^^^开始释放锁, path=" + currentNodeFullPath);
            zkClient.delete(currentNodeFullPath);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("删除节点失败,err:" + e.getMessage());
        }
    }

    @Override
    public void releaseResource() {
        if (zkClient != null) {
            zkClient.close();
        }
    }

    private void firstCreateParentPath(String lockPath) {
        parentPath = lockPath.substring(0, lockPath.lastIndexOf("/"));

        if (!parentPath.equals("/")) {
            boolean exists = zkClient.exists(parentPath);
            if (!exists) {
                zkClient.createPersistent(parentPath, true);
            }
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
