package algorithm.multiple;

import algorithm.Algorithm;
import algorithm.Column;
import algorithm.ColumnGeneration;
import algorithm.pricer.Pricer;
import algorithm.pricer.PricerFacility;
import algorithm.pricer.SequenceThenTimePricerFacility;
import data.DataInstance;
import facility.ColumnWithTiming;
import facility.InitColDetector;
import gurobi.*;
import utils.Global;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by yuhuishi on 1/2/2017.
 * University of Michigan
 * Academic use only
 */
public class MultipleColumnGenerationFacility implements Algorithm {

    private long initTime;
    private Map<String, Map<ColumnWithTiming, GRBVar>> varMap;              // inst id -> map
    private Map<String, Map<Integer, GRBConstr>> testCoverConstrs;          // inst id -> map
    private Map<String, Map<Integer, GRBConstr>> vehicleCapConstrs;         // inst id -> map
    private Map<Integer, GRBConstr> resourceCapConstrs;

    public MultipleColumnGenerationFacility() {
        initTime = System.currentTimeMillis();
        // init maps
        varMap = new HashMap<>();
        testCoverConstrs = new HashMap<>();
        vehicleCapConstrs = new HashMap<>();
        resourceCapConstrs = new HashMap<>();
        DataInstance.getInstIds().forEach(instID->{
            testCoverConstrs.put(instID, new HashMap<>());
            vehicleCapConstrs.put(instID, new HashMap<>());
            varMap.put(instID, new HashMap<>());
        });
    }

    @Override
    public void solve() {
        // enumerate initial set of columns
        Map<String, Set<ColumnWithTiming>> initColList = new HashMap<>();
        for (String instID : DataInstance.getInstIds()) {
            List<Column> normalCols = ColumnGeneration.enumInitCol(instID, 2);
            // two stage method to add additional columns
            InitColDetector detector = new InitColDetector(instID, normalCols);
            List<ColumnWithTiming> additionalCols = detector.getInitSol();

            // transfer to timed version

            Set<ColumnWithTiming> colSet = normalCols.stream()
                    .map(col -> new ColumnWithTiming(instID, col.getSeq(), col.getRelease())).collect(Collectors.toSet());
            additionalCols.stream().forEach(colSet::add);
            initColList.put(instID, colSet);
        }


        // build the initial model
        try {
            GRBEnv env = new GRBEnv();
            GRBModel model = buildModel(env, initColList);
            model.getEnv().set(GRB.IntParam.OutputFlag, 0);

            // =================================== column generation loop ==========================================
            final int maxIter = 10000;
            int iterTimes = 0;

            // initialize the pricer
            Map<String, PricerFacility> instPricers = new HashMap<>();
            DataInstance.getInstIds().forEach(instID -> instPricers.put(instID, new SequenceThenTimePricerFacility(instID)));
            while (iterTimes++ < maxIter) {
                boolean noNewCols = true;
                model.optimize();


                System.out.printf("Iteration: %d, Master obj: %.3f \n", iterTimes,
                        model.get(GRB.DoubleAttr.ObjVal));

                Map<Integer, Double> dayDual = new HashMap<>();
                // get dual information
                for (int d : resourceCapConstrs.keySet()) {
                    GRBConstr constr = resourceCapConstrs.get(d);
                    dayDual.put(d, constr.get(GRB.DoubleAttr.Pi));
                }

                for (String instID : DataInstance.getInstIds()) {
                    Map<Integer, Double> testDual, vehicleDual;
                    testDual = new HashMap<>();
                    vehicleDual = new HashMap<>();
                    for (int tid : testCoverConstrs.get(instID).keySet()) {
                        GRBConstr constr = testCoverConstrs.get(instID).get(tid);
                        testDual.put(tid, constr.get(GRB.DoubleAttr.Pi));
                    }
                    for (int release : vehicleCapConstrs.get(instID).keySet()) {
                        GRBConstr constr = vehicleCapConstrs.get(instID).get(release);
                        vehicleDual.put(release, constr.get(GRB.DoubleAttr.Pi));
                    }

                    // pricing for the single program
                    PricerFacility pricer = instPricers.get(instID);
                    List<ColumnWithTiming> candidates = pricer.price(testDual, vehicleDual, dayDual);

                    // add the columns
                    for (ColumnWithTiming candidate : candidates) {
                        addOneCol(model, candidate, instID);
                    }
                    model.update();

                    if (candidates.size()>0)
                        noNewCols=false;

                    System.out.printf("inst id: %s pricing, added %d cols, pricing obj: %.3f\n",
                            instID, candidates.size(), pricer.getReducedCost());
                }
                if (noNewCols)
                    break;

                //
            }


        } catch (GRBException e) {
            e.printStackTrace();
        }


        // parse the solution

    }

    @Override
    public long getTimeTillNow() {
        return System.currentTimeMillis()-initTime;
    }

    private GRBModel buildModel(GRBEnv env, Map<String, Set<ColumnWithTiming>> initCols) throws GRBException {
        GRBModel model = new GRBModel(env);

        final int horizonStart = DataInstance.getHorizonStartGlobal();
        final int horizonEnd = DataInstance.getHorizonEndGlobal();

        // build constraints for each instID
        for (String instID : initCols.keySet()) {
            // test cover constraints
            for (int tid : DataInstance.getInstance(instID).getTidList()) {
                testCoverConstrs.get(instID).put(tid, model.addConstr(
                        new GRBLinExpr(), GRB.GREATER_EQUAL, 1.0, "cover test " + tid));
            }

            // vehicle capacity constraints
            for (int release : DataInstance.getInstance(instID).getVehicleReleaseList()) {
                vehicleCapConstrs.get(instID).put(release, model.addConstr(
                        new GRBLinExpr(), GRB.LESS_EQUAL, DataInstance.getInstance(instID).numVehiclesByRelease(release),
                        "vehicle capacity " + release
                ));
            }
        }

        // day resource capacity constraints
        for (int d = horizonStart; d < horizonEnd; d++) {
            resourceCapConstrs.put(d, model.addConstr(
                    new GRBLinExpr(), GRB.LESS_EQUAL, Global.FACILITY_CAP,
                    "facility capacity " + d
            ));
        }

        model.update();
        model.addVar(0,1,1,GRB.CONTINUOUS,"test");

        // add variables
        for (String instID : initCols.keySet()) {
            Set<ColumnWithTiming> colList = initCols.get(instID);
            for (ColumnWithTiming columnWithTiming : colList) {
                addOneCol(model, columnWithTiming, instID);
            }
        }

        model.update();
        return model;
    }

    private void addOneCol(GRBModel model, ColumnWithTiming columnWithTiming, String instID) throws GRBException {
        GRBColumn grbColumn = new GRBColumn();
        GRBConstr vConstr = vehicleCapConstrs.get(instID).get(columnWithTiming.getRelease());
        assert vConstr!=null;
        grbColumn.addTerm(1, vConstr);

        columnWithTiming.getSeq().forEach(tid -> {
            GRBConstr constr = testCoverConstrs.get(instID).get(tid);
            assert constr!=null;
            grbColumn.addTerm(1, constr);});
        columnWithTiming.daysHasCrash().forEach(d -> {
            GRBConstr constr = resourceCapConstrs.get(d);
            assert constr!=null;
            grbColumn.addTerm(1, constr); });
        GRBVar variable = model.addVar(0, GRB.INFINITY, columnWithTiming.getCost() + Global.VEHICLE_COST,
                GRB.CONTINUOUS, grbColumn, "x");

        varMap.get(instID).put(columnWithTiming, variable);
    }

}