package cn.yao.lock.zk.test;

import cn.yao.lock.zk.common.SleepUtil;
import cn.yao.lock.zk.common.ZkUtil;
import cn.yao.lock.zk.mutex.ZkLockMutexUpgrade;
import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.junit.Test;

/**
 * @Description
 * @Author yaojun
 * @Date 2021-02-20
 */
public class TestOther {
    @Test
    public void test1() throws Exception{
        String lockPath = "/distribute-lock3/zk-lock-mutexUpdate-";
        ZkClient zkClient = ZkUtil.getZkClient();
        ZkLockMutexUpgrade lock = new ZkLockMutexUpgrade(lockPath, zkClient);
        lock.lock();
        System.out.println("---执行业务---");
        lock.unlock();
    }

    @Test
    public void test2(){
        ZkClient zkClient = ZkUtil.getZkClient();
        String path = "/d-lock3/zk-lock-mutexUpdate-0000000004";
        boolean exists = zkClient.exists(path);
        System.out.println("节点存在?："+exists);
        zkClient.subscribeDataChanges(path, new IZkDataListener() {
            @Override
            public void handleDataChange(String dataPath, Object data) throws Exception {
                System.out.println("节点修改,"+dataPath);
            }

            @Override
            public void handleDataDeleted(String dataPath) throws Exception {
                System.out.println("节点被删除:"+dataPath);
            }
        });

        System.out.println("--监听节点--"+path);
        SleepUtil.sleep(100000L);
    }

}
