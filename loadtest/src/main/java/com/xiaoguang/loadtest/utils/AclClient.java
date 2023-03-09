package com.xiaoguang.loadtest.utils;

import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.remoting.RPCHook;


public class AclClient {

    public static final String ACL_ACCESS_KEY = "rocketmq2";

    public static final String ACL_SECRET_KEY = "12345678";
    public static final String  INSTANCE_ID   = "XXX";

    public static RPCHook getAclRPCHook() {
        return getAclRPCHook(ACL_ACCESS_KEY, ACL_SECRET_KEY);
    }

    public static RPCHook getAclRPCHook(String ak, String sk) {
        return new SofaMQAclRPCHook(new SessionCredentials(ak, sk), INSTANCE_ID);
    }

}
