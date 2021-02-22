package cn.yao.lock.zk.common;

import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkMarshallingError;
import org.I0Itec.zkclient.serialize.ZkSerializer;

import java.io.UnsupportedEncodingException;

/**
 * @Description
 * @Author yaojun
 * @Date 2021-02-20
 */
public class ZkUtil {
    public static final String ZKCONNECT="10.162.176.202:2181,10.162.176.203:2181,10.162.176.126:2181";

    public static ZkClient getZkClient(){
        ZkClient zkClient = new ZkClient(ZKCONNECT, 3000, 2000, new ZkSerializer() {
            @Override
            public byte[] serialize(Object obj) throws ZkMarshallingError {
                try {
                    return String.valueOf(obj).getBytes("UTF-8");
                } catch (final UnsupportedEncodingException e) {
                    throw new ZkMarshallingError(e);
                }
            }

            @Override
            public Object deserialize(byte[] bytes) throws ZkMarshallingError {
                try {
                    return new String(bytes, "utf-8");
                } catch (final UnsupportedEncodingException e) {
                    throw new ZkMarshallingError(e);
                }
            }
        });

        return zkClient;
    }

    public static void closeZkConn(ZkClient zkClient){
        if (zkClient != null) {
            zkClient.close();
        }
    }
}
