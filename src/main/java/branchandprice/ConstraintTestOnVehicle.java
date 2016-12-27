package branchandprice;

import algorithm.Column;

/**
 * Created by yuhuishi on 12/18/2016.
 * University of Michigan
 * Academic use only
 */
public class ConstraintTestOnVehicle extends BranchConstraint {
    // if a test has to be assigned to a vehicle

    private int tid;
    private int vrelease;

    public ConstraintTestOnVehicle(CONSTRAINT_DIRECTION direction, int tid, int vrelease) {
        super(direction);
        this.tid = tid;
        this.vrelease = vrelease;
    }

    @Override
    public String toString() {
        return "ConstraintTestOnVehicle{" +
                "tid=" + tid +
                ", vrelease=" + vrelease +
                '}';
    }


    public boolean isColFixToZero(Column col) {
        if (direction==CONSTRAINT_DIRECTION.ENFORCE) {
            // fix to zero if test is assigned to another vehicle
            if (col.getSeq().contains(tid) && col.getRelease()!=vrelease)
                return true;
        } else {
            // fix to zero if test is assigned to the vehicle
            if (col.getSeq().contains(tid) && col.getRelease()==vrelease)
                return true;
        }

        return false;
    }
}
