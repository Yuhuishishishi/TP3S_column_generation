package branchandprice;

import algorithm.Column;

/**
 * Created by yuhuishi on 12/18/2016.
 * University of Michigan
 * Academic use only
 */
public class ConstraintTestTogetherOnVehicle extends BranchConstraint {

    private int tid1;
    private int tid2;
    private int vrelease;

    public ConstraintTestTogetherOnVehicle(CONSTRAINT_DIRECTION direction, int tid1, int tid2, int vrelease) {
        super(direction);
        this.tid1 = tid1;
        this.tid2 = tid2;
        this.vrelease = vrelease;
    }

    @Override
    public String toString() {
        return "ConstraintTestTogetherOnVehicle{" +
                "tid1=" + tid1 +
                ", tid2=" + tid2 +
                ", vrelease=" + vrelease +
                '}';
    }

    public boolean isColFixToZero(Column col) {
        if (direction==CONSTRAINT_DIRECTION.ENFORCE) {
            // fix to zero if
            // (1) contain exactly one test
            // (2) contain 2 tests but order is wrong
            // (3) contain 2 ordered tests, but vehicle is wrong
            if (col.getSeq().contains(tid1) && !col.getSeq().contains(tid2))
                return true;
            if (col.getSeq().contains(tid2) && !col.getSeq().contains(tid1))
                return true;
            if (col.getSeq().indexOf(tid1) > col.getSeq().indexOf(tid2))
                return true;
            if (col.getRelease()!=vrelease)
                return true;
        } else {
            // fix to zero if
            // (1) contain the ordered tests, and correct vehicle
            if (col.getSeq().contains(tid1) && col.getSeq().contains(tid2)
                    && col.getSeq().indexOf(tid1) < col.getSeq().indexOf(tid2)
                    && col.getRelease()==vrelease)
                return true;
        }


        return false;
    }
}
