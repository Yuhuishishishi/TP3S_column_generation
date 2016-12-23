package facility;

import algorithm.Algorithm;
import algorithm.Column;
import algorithm.pricer.CPOPricerFacility;
import algorithm.pricer.PricerFacility;
import data.DataInstance;
import gurobi.*;
import utils.Global;

import java.util.*;
import java.util.stream.Collectors;

import static algorithm.ColumnGeneration.enumInitCol;

/**
 * Created by yuhuishi on 11/11/2016.
 * University of Michigan
 * Academic use only
 * Lagrangian relaxation + subgradient method
 */
public class LagRelax implements Algorithm {

    private Map<ColumnWithTiming, GRBVar> varMap;
    private Map<Integer, GRBConstr> testCoverConstrs;
    private Map<Integer, GRBConstr> vehicleCapConstrs;

    private GRBModel model;

    public LagRelax() {
        varMap = new HashMap<>();
        testCoverConstrs = new HashMap<>();
        vehicleCapConstrs = new HashMap<>();
    }

    private void updatePenalty(Map<Integer,Double> mu) {
        // update the coefficients of the penalties
        varMap.forEach((k,v)-> {
            // update the coefficients
            double dayPenalty = k.daysHasCrash().stream().mapToDouble(mu::get).sum();
            double objCoef = Global.VEHICLE_COST + k.getCost() + dayPenalty;
            try {
                v.set(GRB.DoubleAttr.Obj, objCoef);
            } catch (GRBException e) {
                e.printStackTrace();
            }
        });
    }

    private GRBModel buildModel(GRBEnv env, List<ColumnWithTiming> colList) throws GRBException {
        // build the model with current penalty setting
        GRBModel model = new GRBModel(env);

        // build constraints first

        // test cover constraints
        for (int tid : DataInstance.getInstance().getTidList()) {
            testCoverConstrs.put(tid, model.addConstr(
                    new GRBLinExpr(), GRB.GREATER_EQUAL, 1.0, "cover test " + tid));
        }

        // vehicle capacity constraints
        for (int release : DataInstance.getInstance().getVehicleReleaseList()) {
            vehicleCapConstrs.put(release, model.addConstr(
                    new GRBLinExpr(), GRB.LESS_EQUAL, DataInstance.getInstance().numVehiclesByRelease(release),
                    "vehicle capacity " + release
            ));
        }

        model.update();

        // add variables

        for (ColumnWithTiming col : colList) {
            addOneCol(model, col, GRB.CONTINUOUS);
        }

        model.update();
        return model;
    }



    private Map<ColumnWithTiming, Double> solveLagRelax(Map<Integer, Double> mu) throws GRBException {
        // solve the Lagragian relaxation with current penalty settings

        // update the penalties
        updatePenalty(mu);
        model.update();

        // initiate the column generation
        final int maxIter = 10000;
        int iterTimes = 0;


        PricerFacility pricer = new CPOPricerFacility();
        while (iterTimes++ < maxIter) {
            model.optimize();

            // get dual information
            Map<Integer, Double> testDual = new HashMap<>();
            Map<Integer, Double> vehicleDual = new HashMap<>();
            for (int tid : testCoverConstrs.keySet()) {
                GRBConstr constr = testCoverConstrs.get(tid);
                testDual.put(tid, constr.get(GRB.DoubleAttr.Pi));
            }
            for (int release : vehicleCapConstrs.keySet()) {
                GRBConstr constr = vehicleCapConstrs.get(release);
                vehicleDual.put(release, constr.get(GRB.DoubleAttr.Pi));
            }


            List<ColumnWithTiming> candidates = pricer.price(testDual, vehicleDual, mu);
            System.out.printf("Iteration: %d, Master obj: %.3f, pricing obj: %.3f, # cols: %d, ", iterTimes,
                    model.get(GRB.DoubleAttr.ObjVal),
                    pricer.getReducedCost(),
                    candidates.size());
            if (candidates.size()==0)
                break;
            // add the column to master problem
            int realColNum = 0;
            for (ColumnWithTiming col : candidates) {
                if (!varMap.keySet().contains(col)) {
                    addOneCol(model, col, GRB.CONTINUOUS);
                    realColNum++;
                }
            }
            System.out.print("# col added: " + realColNum + "\n");
            model.update();
        }

        // return the current solution
        Map<ColumnWithTiming, Double> solution = new HashMap<>();
        varMap.entrySet().stream().filter(e->{
            try {
                return e.getValue().get(GRB.DoubleAttr.X) > 0.001;
            } catch (GRBException e1) {
                e1.printStackTrace();
                return false;
            }
        }).forEach(e -> {
            try {
                solution.put(e.getKey(), e.getValue().get(GRB.DoubleAttr.X));
            } catch (GRBException e1) {
                e1.printStackTrace();
            }
        });
        return solution;
    }

    @Override
    public void solve() {
        // solve the subgradient iteration
        final int horizonStart = DataInstance.getInstance().getHorizonStart();
        final int horizonEnd = DataInstance.getInstance().getHorizonEnd();

        double[] mu = new double[horizonEnd-horizonStart]; // initial penalty on day
        double stepFactor = 1.0;
        double bestBound = Double.MAX_VALUE;
        Arrays.fill(mu, 0.0);

        final double zeroEps = 0.001;

        int iteration = 0;
        final int maxIter = 10000;

        // the initial set of columns
        // enumerate initial set of columns
        List<Column> normalColList = enumInitCol(Global.MAX_HITS);
        // transfer to subclass
        Set<ColumnWithTiming> colList = normalColList.stream()
                .map(col -> new ColumnWithTiming(col.getSeq(), col.getRelease()))
                .collect(Collectors.toSet());
        InitColDetector detector = new InitColDetector(normalColList);
        List<ColumnWithTiming> additionalCols = detector.getInitSol();
        System.out.println("additional col size: " + additionalCols.size());
        additionalCols.forEach(colList::add);

        // build the initial model
        GRBEnv env;
        try {
            env = new GRBEnv("lagrelax");


        } catch (GRBException ex) {
            ex.printStackTrace();
        }


    }

    @Override
    public long getTimeTillNow() {
        return 0;
    }

    private double twoNorm(double[] vector) {
        double result = 0;
        for (double v : vector)
            result += v*v;
        return result;
    }

    private void addOneCol(GRBModel model, ColumnWithTiming col, char vtype) throws GRBException {
        GRBColumn grbColumn = new GRBColumn();
        GRBConstr vConstr = vehicleCapConstrs.get(col.getRelease());
        grbColumn.addTerm(1, vConstr);

        col.getSeq().forEach(tid -> grbColumn.addTerm(1, testCoverConstrs.get(tid)));

        GRBVar v;
        if (vtype == GRB.BINARY)
            v = model.addVar(0, 1, col.getCost() + Global.VEHICLE_COST,
                    GRB.BINARY, grbColumn, "use col " + col.getSeq());
        else
            v = model.addVar(0, GRB.INFINITY, col.getCost() + Global.VEHICLE_COST,
                    GRB.CONTINUOUS, grbColumn, "use col " + col.getSeq());

        varMap.put(col, v);
    }

}
