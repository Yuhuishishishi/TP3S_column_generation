package facility;

import algorithm.Algorithm;
import algorithm.Column;
import algorithm.pricer.PricerFacility;
import algorithm.pricer.SequenceThenTimePricerFacility;
import data.DataInstance;
import gurobi.*;
import utils.Global;

import java.util.*;
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

    private long timeInit;

    public ColumnGenerationFacility() {
        varMap = new HashMap<>();
        resourceCapConstrs = new HashMap<>();
        testCoverConstrs = new HashMap<>();
        vehicleCapConstrs = new HashMap<>();

        timeInit = System.currentTimeMillis();
    }


    @Override
    public void solve() {
        System.out.println("Enumerating initial set of columns ... ");
        // enumerate initial set of columns
        List<Column> normalColList = enumInitCol(2);
        // transfer to subclass
        List<ColumnWithTiming> colList = normalColList.stream()
                .map(col -> new ColumnWithTiming(col.getSeq(), col.getRelease()))
                .collect(Collectors.toList());


        Set<ColumnWithTiming> uniqColSet = new HashSet<>(colList);
//        colList.clear();
//        colList.addAll(uniqColSet);
        assert colList.size()==uniqColSet.size();
        System.out.println("unique col size: " + uniqColSet.size());

        System.out.println("Solve for initial set of columns ... ");
        // get the intial solutions
        InitColDetector detector = new InitColDetector(normalColList);
        List<ColumnWithTiming> additionalCols = detector.getInitSol();
        System.out.println("additonal col size: " + additionalCols.size());
        additionalCols.forEach(columnWithTiming -> {
            if (uniqColSet.add(columnWithTiming))
                colList.add(columnWithTiming);
        });

        final int initColSize = colList.size();

        // compute the horizon length
        OptionalInt horizonEnd = colList.stream().flatMap(col -> col.daysHasCrash().stream())
                .mapToInt(d -> d).max();
        assert horizonEnd.isPresent();
        if (horizonEnd.getAsInt() > DataInstance.getInstance().getHorizonEnd()) {
            System.out.println("Updating horzion end from " + DataInstance.getInstance().getHorizonEnd()
            + " to " + horizonEnd);
            DataInstance.getInstance().setHorizonEnd((int) (horizonEnd.getAsInt() + 1));
        }

        try {
            GRBEnv env = new GRBEnv();
            GRBModel model = buildModel(env, colList);
            // suppress the log
            model.getEnv().set(GRB.IntParam.OutputFlag, 0);

//            // change the variables type
//            for (GRBVar var : varMap.values()) {
//                var.set(GRB.CharAttr.VType, GRB.CONTINUOUS);
//                var.set(GRB.DoubleAttr.UB, GRB.INFINITY);
//            }
            model.update();

            // ================================= Column Generation Loop ================================================

            final int maxIter = 10000;
            int iterTimes = 0;

//            PricerFacility pricer = new CPOPricerFacility();
            PricerFacility pricer = new SequenceThenTimePricerFacility();
            System.out.printf("[%d] - Starting column generation loop ... \n", getTimeTillNow());
            double relaxObjVal = Double.MAX_VALUE;

            Map<Integer, Integer> dayResourceActiveCounter = new HashMap<>();
            resourceCapConstrs.keySet().forEach(d->dayResourceActiveCounter.put(d, 0));

            Map<Integer, Double> testDual = new HashMap<>();
            Map<Integer, Double> vehicleDual = new HashMap<>();
            Map<Integer, Double> dayDual = new HashMap<>();

            while (iterTimes++ < maxIter) {
                model.optimize();

                // get dual information
                testDual.clear();
                vehicleDual.clear();
                dayDual.clear();

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
                    if (dayDual.get(d) < -0.01) {
                        int count = dayResourceActiveCounter.get(d);
                        dayResourceActiveCounter.put(d, ++count);
                    }
                }

                List<ColumnWithTiming> candidates = pricer.price(testDual, vehicleDual, dayDual);
                System.out.printf("Iteration: %d, Master obj: %.3f, pricing obj: %.3f, # cols: %d, ", iterTimes,
                        model.get(GRB.DoubleAttr.ObjVal),
                        pricer.getReducedCost(),
                        candidates.size());
                relaxObjVal = model.get(GRB.DoubleAttr.ObjVal);
                if (candidates.size()==0)
                    break;
                // add the column to master problem
                int realColNum = 0;
                for (ColumnWithTiming col : candidates) {
                    assert col.isValid();
                    if (uniqColSet.add(col)) {
                        addOneCol(model, col, GRB.CONTINUOUS);
                        colList.add(col);
                        realColNum++;
                    }

                    Column colWithoutTime = new Column(col.getSeq(), col.getRelease());
//                    System.out.println("cost 1 "  + colWithoutTime.getCost() + " cost 2: " + col.getCost());
//                    for (int tid : col.getSeq()) {
//                        System.out.println("\nstart " + col.getStartTimeByTid(tid));
//                        System.out.println(DataInstance.getInstance().getTestById(tid).getRelease());
//                    }
//
//                    double[][] value = SequenceThenTimePricerFacility.optimalTimeFinding(col.getSeq(), dayDual);
//                    SequenceThenTimePricerFacility.backTractStartTime(col.getSeq(), vehicleDual, dayDual, value);
                    assert  colWithoutTime.getCost()<=col.getCost();
                }
                System.out.print("# col added: " + realColNum + "\n");
                model.update();

            }
            System.out.println();
            System.out.println("Relaxation obj val: " + relaxObjVal);
            System.out.println("Number of iterations: " + iterTimes);
            System.out.println("Number of columns generated: " + (colList.size()-initColSize));


            List<ColumnWithTiming> heuristicSol = ((SequenceThenTimePricerFacility) pricer).primalHeuristic(
                    testDual, vehicleDual, dayDual
            );

            pricer.end();

            assert colList.size()==varMap.size();

            // add the heuristic solution to the model
            for (ColumnWithTiming col : heuristicSol) {
                GRBVar var;

                Column colwithoutTime = new Column(col.getSeq(), col.getRelease());
                assert colwithoutTime.getCost() <= col.getCost();
                if (uniqColSet.add(col)) {
                    var = addOneCol(model, col, GRB.BINARY);
                    colList.add(col);
                }
                else
                    var = varMap.get(col);
                var.set(GRB.DoubleAttr.Start, 1.0);
            }


            // solve the integer version
            for (GRBVar var : varMap.values()) {
                var.set(GRB.CharAttr.VType, GRB.BINARY);
            }

            // generate other timed versions of columns
            SequenceThenTimePricerFacility pricerFacility = (SequenceThenTimePricerFacility) pricer;

            List<ColumnWithTiming> newColsToAdd = new ArrayList<>();
            colList.forEach(col->newColsToAdd.addAll(pricerFacility.createMultipleVehicleVersion(col, dayDual)));
            int newColCounter = 0;
            for (ColumnWithTiming columnWithTiming : newColsToAdd) {

                if (!columnWithTiming.isValid()) {
//                    System.out.println("Detect invalid columns");
                    continue;
                }

                if (uniqColSet.add(columnWithTiming)) {
                    addOneCol(model, columnWithTiming, GRB.BINARY);
                    colList.add(columnWithTiming);
                    newColCounter++;
                }
            }
            System.out.println(newColCounter + " more columns added");
//            List<ColumnWithTiming> additonalTimedCols = new ArrayList<>();
//            colList.forEach(col->{
//                List<ColumnWithTiming> colsToAdd = CPOPricerFacility.createMultipleVersion(col);
//                colsToAdd.stream().filter(uniqColSet::add)
//                        .forEach(additonalTimedCols::add);
//            });
            // add to problem
//            additonalTimedCols.forEach(col -> {
//                try {
//                    addOneCol(model, col, GRB.BINARY);
//                } catch (GRBException e) {
//                    e.printStackTrace();
//                }
//            });

            // set all capacity constraints as lazy
            resourceCapConstrs.values().forEach(constr -> {
                try {
                    constr.set(GRB.IntAttr.Lazy, 3);
                } catch (GRBException e) {
                    e.printStackTrace();
                }
            });
//
////             set all inactive one as lazy
            dayResourceActiveCounter.entrySet().stream().filter(e->e.getValue()<=0.1)
                    .map(e->resourceCapConstrs.get(e.getKey()))
                    .forEach(constr -> {
                        try {
                            constr.set(GRB.IntAttr.Lazy, 1);
                        } catch (GRBException e) {
                            e.printStackTrace();
                        }
                    });


            model.update();

            model.getEnv().set(GRB.IntParam.OutputFlag, 1);


//            model.getEnv().set(GRB.DoubleParam.TuneTimeLimit, 12000);
//            model.tune();
//            model.write("C:\\Users\\yuhuishi\\Desktop\\projects\\TP3S_column_generation\\tuned.prm");

            System.out.printf("[%d] - Solving last iteration integer formulation ... \n", getTimeTillNow());
            model.update();
//            model.getEnv().set(GRB.DoubleParam.MIPGap, 0.01); // optimality termination gap
            model.getEnv().set(GRB.DoubleParam.TimeLimit, 900); // time limit
            model.getEnv().set(GRB.IntParam.MIPFocus, 1); // finding feasible solution first
            model.getEnv().set(GRB.IntParam.Presolve, 2); // more aggresive presolve
            model.getEnv().set(GRB.DoubleParam.Heuristics, 0.1); // more heuristic investment

            model.optimize();

            if (model.get(GRB.IntAttr.Status) == GRB.OPTIMAL
                    || model.get(GRB.IntAttr.Status)==GRB.TIME_LIMIT) {
                List<ColumnWithTiming> usedCols = parseSol(new ArrayList<>(varMap.keySet()));

                // check test covering
                Map<Integer, Boolean> testCovered = new HashMap<>();
                DataInstance.getInstance().getTidList().forEach(tid->testCovered.put(tid, false));
                usedCols.stream().flatMap(col->col.getSeq().stream()).forEach(tid->testCovered.put(tid, true));
                assert !testCovered.values().contains(false);

                System.out.println("Max tardiness: " + usedCols.stream().mapToDouble(ColumnWithTiming::getCost).sum());
                double tardiness = model.get(GRB.DoubleAttr.ObjVal) - usedCols.size()*Global.VEHICLE_COST;
                System.out.println("Used vehicles: " + usedCols.size());
                System.out.println("Tardiness: " + tardiness);
                System.out.println("Obj val: " + model.get(GRB.DoubleAttr.ObjVal));

                System.out.println("Opt gap: " + model.get(GRB.DoubleAttr.MIPGap));

                // count the max activities
                Map<Integer, Integer> counter = new HashMap<>();
                dayResourceActiveCounter.keySet().forEach(d->counter.put(d, 0));
                usedCols.stream().forEach(col->col.daysHasCrash().forEach(d->{
                    int count = counter.get(d);
                    counter.put(d, ++count);
                }));
                OptionalInt maxAct = counter.values().stream().mapToInt(e -> e).max();
                assert maxAct.isPresent();
                try {
                    assert maxAct.getAsInt() <= Global.FACILITY_CAP;
                } catch (AssertionError err) {
                    System.out.println("maxAct = " + maxAct);
                }
            }

            System.out.println("Done. Total time spend : " + getTimeTillNow());

            model.dispose();
            env.dispose();
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getTimeTillNow() {
        return (System.currentTimeMillis() - timeInit)/1000;
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
            addOneCol(model, col, GRB.CONTINUOUS);
        }

        model.update();
        return model;
    }

    private GRBVar addOneCol(GRBModel model, ColumnWithTiming col, char vtype) throws GRBException {
        GRBColumn grbColumn = new GRBColumn();
        GRBConstr vConstr = vehicleCapConstrs.get(col.getRelease());
        grbColumn.addTerm(1, vConstr);

        col.getSeq().forEach(tid -> grbColumn.addTerm(1, testCoverConstrs.get(tid)));
        col.daysHasCrash().forEach(d -> grbColumn.addTerm(1, resourceCapConstrs.get(d)));
        GRBVar v;
        if (vtype == GRB.BINARY)
            v = model.addVar(0, 1, col.getCost() + Global.VEHICLE_COST,
                    GRB.BINARY, grbColumn, null);
        else
            v = model.addVar(0, GRB.INFINITY, col.getCost() + Global.VEHICLE_COST,
                    GRB.CONTINUOUS, grbColumn, null);

        varMap.put(col, v);
        return v;
    }


}
