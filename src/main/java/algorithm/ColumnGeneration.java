package algorithm;

import algorithm.pricer.CPOPricer;
import algorithm.pricer.EnumPricer;
import algorithm.pricer.Pricer;
import data.DataInstance;
import facility.ColumnWithTiming;
import gurobi.*;
import utils.Global;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
public class ColumnGeneration implements Algorithm{


    private final Map<Column, GRBVar> varMap;
    private final Map<Integer, GRBConstr> testCoverConstrs;
    private final Map<Integer, GRBConstr> vehicleCapConstrs;

    private long timeInit;

    public ColumnGeneration() {
        testCoverConstrs = new HashMap<>();
        vehicleCapConstrs = new HashMap<>();
        varMap = new HashMap<>();

        timeInit = System.currentTimeMillis();
    }

    // enumerate initial set of columns
    public static List<Column> enumInitCol(int maxLevel) {

        // enumerate the sequence first
        List<List<Integer>> seqList = new ArrayList<>();
        final List<List<Integer>> curr_lvl = new ArrayList<>();

        int lvl = 1;
        // the single ones
        DataInstance.getInstance().getTidList()
                .forEach(tid -> curr_lvl.add(Collections.singletonList(tid)));
        seqList.addAll(curr_lvl);
        System.out.printf("Level: %d, # seq: %d\n", lvl, curr_lvl.size());

        while (lvl++ < maxLevel) {
            List<List<Integer>> nxt_lvl = new ArrayList<>();
            for (List<Integer> seq : curr_lvl) {
                DataInstance.getInstance().getTidList().stream()
                        .filter(tid -> DataInstance.isSeqCompWithTest(seq, tid)).forEach(tid -> {
                    List<Integer> newSeq = new ArrayList<>(seq);
                    newSeq.add(tid);
                    nxt_lvl.add(newSeq);
                });
            }
            if (nxt_lvl.size()==0)
                break;
            seqList.addAll(nxt_lvl);
            System.out.printf("Level: %d, # seq: %d\n", lvl, nxt_lvl.size());
            curr_lvl.clear(); curr_lvl.addAll(nxt_lvl);
        }

        System.out.printf("Total sequences: %d\n", seqList.size());
        List<Column> colList = new ArrayList<>();
        // pair with release to create columns
        seqList.forEach(
                seq -> DataInstance.getInstance().getVehicleReleaseList()
                        .forEach(release -> colList.add(new Column(seq, release)))
        );
        return colList;

    }

    @Override
    public void solve() {
        System.out.println("Enumerating initial set of columns ... ");
        List<Column> colList = enumInitCol(2);

        final int initColSize = colList.size();

        try {
            GRBEnv env = new GRBEnv();
            GRBModel model = buildModel(env, colList);
            model.getEnv().set(GRB.IntParam.OutputFlag, 0);

            // change the variables type
            for (GRBVar var : varMap.values()) {
                var.set(GRB.CharAttr.VType, GRB.CONTINUOUS);
                var.set(GRB.DoubleAttr.UB, GRB.INFINITY);
            }
            model.update();

            final int maxIter = 1000;
            int iterTimes = 0;
//            Pricer pricer = new JacopPricer();
//            Pricer pricer = new CPOPricer();
            Pricer pricer = new EnumPricer();


            System.out.printf("[%d] - Starting column generation loop ... \n", getTimeTillNow());
            double relaxObjVal = Double.MAX_VALUE;
            while (iterTimes++ < maxIter) {

                model.optimize();
                // grb dual information
                Map<Integer, Double> testDual = new HashMap<>();
                Map<Integer, Double> vehicleDual = new HashMap<>();
                for (int tid : testCoverConstrs.keySet()) {
                    testDual.put(tid,
                            testCoverConstrs.get(tid).get(GRB.DoubleAttr.Pi));
                }
                for (int release : vehicleCapConstrs.keySet()) {
                    vehicleDual.put(release,
                            vehicleCapConstrs.get(release).get(GRB.DoubleAttr.Pi));
                }

                List<Column> candidates = pricer.price(testDual, vehicleDual);
                System.out.printf("Iteration: %d, Master obj: %.3f, pricing obj: %.3f\n", iterTimes,
                        model.get(GRB.DoubleAttr.ObjVal),
                        pricer.getReducedCost());
                relaxObjVal = model.get(GRB.DoubleAttr.ObjVal);
                if (candidates.size()==0)
                    break;
                // add the column to the master problem
                for (Column col : candidates) {
                    addOneCol(model, col, GRB.CONTINUOUS);
                    colList.add(col);
                }
                model.update();
            }

            System.out.println("Relaxation obj val: " + relaxObjVal);
            System.out.println("Number of iterations: " + iterTimes);
            System.out.println("Number of columns generated: " + (colList.size()-initColSize));

            // solve the integer value version
            for (GRBVar var : varMap.values()) {
                var.set(GRB.CharAttr.VType, GRB.BINARY);
            }
            model.getEnv().set(GRB.IntParam.OutputFlag, 1);
            model.getEnv().set(GRB.DoubleParam.TimeLimit, 300); // time limit to 300 secs

            model.update();
            model.optimize();
            printStats(model, colList);

            pricer.end();

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

    public void solveFull() {
        // enumerate initial set of columns
        List<Column> colList = enumInitCol(Global.MAX_HITS);

        System.out.println("Total number of columns: " + colList.size());
        try {
            GRBEnv env = new GRBEnv();
            GRBModel model = buildModel(env, colList);
            model.getEnv().set(GRB.IntParam.OutputFlag, 1);
            model.getEnv().set(GRB.DoubleParam.TimeLimit, 600); // time limit to 600 secs


            model.optimize();

            printStats(model, colList);

        } catch (GRBException e) {
            e.printStackTrace();
        }
    }

    private void printStats(GRBModel model, List<Column> colList) throws GRBException {
        if (model.get(GRB.IntAttr.Status) == GRB.OPTIMAL
                || model.get(GRB.IntAttr.Status) == GRB.TIME_LIMIT) {
            List<Column> usedCols = parseSol(colList);
            double tardiness = model.get(GRB.DoubleAttr.ObjVal) - usedCols.size()*Global.VEHICLE_COST;
            System.out.println("Total tardiness: " + usedCols.stream().mapToDouble(Column::getCost).sum());

            System.out.println("Used vehicles: " + usedCols.size());
            System.out.println("Tardiness: " + tardiness);
            System.out.println("Obj val: " + model.get(GRB.DoubleAttr.ObjVal));

            System.out.println("Max activities on day " + countMaxActivityPerDay(usedCols));
            System.out.println("Opt gap: " + model.get(GRB.DoubleAttr.MIPGap));
        }
    }

    public static int countMaxActivityPerDay(List<Column> usedCols) {
        // count the max activities on day
        List<ColumnWithTiming> usedColTimed = usedCols.stream().map(c->new ColumnWithTiming(c.getSeq(), c.getRelease()))
                .collect(Collectors.toList());
        Map<Integer, Integer> activitiesOnDays = new HashMap<>();
        usedColTimed.forEach(c-> {
            List<Integer> daysWithActivties = c.daysHasCrash();
            for (int d : daysWithActivties) {
                if (activitiesOnDays.containsKey(d))
                    activitiesOnDays.put(
                            d,
                            activitiesOnDays.get(d)+1
                    );
                else
                    activitiesOnDays.put(d,1);
            }
        });
        // find the max
        Integer maxDay = activitiesOnDays.keySet().stream()
                .reduce((i, j) -> activitiesOnDays.get(i) > activitiesOnDays.get(j) ? i : j).orElse(-1);
        return maxDay;
    }

    private GRBModel buildModel(GRBEnv env, List<Column> colList) throws GRBException {
        GRBModel model =    new GRBModel(env);

        // build constraints first
        // test cover constraints
        for (int tid : DataInstance.getInstance().getTidList()) {
            GRBConstr constr = model.addConstr(
                            new GRBLinExpr(), GRB.GREATER_EQUAL, 1.0, "cover test " + tid);
            testCoverConstrs.put(tid, constr);
        }

        // vehicle capacity constraints
        for (int release : DataInstance.getInstance().getVehicleReleaseList()) {
            GRBConstr constr =  model.addConstr(
                    new GRBLinExpr(), GRB.LESS_EQUAL, DataInstance.getInstance().numVehiclesByRelease(release),
                    "vehicle capacity " + release);
            vehicleCapConstrs.put(release, constr);
        }

        model.update();
        // add variables

        for (Column col : colList) {
            addOneCol(model, col, GRB.BINARY);
        }

        model.update();

        return model;
    }

    private void addOneCol(GRBModel model, Column col, char type) throws GRBException {
        GRBColumn grbColumn = new GRBColumn();
        GRBConstr vConstr = vehicleCapConstrs.get(col.getRelease());
        grbColumn.addTerm(1, vConstr);

        testCoverConstrs.entrySet().stream().filter(e -> col.getSeq().contains(e.getKey()))
                .forEach(e -> grbColumn.addTerm(1, e.getValue()));
        GRBVar v;
        if (type == GRB.CONTINUOUS) {
            v = model.addVar(0, GRB.INFINITY, col.getCost() + Global.VEHICLE_COST, GRB.CONTINUOUS,
                    grbColumn, "use col " + col.getSeq());
        } else {
            v = model.addVar(0, 1, col.getCost() + Global.VEHICLE_COST,
                    GRB.BINARY, grbColumn, "use col " + col.getSeq());
        }

        varMap.put(col, v);
    }


    private List<Column> parseSol(List<Column> colList) {
        return colList.stream().filter(col -> {
            try {
                return varMap.get(col).get(GRB.DoubleAttr.X) > 0.5;
            } catch (GRBException e) {
                e.printStackTrace();
                return false;
            }
        }).collect(Collectors.toList());
    }
}
