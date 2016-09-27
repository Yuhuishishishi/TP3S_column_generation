package algorithm.pricer;

import data.DataInstance;
import facility.ColumnWithTiming;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBModel;
import utils.Global;

import java.util.List;
import java.util.Map;

/**
 * Created by yuhuishi on 9/26/2016.
 * University of Michigan
 * Academic use only
 */
public class MIPPricerFacility implements PricerFacility {

    private double reducedCost;
    private GRBEnv env;
    private GRBModel model;

    public MIPPricerFacility() {
        this.reducedCost = Double.MAX_VALUE;

    }

    private GRBModel buildModel() throws GRBException {
        this.env = new GRBEnv();
        GRBModel m =  new GRBModel(this.env);

        // ======================================== parameters =======================================================
        final int slack = 50;
        final int horizonLength = DataInstance.getInstance().getHorizonStart()
                - DataInstance.getInstance().getHorizonEnd() + slack;
        final int numSlots = Global.MAX_HITS;

        // ======================================== variables ========================================================
        // decision variables


        // ======================================== constraints ======================================================




        return m;
    }

    @Override
    public List<ColumnWithTiming> price(Map<Integer, Double> testDual,
                                        Map<Integer, Double> vehicleDual, Map<Integer, Double> dayDual) {
        return null;
    }

    @Override
    public double getReducedCost() {
        return this.reducedCost;
    }

    @Override
    public void end() {
        this.model.dispose();
        try {
            this.env.dispose();
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }
}
