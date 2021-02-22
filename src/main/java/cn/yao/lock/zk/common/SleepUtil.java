package cn.yao.lock.zk.common;

/**
 * @Description
 * @Author yaojun
 * @Date 2021-02-22
 */
public class SleepUtil {
    public static void sleep(long millSeconds){
        try {
            Thread.sleep(millSeconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
