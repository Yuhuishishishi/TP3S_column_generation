package algorithm.multiple;

import algorithm.Algorithm;
import algorithm.Column;
import algorithm.ColumnGeneration;
import algorithm.pricer.PricerFacility;
import algorithm.pricer.SequenceThenTimePricerFacility;
import data.DataInstance;
import facility.ColumnWithTiming;
import facility.WarmupAlgorithm;
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

            // transfer to timed version
            Set<ColumnWithTiming> colSet = normalCols.stream()
                    .map(col -> new ColumnWithTiming(instID, col.getSeq(), col.getRelease())).collect(Collectors.toSet());
            initColList.put(instID, colSet);
        }
        // add additional columns
        WarmupAlgorithm detector = new MultipleInitColDetector();
        List<ColumnWithTiming> additionalCols = detector.getInitSol();
        System.out.println("Additional cols size: " + additionalCols.size());
        additionalCols.forEach(col-> initColList.get(col.getInstID()).add(col));

        final int initColSize = initColList.values().stream().mapToInt(Set::size).sum();

        // build the initial model
        double relaxObjVal = 0;
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

            Map<Integer, Integer> dayActiveCounter = new HashMap<>();
            resourceCapConstrs.keySet().forEach(d->dayActiveCounter.put(d, 0));

            while (iterTimes++ < maxIter) {
                boolean noNewCols = true;
                model.optimize();

                assert model.get(GRB.IntAttr.Status)==GRB.OPTIMAL;

                relaxObjVal = model.get(GRB.DoubleAttr.ObjVal);
                System.out.printf("Iteration: %d, Master obj: %.3f \n", iterTimes,
                        model.get(GRB.DoubleAttr.ObjVal));

                Map<Integer, Double> dayDual = new HashMap<>();
                // get dual information
                for (int d : resourceCapConstrs.keySet()) {
                    GRBConstr constr = resourceCapConstrs.get(d);
                    dayDual.put(d, constr.get(GRB.DoubleAttr.Pi));

                    if (dayDual.get(d) < -0.01) {
                        int activeCount = dayActiveCounter.get(d);
                        dayActiveCounter.put(d, ++activeCount);
                    }
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
                        if (initColList.get(instID).add(candidate))
                            addOneCol(model, candidate, instID);
                    }

                    if (candidates.size()>0)
                        noNewCols=false;

                    System.out.printf("inst id: %s pricing, added %d cols, pricing obj: %.3f\n",
                            instID, candidates.size(), pricer.getReducedCost());
                }

                model.update();

                if (noNewCols)
                    break;//
            }

            // do the last pricing
            Map<Integer, Double> dayDual = new HashMap<>();

            for (int d : resourceCapConstrs.keySet()) {
                GRBConstr constr = resourceCapConstrs.get(d);
                dayDual.put(d, constr.get(GRB.DoubleAttr.Pi));
            }
            for (String instID : DataInstance.getInstIds()) {
//                Map<Integer, Double> testDual, vehicleDual;
//                testDual = new HashMap<>();
//                vehicleDual = new HashMap<>();
//                for (int tid : testCoverConstrs.get(instID).keySet()) {
//                    GRBConstr constr = testCoverConstrs.get(instID).get(tid);
//                    testDual.put(tid, constr.get(GRB.DoubleAttr.Pi));
//                }
//                for (int release : vehicleCapConstrs.get(instID).keySet()) {
//                    GRBConstr constr = vehicleCapConstrs.get(instID).get(release);
//                    vehicleDual.put(release, constr.get(GRB.DoubleAttr.Pi));
//                }
//
//                // pricing for the single program
                SequenceThenTimePricerFacility pricer = (SequenceThenTimePricerFacility) instPricers.get(instID);
//                List<ColumnWithTiming> candidates = pricer.price(testDual, vehicleDual, dayDual);
//                for (ColumnWithTiming candidate : candidates) {
//                    if (initColList.get(instID).add(candidate))
//                        addOneCol(model, candidate, instID);
//                }

                Set<ColumnWithTiming> colList = initColList.get(instID);
                List<ColumnWithTiming> newColsToAdd = new ArrayList<>();
                colList.forEach(col->newColsToAdd.addAll(pricer.createMultipleVehicleVersion(col, dayDual)));
                for (ColumnWithTiming columnWithTiming : newColsToAdd) {
                    if (colList.add(columnWithTiming))
                        addOneCol(model, columnWithTiming, instID);
                }
            }


            System.out.println();
            System.out.println("Relaxation obj val: " + relaxObjVal);
            System.out.println("Number of iterations: " + iterTimes);
            System.out.println("Number of columns generated: " + (varMap.values().stream()
                    .mapToInt(Map::size).sum()-initColSize));

            // end of column generation loop

            // solve the last iteration integer programming problem
//            List<ColumnWithTiming> allCols = new ArrayList<>();
//            initColList.values().forEach(allCols::addAll);
//            LastIterationSolverCP lastIterationSolverCP = new
//                    LastIterationSolverCP(allCols);
//            lastIterationSolverCP.solve();



            // covert day capacity to lazy
            // set all capacity constraints as lazy
            resourceCapConstrs.values().forEach(constr -> {
                try {
                    constr.set(GRB.IntAttr.Lazy, 3);
                } catch (GRBException e) {
                    e.printStackTrace();
                }
            });

//             set all inactive one as lazy
            dayActiveCounter.entrySet().stream().filter(e->e.getValue()<=0.1)
                    .map(e->resourceCapConstrs.get(e.getKey()))
                    .forEach(constr -> {
                        try {
                            constr.set(GRB.IntAttr.Lazy, 1);
                        } catch (GRBException e) {
                            e.printStackTrace();
                        }
                    });


            // solve the integer version
            for (Map<ColumnWithTiming, GRBVar> columnWithTimingGRBVarMap : varMap.values()) {
                for (GRBVar grbVar : columnWithTimingGRBVarMap.values()) {
                    grbVar.set(GRB.CharAttr.VType, GRB.BINARY);
                }
            }

            model.getEnv().set(GRB.IntParam.OutputFlag, 1);
            model.getEnv().set(GRB.DoubleParam.TimeLimit, 600); // time limit 10 mins
            model.optimize();


            if (model.get(GRB.IntAttr.Status) == GRB.OPTIMAL
                    || model.get(GRB.IntAttr.Status)==GRB.TIME_LIMIT) {

            for (String instID : DataInstance.getInstIds()) {
                System.out.println("Inst ID: " + instID + " stats: ");
                    // parse used cols
                Set<ColumnWithTiming> instColSet = initColList.get(instID);
                List<ColumnWithTiming> usedCols = new ArrayList<>();

                for (ColumnWithTiming columnWithTiming : instColSet) {
                    if (varMap.get(instID).get(columnWithTiming).get(GRB.DoubleAttr.X) > 0.5)
                        usedCols.add(columnWithTiming);
                }
                    System.out.println("Max tardiness: " + usedCols.stream().mapToDouble(ColumnWithTiming::getCost).sum());
                    double tardiness = usedCols.stream().mapToDouble(ColumnWithTiming::getCost).sum();
                    System.out.println("Used vehicles: " + usedCols.size());
                    System.out.println("Tardiness: " + tardiness);

                }

                System.out.println("Obj val: " + model.get(GRB.DoubleAttr.ObjVal));
                System.out.println("Opt gap: " + model.get(GRB.DoubleAttr.MIPGap));
                System.out.println("Done. Total time spend : " + getTimeTillNow());
            }


        } catch (GRBException e) {
            e.printStackTrace();
        }
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
                        new GRBLinExpr(), GRB.GREATER_EQUAL, 1.0, null));
            }

            // vehicle capacity constraints
            for (int release : DataInstance.getInstance(instID).getVehicleReleaseList()) {
                vehicleCapConstrs.get(instID).put(release, model.addConstr(
                        new GRBLinExpr(), GRB.LESS_EQUAL, DataInstance.getInstance(instID).numVehiclesByRelease(release),
                        null
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
