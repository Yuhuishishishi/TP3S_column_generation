package algorithm;

import data.DataInstance;
import data.TestRequest;
import data.Vehicle;

import java.util.List;

/**
 * Created by yuhui on 8/24/2016.
 */
public class Column {

    protected double cost;
    protected List<Integer> seq;
    protected int release;

    public Column(List<Integer> seq, int release) {
        this.seq = seq;
        this.release = release;
        this.cost = this.calacCost();
    }


    private double calacCost() {
        int release = this.release;

        double cost = 0;
        for (int tid : this.seq) {
            TestRequest test = DataInstance.getInstance().getTestById(tid);
            if (release + test.getPrep() < test.getRelease()) {
                release = test.getRelease() + test.getTat() + test.getAnalysis();
            } else {
                release += test.getDur();
            }

            cost += Math.max(0, release-test.getDeadline());
        }

        return cost;
    }

    public double getCost() {
        return cost;
    }

    public List<Integer> getSeq() {
        return seq;
    }

    public int getRelease() {
        return release;
    }
}
