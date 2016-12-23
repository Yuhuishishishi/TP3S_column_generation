package facility;

import algorithm.Algorithm;
import algorithm.Column;

import java.util.List;

/**
 * Created by yuhuishi on 12/14/2016.
 * University of Michigan
 * Academic use only
 */
public class LastIterationSolverCP implements Algorithm {

    private List<Column> colPool;

    private final long timeInit;

    public LastIterationSolverCP(List<Column> colPool) {
        this.colPool = colPool;
        timeInit = System.currentTimeMillis();
    }

    @Override
    public void solve() {
        // solve using CP algorithm

    }

    @Override
    public long getTimeTillNow() {
        return (System.currentTimeMillis()-timeInit);
    }
}
