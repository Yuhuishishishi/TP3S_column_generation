package facility;

import algorithm.Algorithm;
import algorithm.Column;
import algorithm.pricer.CPOPricerFacility;
import algorithm.pricer.PricerFacility;
import data.DataInstance;
import gurobi.*;
import utils.Global;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static algorithm.ColumnGeneration.enumInitCol;

/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
@SuppressWarnings("ALL")
public class ColumnGenerationFacility implements Algorithm {

    private Map<ColumnWithTiming, GRBVar> varMap;
    private Map<Integer, GRBConstr> resourceCapConstrs;
    private Map<Integer, GRBConstr> testCoverConstrs;
    private Map<Integer, GRBConstr> vehicleCapConstrs;

    public ColumnGenerationFacility() {
        varMap = new HashMap<>();
        resourceCapConstrs = new HashMap<>();
        testCoverConstrs = new HashMap<>();
        vehicleCapConstrs = new HashMap<>();
    }


    @Override
    public void solve() {
        // enumerate initial set of columns
        List<Column> normalColList = enumInitCol(Global.MAX_HITS);
        // transfer to subclass
        List<ColumnWithTiming> colList = normalColList.stream()
                .map(col -> new ColumnWithTiming(col.getSeq(), col.getRelease()))
                .collect(Collectors.toList());

        try {
            GRBEnv env = new GRBEnv();
            GRBModel model = buildModel(env, colList);
            // suppress the log
            model.getEnv().set(GRB.IntParam.OutputFlag, 0);

            // change the variables type
            for (GRBVar var : varMap.values()) {
                var.set(GRB.CharAttr.VType, GRB.CONTINUOUS);
                var.set(GRB.DoubleAttr.UB, GRB.INFINITY);
            }
            model.update();

            // ================================= Column Generation Loop ================================================

            final int maxIter = 10000;
            int iterTimes = 0;

            PricerFacility pricer = new CPOPricerFacility();
            while (iterTimes++ < maxIter) {
                model.optimize();

                // get dual information
                Map<Integer, Double> testDual = new HashMap<>();
                Map<Integer, Double> vehicleDual = new HashMap<>();
                Map<Integer, Double> dayDual = new HashMap<>();
                for (int tid : testCoverConstrs.keySet()) {
                    GRBConstr constr = testCoverConstrs.get(tid);
                    testDual.put(tid, constr.get(GRB.DoubleAttr.Pi));
                }
                for (int release : vehicleCapConstrs.keySet()) {
                    GRBConstr constr = vehicleCapConstrs.get(release);
                    vehicleDual.put(release, constr.get(GRB.DoubleAttr.Pi));
                }
                for (int d : resourceCapConstrs.keySet()) {
                    GRBConstr constr = resourceCapConstrs.get(d);
                    dayDual.put(d, constr.get(GRB.DoubleAttr.Pi));
                }

                List<ColumnWithTiming> candidates = pricer.price(testDual, vehicleDual, dayDual);
                System.out.printf("Iteration: %d, Master obj: %.3f, pricing obj: %.3f\n", iterTimes,
                        model.get(GRB.DoubleAttr.ObjVal),
                        pricer.getReducedCost());
                if (candidates.size()==0)
                    break;
                // add the column to master problem
                for (ColumnWithTiming col : candidates) {
                    addOneCol(model, col, GRB.CONTINUOUS);
                    colList.add(col);
                }
                model.update();
            }

            // solve the integer version
            for (GRBVar var : varMap.values()) {
                var.set(GRB.CharAttr.VType, GRB.BINARY);
            }
            model.update();
            model.optimize();

            if (model.get(GRB.IntAttr.Status) == GRB.OPTIMAL) {
                List<ColumnWithTiming> usedCols = parseSol(colList);
                System.out.println("max tardiness: " + usedCols.stream().mapToDouble(ColumnWithTiming::getCost).sum());
                double tardiness = model.get(GRB.DoubleAttr.ObjVal) - usedCols.size()*Global.VEHICLE_COST;
                System.out.println("Used vehicles: " + usedCols.size());
                System.out.println("Tardiness: " + tardiness);
                System.out.println("Obj val: " + model.get(GRB.DoubleAttr.ObjVal));
            }
        } catch (GRBException e) {
            e.printStackTrace();
        }

    }

    private List<ColumnWithTiming> parseSol(List<ColumnWithTiming> colList) throws GRBException {
        List<ColumnWithTiming> result = new ArrayList<>();
        for (Map.Entry<ColumnWithTiming, GRBVar> entry : this.varMap.entrySet()) {
            if (entry.getValue().get(GRB.DoubleAttr.X) > 0.5)
                result.add(entry.getKey());
        }
        return result;
    }

    private GRBModel buildModel(GRBEnv env, List<ColumnWithTiming> colList) throws GRBException {
        GRBModel model = new GRBModel(env);

        final int horizonStart = DataInstance.getInstance().getHorizonStart();
        final int horizonEnd = DataInstance.getInstance().getHorizonEnd();

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

        // day resource capacity constraints
        for (int d = horizonStart; d < horizonEnd; d++) {
            resourceCapConstrs.put(d, model.addConstr(
                    new GRBLinExpr(), GRB.LESS_EQUAL, Global.FACILITY_CAP,
                    "facility capacity " + d
            ));
        }

        model.update();

        // add variables

        for (ColumnWithTiming col : colList) {
            addOneCol(model, col, GRB.BINARY);
        }

        model.update();
        return model;
    }

    private void addOneCol(GRBModel model, ColumnWithTiming col, char vtype) throws GRBException {
        GRBColumn grbColumn = new GRBColumn();
        GRBConstr vConstr = vehicleCapConstrs.get(col.getRelease());
        grbColumn.addTerm(1, vConstr);

        col.getSeq().forEach(tid -> grbColumn.addTerm(1, testCoverConstrs.get(tid)));
        col.daysHasCrash().forEach(d -> grbColumn.addTerm(1, resourceCapConstrs.get(d)));
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
