package branchandprice;

import algorithm.Column;

/**
 * Created by yuhuishi on 12/18/2016.
 * University of Michigan
 * Academic use only
 */
public class ConstraintTestTogether extends BranchConstraint {
    // if two tests have to been assigned to the same sequence

    private int tid1;
    private int tid2;

    public ConstraintTestTogether(CONSTRAINT_DIRECTION direction, int tid1, int tid2) {
        super(direction);
        this.tid1 = tid1;
        this.tid2 = tid2;
    }

    @Override
    public String toString() {
        return "ConstraintTestTogether{" +
                "tid1=" + tid1 +
                ", tid2=" + tid2 +
                '}';
    }

    public boolean isColFixToZero(Column col) {
        if (direction==CONSTRAINT_DIRECTION.ENFORCE) {
            // fix to zero, if a column contains exactly one of the two tests
            if (col.getSeq().contains(tid1) && !col.getSeq().contains(tid2))
                return true;
            if (col.getSeq().contains(tid2) && !col.getSeq().contains(tid1))
                return true;
        } else {
            // fix to zero if two tests are contained in the same column
            if (col.getSeq().contains(tid1) && col.getSeq().contains(tid2))
                return true;
        }



        return false;
    }
}
