package integerprogramming;

import algorithm.Algorithm;

/**
 * Created by yuhuishi on 12/27/2016.
 * University of Michigan
 * Academic use only
 */
public class GeneralInteger implements Algorithm {
    public static final double IP_TIME_LIMIT = 600;


    private long initTime;

    public GeneralInteger(long initTime) {
        this.initTime = initTime;
    }

    @Override
    public void solve() {

    }

    @Override
    public long getTimeTillNow() {
        return System.currentTimeMillis()-initTime;
    }
}
