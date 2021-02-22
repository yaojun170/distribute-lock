package cn.yao.lock.zk.mutex;

/**
 * @Description
 * @Author yaojun
 * @Date 2021-02-20
 */
public interface DistributeLock {
    void lock() throws Exception;
    void unlock();

    //释放此分布式锁所需要的资源，比如zk连接
    void releaseResource();
}
