package branchandprice;

import algorithm.Column;

/**
 * Created by yuhuishi on 12/18/2016.
 * University of Michigan
 * Academic use only
 */
public abstract class BranchConstraint {

    public enum CONSTRAINT_DIRECTION {
        ENFORCE, FORBID
    }

    protected CONSTRAINT_DIRECTION direction;

    protected BranchConstraint(CONSTRAINT_DIRECTION direction) {
        this.direction = direction;
    }

    public abstract boolean isColFixToZero(Column col);
}
