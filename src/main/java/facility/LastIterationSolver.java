package facility;

import algorithm.Algorithm;
import algorithm.Column;
import data.DataInstance;
import data.TestRequest;
import gurobi.*;
import utils.Global;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Created by yuhuishi on 12/14/2016.
 * University of Michigan
 * Academic use only
 */
public class LastIterationSolver implements Algorithm {


    private List<Column> colPool;

    public LastIterationSolver(List<Column> colPool) {
        this.colPool = colPool;
    }

    @Override
    public void solve() {

        System.out.println("\n======================== Build the last iteration timing problem ===========================");

        // solve the last iteration problem using MILP formulation
        try {
            GRBEnv env = new GRBEnv("last_iteration");
            GRBModel model = new GRBModel(env);

            // constraints maps
            Map<Integer, GRBConstr> testCoverConstrs = new HashMap<>();
            Map<Integer, GRBConstr> vehicleCapConstrs = new HashMap<>();

            // test cover constraints
            for (int tid : DataInstance.getInstance().getTidList()) {
                testCoverConstrs.put(tid, model.addConstr(
                        new GRBLinExpr(), GRB.EQUAL, 1.0, "cover test " + tid));
            }

            // vehicle capacity constraints
            for (int release : DataInstance.getInstance().getVehicleReleaseList()) {
                vehicleCapConstrs.put(release, model.addConstr(
                        new GRBLinExpr(), GRB.LESS_EQUAL, DataInstance.getInstance().numVehiclesByRelease(release),
                        "vehicle capacity " + release
                ));
            }

            model.update();

            Map<Column, GRBVar> varMap = new HashMap<>();

            // add variables
            for (Column column : colPool) {
                GRBColumn grbCol = new GRBColumn();
                int vehicleRelease = column.getRelease();
                grbCol.addTerm(1.0, vehicleCapConstrs.get(vehicleRelease));

                column.getSeq().forEach(tid -> grbCol.addTerm(1.0, testCoverConstrs.get(tid)));
                GRBVar var = model.addVar(0.0, 1.0, Global.VEHICLE_COST, GRB.BINARY, grbCol, "use_col_" + column);
                varMap.put(column, var);
            }

            model.update();

            // add the time decisions
            Map<Integer, GRBVar[]> startTimes = new HashMap<>();
            final int numHorizon = DataInstance.getInstance().getHorizonEnd()
                    - DataInstance.getInstance().getHorizonStart() + 1;
            final double[] lb = new double[numHorizon];
            Arrays.fill(lb, 0.0);
            final double[] ub = new double[numHorizon];
            Arrays.fill(ub, 1.0);
            final char[] vtype = new char[numHorizon];
            Arrays.fill(vtype, GRB.BINARY);

            for (int tid : DataInstance.getInstance().getTidList()) {
                double[] obj = new double[numHorizon];
                TestRequest test = DataInstance.getInstance().getTestById(tid);
                for (int d = 0; d < numHorizon; d++) {
                    int end = d + test.getDur();
                    int tardiness = Math.max(0, end - test.getDeadline());
                    if (end > numHorizon) tardiness = numHorizon * 5;
                    obj[d] = tardiness;
                }

                GRBVar[] times = model.addVars(lb, ub, obj, vtype, null);
                startTimes.put(tid, times);
            }

            model.update();
//
            // add constraints related to times
//
            // aux variables
            Map<Integer, GRBVar> startTimeCont = new HashMap<>();
            // initialize
            for (int tid : DataInstance.getInstance().getTidList()) {
                startTimeCont.put(tid,
                        model.addVar(0, numHorizon, 0, GRB.CONTINUOUS, null));
            }
//
//
//            // vehicle release
            Map<Integer, Map<Integer, GRBVar>> assignTestToVehicle = new HashMap<>();
            // initialization
            for (int tid : DataInstance.getInstance().getTidList()) {
                assignTestToVehicle.put(tid, new HashMap<>());
                for (int release : DataInstance.getInstance().getVehicleReleaseList())
                    assignTestToVehicle.get(tid).put(release,
                        model.addVar(0, 1, 0, GRB.BINARY, null));
            }
//
            // precedence relations
            Map<Integer, Map<Integer, GRBVar>> precedenceIndicators = new HashMap<>();
            // initialization
            for (int tid1 : DataInstance.getInstance().getTidList()) {
                precedenceIndicators.put(tid1, new HashMap<>());
                for (int tid2 : DataInstance.getInstance().getTidList()) {
                    if (tid1 == tid2) continue;
                    precedenceIndicators.get(tid1).put(tid2,
                            model.addVar(0, 1, 0, GRB.BINARY, null));
                }
            }
//
//
            model.update();
//
//            // aux variable linking
            for (int tid : DataInstance.getInstance().getTidList()) {
                        // start time linking
                        GRBLinExpr expr = new GRBLinExpr();
                        double[] coeff = IntStream.range(0, numHorizon).mapToDouble(d -> (double) d).toArray();
                            expr.addTerms(coeff, startTimes.get(tid));
                            model.addConstr(expr, GRB.EQUAL, startTimeCont.get(tid), null);


                    }
//
//            // precedence indicator and vehicle assignment linking
            Map<GRBVar, GRBLinExpr> assignTestMap = new HashMap<>(), precedenceIndicatorMap = new HashMap<>();
            assignTestToVehicle.values().stream().flatMap(map->map.values().stream())
                    .forEach(var->assignTestMap.put(var, new GRBLinExpr()));
            precedenceIndicators.values().stream().flatMap(map->map.values().stream())
                    .forEach(var->precedenceIndicatorMap.put(var, new GRBLinExpr()));
//
            for (Column col : colPool) {
                // assign test to vehicle
                for (int tid : col.getSeq()) {
                    GRBVar var = assignTestToVehicle.get(tid).get(col.getRelease());
                    GRBLinExpr expr = assignTestMap.get(var);
                    expr.addTerm(1, varMap.get(col));
                }

                // precedence linking
                for (int i = 0; i < col.getSeq().size() - 1; i++) {
                    int firstTid = col.getSeq().get(i);
                    int secondTid = col.getSeq().get(i+1);

                    GRBVar var = precedenceIndicators.get(firstTid).get(secondTid);
                    GRBLinExpr expr = precedenceIndicatorMap.get(var);
                    expr.addTerm(1, varMap.get(col));
                }
            }

            model.update();

//
//            // add equality constraint
            for (Map.Entry<GRBVar, GRBLinExpr> entry : assignTestMap.entrySet()) {
                if (entry.getValue().size()==0) continue;
                model.addConstr(entry.getKey(), GRB.EQUAL, entry.getValue(), null);
            }
//
            for (Map.Entry<GRBVar, GRBLinExpr> entry : precedenceIndicatorMap.entrySet()) {
                if (entry.getValue().size()==0) continue;
                model.addConstr(entry.getKey(), GRB.EQUAL, entry.getValue(), null);
            }
//
//
            // one start time
            double[] coef = new double[numHorizon];
            Arrays.fill(coef, 1);
            for (int tid : DataInstance.getInstance().getTidList()) {
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerms(coef, startTimes.get(tid));
                model.addConstr(expr, GRB.EQUAL, 1, null);
            }
//
            // test release
            for (int tid : DataInstance.getInstance().getTidList()) {
                TestRequest test = DataInstance.getInstance().getTestById(tid);
                for (int d = 0; d < test.getRelease(); d++) {
                    startTimes.get(tid)[d].set(GRB.DoubleAttr.UB, 0.0);
                }
            }
//
            // vehicle release
            for (int tid : DataInstance.getInstance().getTidList()) {
                GRBVar[] vars = startTimes.get(tid);
                for (int release : DataInstance.getInstance().getVehicleReleaseList()) {
                    // s1 + s2 + s3 + ... s_v <= 1 - u_v

                    GRBVar releaseVar = assignTestToVehicle.get(tid).get(release);
                    GRBLinExpr lhs = new GRBLinExpr(), rhs = new GRBLinExpr();
                    lhs.addTerms(coef, vars, 0, release);
                    rhs.addConstant(1);
                    rhs.addTerm(-1, releaseVar);
                    model.addConstr(lhs, GRB.LESS_EQUAL, rhs, null);
                }
            }
//
//            // no overlap
            for (int tid1 : DataInstance.getInstance().getTidList()) {
                TestRequest test1 = DataInstance.getInstance().getTestById(tid1);
                for (int tid2 : DataInstance.getInstance().getTidList()) {
                    if (tid1 == tid2) continue;

                    // s1 + p1 <= s2 + M(1-y12)
                    GRBLinExpr lhs = new GRBLinExpr(), rhs = new GRBLinExpr();
                    lhs.addConstant(test1.getDur());
                    lhs.addTerm(1, startTimeCont.get(tid1));

                    rhs.addTerm(1, startTimeCont.get(tid2));
                    rhs.addConstant(numHorizon);
                    rhs.addTerm(-numHorizon, precedenceIndicators.get(tid1).get(tid2));

                    model.addConstr(lhs, GRB.LESS_EQUAL, rhs, null);
                }
            }
//
//
            // resource capacity
            GRBLinExpr[] testOngoingOnDay = new GRBLinExpr[numHorizon];
            // initialization
            for (int i = 0; i < testOngoingOnDay.length; i++) {
                testOngoingOnDay[i] = new GRBLinExpr();
            }
            DataInstance.getInstance().getTidList()
                    .forEach(tid -> {
                        TestRequest test = DataInstance.getInstance().getTestById(tid);
                        int tatStart = test.getPrep();
                        int tatEnd = test.getPrep() + test.getTat();
                        GRBVar[] var = startTimes.get(tid);
                        for (int d = 0; d < numHorizon; d++) {
                            for (int j = d + tatStart; j < Math.min(numHorizon, d + tatEnd); j++) {
                                testOngoingOnDay[j].addTerm(1, var[d]);
                            }
                        }
                    });
            // resource constraints
            char[] sense = new char[numHorizon];
            Arrays.fill(sense, GRB.LESS_EQUAL);
            double[] rhs = new double[numHorizon];
            Arrays.fill(rhs, Global.FACILITY_CAP);
            model.addConstrs(testOngoingOnDay, sense, rhs, null);
//
//            // solve the model
//            model.update();
            // set branch priority
            for (GRBVar grbVar : varMap.values()) {
                grbVar.set(GRB.IntAttr.BranchPriority, 1);
            }

            model.optimize();

            if (model.get(GRB.IntAttr.Status)==GRB.OPTIMAL) {
                // parse the used columns
                int counter = 0;
                for (GRBVar grbVar : varMap.values()) {
                    if (grbVar.get(GRB.DoubleAttr.X) > 0.5)
                        counter++;
                }
                System.out.println("Used vehicles : " + counter);
                System.out.println("Tardiness : " + (model.get(GRB.DoubleAttr.ObjVal) - Global.VEHICLE_COST*counter));

            }

            model.dispose();


        } catch (GRBException e) {
            e.printStackTrace();
        }


    }

}
