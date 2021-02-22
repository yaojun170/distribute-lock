package cn.yao.lock.zk.mutex;

import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.checkerframework.checker.units.qual.C;

import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * @Description 分布式锁-基于zookeeper实现-读写锁
 * 目标：读写锁是同一时刻可以允许多个读操作访问，但是在写操作访问时，所有的后续读操作和其他写操作均会被阻塞
 * @Author yaojun
 * @Date 2021-02-20
 */

//TODO
public class ZkLockReadWrite {
    private String lockBasePath;
    private ZkClient zkClient;
    private Thread currentThread;

    private String readNodePath;
    private String writeNodePath;

    private static final String PRE_READ = "R-";
    private static final String PRE_WRITE = "W-";

    public ZkLockReadWrite(String lockBasePath, ZkClient zkClient) {
        this.lockBasePath = lockBasePath;
        this.zkClient = zkClient;
        currentThread = Thread.currentThread();
        beforeCreateParentPath(lockBasePath);
    }

    /**
     * 1. 创建一个临时顺序节点，如果当前是读请求，那就就创建如/lock/R-的节点，如果是写请求，就创建/lock/W-的节点
     * 2. 创建完节点之后，获取父路径/lock节点下所有子节点，
     * + 如果是读请求，判断比自己小的节点是否存在写节点,如果存在写节点，进入阻塞等待，并监听此节点，如果没有写节点，则获取锁成功，开始执行业务逻辑
     * + 如果是写请求，判断当前是否是最小节点，如果是获取锁成功，如果不是，进入阻塞等待，监听上一个比自己小的节点
     */
    public void lockRead() throws Exception{
        // 获取读锁
        // 创建临时顺序节点，节点以R-开头
        // 创建完成后，获取lockBasePath下所有节点，排序
        // 获取该读锁的前一个写锁，如果不存在，则获取锁，如果存在，进行监听此写节点删除事件，监听到节点删除，唤醒线程获取到锁

        String readNodePrePath = lockBasePath + "/" + PRE_READ;
        readNodePath = zkClient.createEphemeralSequential(readNodePrePath, "r");
        System.out.println("**创建节点R**"+readNodePath+",thread:"+currentThread.getName());

        String beforeWriteNode = getReadNodeBeforeWriteNode(readNodePath);//前一个写节点
        if (beforeWriteNode==null) {
            // 前面没有写节点，可以获取锁
            System.out.println("---获取[读]锁成功--"+currentThread.getName());
        }else{
            // 前面有写节点，监听此节点删除事件
            String beforeWriteNodePath = lockBasePath+"/"+beforeWriteNode;
            waitForLock(beforeWriteNodePath);
        }

    }

    public void lockWrite() throws Exception{
        String writeNodePrePath = lockBasePath + "/" + PRE_WRITE;
        writeNodePath = zkClient.createEphemeralSequential(writeNodePrePath, "w");
        System.out.println("**创建节点W**"+writeNodePath+",thread:"+currentThread.getName());

        //获取前一个节点
        String beforeNode = getBeforeNode(writeNodePath);
        if (beforeNode == null) {
            System.out.println("---获取[写]锁成功--"+currentThread.getName());
        }else{
            //监听前一个节点删除事件
            String beforeWriteNodePath = lockBasePath+"/"+beforeNode;
            waitForLock(beforeWriteNodePath);
        }
    }

    private void waitForLock(String toWatchNodePath) throws Exception{
        CountDownLatch cdl = new CountDownLatch(1);

        IZkDataListener listener = new IZkDataListener() {
            @Override
            public void handleDataChange(String dataPath, Object data) throws Exception {
            }

            @Override
            public void handleDataDeleted(String dataPath) throws Exception {
                System.out.println("---节点被删除---" + dataPath);
                cdl.countDown();
            }
        };

        zkClient.subscribeDataChanges(toWatchNodePath, listener);


        System.out.println("---监听---"+toWatchNodePath+",thread="+currentThread.getName());
        if (zkClient.exists(toWatchNodePath)) {
            cdl.await();//阻塞，等待事件唤醒
        }

        zkClient.unsubscribeDataChanges(toWatchNodePath, listener);

        // 唤醒后，获取锁成功
        System.out.println("----监听被唤醒，获取锁成功---"+currentThread.getName());
    }

    public void unLock() {
        System.out.println("--释放锁--readPath="+readNodePath+",writePath="+writeNodePath);
        if (readNodePath != null) {
            zkClient.delete(readNodePath);
            readNodePath = null;
        }

        if (writeNodePath != null) {
            zkClient.delete(writeNodePath);
            writeNodePath = null;
        }
    }

    private String getBeforeNode(String currentNodePath) {
        String currentNodeName = currentNodePath.substring(currentNodePath.lastIndexOf("/") + 1);
        List<String> childNodes = getChildren();

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

    //获取当前节点的前一个写节点
    //反向遍历，获取到W-开头的节点即返回
    private String getReadNodeBeforeWriteNode(String readNodePath) {
        String currentNodeName = readNodePath.substring(readNodePath.lastIndexOf("/") + 1);
        List<String> childNodes = getChildren();

        // 遍历当前所有子节点，直到子节点等于当前节点，保存遍历过程中的所有写节点，返回最后一个写节点
        LinkedList<String> beforeWriteNodes = new LinkedList<>();
        boolean ifExistCurrentNode = false;
        for (String childNode : childNodes) {
            if (childNode.equals(currentNodeName)) {
                ifExistCurrentNode = true;
                break;
            }

            if (childNode.startsWith(PRE_WRITE)) {
                beforeWriteNodes.add(childNode);
            }
        }

        if (!ifExistCurrentNode) {
            return null;
        }

        return beforeWriteNodes.peekLast();
    }

    private List<String> getChildren() {
        List<String> childNodes = zkClient.getChildren(lockBasePath);
        //排序，注意：此处的排序要考虑前缀R和W
        childNodes.sort(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                int i1 = Integer.parseInt(o1.substring(o1.lastIndexOf("-")));
                int i2 = Integer.parseInt(o2.substring(o2.lastIndexOf("-")));
                return i2 - i1;
            }
        });
        return childNodes;
    }


    private void beforeCreateParentPath(String lockBasePath) {
        if (!zkClient.exists(lockBasePath)) {
            zkClient.createPersistent(lockBasePath, true);
        }
    }
}
