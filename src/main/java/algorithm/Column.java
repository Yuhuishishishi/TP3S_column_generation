package algorithm;

import data.DataInstance;
import data.TestRequest;

import java.util.List;

/**
 * Created by yuhui on 8/24/2016.
 * University of Michigan
 * Academic use only
 */
public class Column {

    protected double cost;
    protected final List<Integer> seq;
    protected final int release;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Column column = (Column) o;

        if (release != column.release) return false;
        return seq != null ? sameSeq(seq, column.seq) : column.seq == null;

    }

    private boolean sameSeq(List<Integer> seq1, List<Integer> seq2) {
        if (seq1.size() != seq2.size())
            return false;

        for (int i = 0; i < seq1.size(); i++) {
            if (seq1.get(i).intValue()!=seq2.get(i).intValue())
                return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = seq != null ? seq.hashCode() : 0;
        result = 31 * result + release;
        return result;
    }
}
