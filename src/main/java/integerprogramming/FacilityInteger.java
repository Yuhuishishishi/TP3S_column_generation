package integerprogramming;

import algorithm.Algorithm;

/**
 * Created by yuhuishi on 12/27/2016.
 * University of Michigan
 * Academic use only
 */
public class FacilityInteger implements Algorithm {
    private long initTime;

    public FacilityInteger() {
        this.initTime = System.currentTimeMillis();
    }

    @Override
    public void solve() {

    }

    @Override
    public long getTimeTillNow() {
        return System.currentTimeMillis()-initTime;
    }
}
