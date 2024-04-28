package m2.proxy.admin;

import m2.proxy.executors.ServiceBaseExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class AccessController extends ServiceBaseExecutor {
    private static final Logger log = LoggerFactory.getLogger(AccessController.class);

    //public static long MAX_ID=99999999999L;


    @Override
    protected boolean open() {
        return true;
    }

    @Override
    protected void startServices() {}

    @Override
    protected void execute() {
        long waitTime = 1000L;
        while(isRunning()) {
            log.info("check access controller");
            // clean up invalid access
            waitfor(waitTime);
        }
    }
    @Override
    protected void close() {}
    @Override
    protected void forceClose() {}

}
