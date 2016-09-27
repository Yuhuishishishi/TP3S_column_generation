package algorithm.pricer;

import data.DataInstance;
import facility.ColumnWithTiming;
import gurobi.*;
import utils.Global;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

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
        final int numTest = DataInstance.getInstance().getTestArr().size();
        final int numVehicle = DataInstance.getInstance().getVehicleReleaseList().size();
        final int[] vehicleRelease = DataInstance.getInstance().getVehicleReleaseList()
                .stream().mapToInt(Integer::intValue).toArray();
        final int[] tidArr = DataInstance.getInstance().getTidList()
                .stream().mapToInt(Integer::intValue).toArray();

        // ======================================== variables ========================================================
        // decision variables
        GRBVar[][] slotStartAtTime = new GRBVar[numSlots][horizonLength];
        GRBVar[][] assignTestToSlot = new GRBVar[numTest][numSlots];
        GRBVar[][] testStartAtTime = new GRBVar[numTest][horizonLength]; // aux variables
        GRBVar[] selectVehicle;

        // initialization
        for (int p = 0; p < numSlots; p++) {
            slotStartAtTime[p] = m.addVars(horizonLength, GRB.BINARY);
        }
        for (int t = 0; t < numTest; t++) {
            assignTestToSlot[t] = m.addVars(numSlots, GRB.BINARY);
            testStartAtTime[t] = m.addVars(horizonLength, GRB.BINARY);
        }
        selectVehicle = m.addVars(numVehicle, GRB.BINARY);
        m.update();

        // ======================================== constraints ======================================================
        // at most one vehicle
        GRBLinExpr oneVehicle = new GRBLinExpr();
        oneVehicle.addTerms(IntStream.range(0,numVehicle).mapToDouble(e->1).toArray(), selectVehicle);
        m.addConstr(oneVehicle, GRB.EQUAL, 1, "select one vehicle");

        // test start after vehicle release
        for (int v = 0; v < numVehicle; v++) {
            int release = vehicleRelease[v];
            for (int p = 0; p < numSlots; p++) {
                for (int e = 0; e < release; e++) {
                    GRBLinExpr expr = new GRBLinExpr();
                    expr.addTerms(new double[]{1,1},
                            new GRBVar[]{slotStartAtTime[p][e], selectVehicle[v]});
                    m.addConstr(expr, GRB.LESS_EQUAL, 1, "vehicle release for " + v);
                }
            }
        }

        // test start after its own release
        // do not start too late
        for (int t = 0; t < numTest; t++) {
            int release = DataInstance.getInstance().getTestById(tidArr[t]).getRelease();
            int prep = DataInstance.getInstance().getTestById(tidArr[t]).getPrep();
            int dur = DataInstance.getInstance().getTestById(tidArr[t]).getDur();

            int earlisetStartTime = Math.max(0, release-prep);
            for (int e = 0; e < earlisetStartTime; e++) {
                m.addConstr(testStartAtTime[t][e], GRB.LESS_EQUAL, 1, "test release " + t);
            }
            int latestStartTime = Math.max(0, horizonLength-dur);
            for (int e = latestStartTime; e < horizonLength; e++) {
                m.addConstr(testStartAtTime[t][e], GRB.LESS_EQUAL, 1, "test not too late " + t);
            }
        }

        // use early slots first // symmetry breaking
        for (int p = 0; p < numSlots-1; p++) {
            GRBLinExpr slotUsedp = new GRBLinExpr();
            GRBLinExpr slotUsedpplusone = new GRBLinExpr();
            int finalP = p;
            slotUsedp.addTerms(IntStream.range(0,numTest).mapToDouble(e->1.0).toArray(),
                    (GRBVar[]) IntStream.range(0,numTest).mapToObj(t->assignTestToSlot[t][finalP]).toArray());
            slotUsedpplusone.addTerms(IntStream.range(0,numTest).mapToDouble(e->1.0).toArray(),
                    (GRBVar[]) IntStream.range(0,numTest).mapToObj(t->assignTestToSlot[t][finalP+1]).toArray());
            m.addConstr(slotUsedpplusone, GRB.LESS_EQUAL, slotUsedp, "symmetry breaking " + p);
        }

        // test start time linking
        for (int t = 0; t < numTest; t++) {
            for (int e = 0; e < horizonLength; e++) {
                for (int p = 0; p < numSlots; p++) {
                    GRBLinExpr expr = new GRBLinExpr();
                    expr.addConstant(-1);
                    expr.addTerms(new double[]{1,1},
                            new GRBVar[]{assignTestToSlot[t][p], slotStartAtTime[p][e]});
                    m.addConstr(testStartAtTime[t][e], GRB.GREATER_EQUAL, expr, "time linking " + t + "\t" + e);
                }
            }
        }

        // no overlap
        for (int p = 0; p < numSlots - 1; p++) {

        }

        // compatibility



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
