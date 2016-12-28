package integerprogramming;

import algorithm.Algorithm;
import data.DataInstance;
import data.TestRequest;
import gurobi.*;
import utils.Global;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by yuhuishi on 12/27/2016.
 * University of Michigan
 * Academic use only
 */
public class GeneralIntegerProgramming implements Algorithm {
    public static final double IP_TIME_LIMIT = 600;
    private GRBVar[][] assignTest;
    private GRBVar[][] precedence;
    private GRBVar[] startTime;
    private GRBVar[] tardiness;
    private GRBVar[] useVehicle;

    private long initTime;

    public GeneralIntegerProgramming() {
        this.initTime = System.currentTimeMillis();
    }

    private GRBModel buildModel(GRBEnv env) throws GRBException {
        // build the integer programming model
        GRBModel model = new GRBModel(env);

        // parameters
        final int numVehicle = DataInstance.getInstance().getVehicleReleaseList()
                .stream().mapToInt(release->DataInstance.getInstance().numVehiclesByRelease(release)).sum();
        final TestRequest[] tests = DataInstance.getInstance().getTestArr()
                .toArray(new TestRequest[DataInstance.getInstance().getTestArr().size()]);
        final int numTest = tests.length;
        final int horizonMax = DataInstance.getInstance().getHorizonEnd();
        int counter = 0;
        final int[] vehicleRelease = new int[numVehicle];

        for (int i = 0; i < DataInstance.getInstance().getVehicleReleaseList().size(); i++) {
            int release = DataInstance.getInstance().getVehicleReleaseList().get(i);
            for (int j = 0; j < DataInstance.getInstance().numVehiclesByRelease(release); j++) {
                vehicleRelease[counter++] = release;
            }
        }

        // initialize variables
        assignTest = new GRBVar[numTest][numVehicle];
        precedence = new GRBVar[numTest][numTest];
        startTime = new GRBVar[numTest];
        tardiness = new GRBVar[numTest];
        useVehicle = new GRBVar[numVehicle];

        for (int t = 0; t < numTest; t++) {
            assignTest[t] = model.addVars(numVehicle, GRB.BINARY);
            precedence[t] = model.addVars(numTest, GRB.BINARY);
            startTime[t] = model.addVar(tests[t].getRelease(), horizonMax, 0, GRB.CONTINUOUS, null);
            tardiness[t] = model.addVar(0, horizonMax, 1, GRB.CONTINUOUS, null);
        }

        for (int v = 0; v < numVehicle; v++) {
            useVehicle[v] = model.addVar(0, 1, Global.VEHICLE_COST, GRB.BINARY, null);
        }

        model.update();

        // constraints
        // tests can be assigned to a vehicle only it is used
        for (int v = 0; v < numVehicle; v++) {
            GRBLinExpr expr = new GRBLinExpr();
            for (int t = 0; t < numTest; t++) {
                expr.addTerm(1, assignTest[t][v]);
            }
            GRBLinExpr rhs = new GRBLinExpr();
            rhs.addTerm(Global.MAX_HITS, useVehicle[v]);

            model.addConstr(expr, GRB.LESS_EQUAL, rhs, null);
        }

        // each test included exactly once
        for (int t = 0; t < numTest; t++) {
            GRBLinExpr expr = new GRBLinExpr();
            for (int v = 0; v < numVehicle; v++) {
                expr.addTerm(1, assignTest[t][v]);
            }
            model.addConstr(expr, GRB.EQUAL, 1, null);
        }

        // start after vehicle release
        for (int t = 0; t < numTest; t++) {
            GRBLinExpr expr = new GRBLinExpr();
            for (int v = 0; v < numVehicle; v++) {
                expr.addTerm(vehicleRelease[v], useVehicle[v]);
            }
            model.addConstr(startTime[t], GRB.GREATER_EQUAL, expr, null);
        }
//
//        // tardiness linking
        for (int t = 0; t < numTest; t++) {
            GRBLinExpr lhs = new GRBLinExpr();
            GRBLinExpr rhs = new GRBLinExpr();

            lhs.addTerm(1, startTime[t]);
            lhs.addConstant(tests[t].getDur());

            rhs.addConstant(tests[t].getDeadline());
            rhs.addTerm(1, tardiness[t]);

            model.addConstr(lhs, GRB.LESS_EQUAL, rhs, null);
        }

        // relations between a pair of tests
        for (int t1 = 0; t1 < numTest; t1++) {
            for (int t2 = t1+1; t2 < numTest; t2++) {
                // at most one precedence
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1, precedence[t1][t2]);
                expr.addTerm(1, precedence[t2][t1]);
                model.addConstr(expr, GRB.LESS_EQUAL, 1, null);

                // if two tests assigned to the same vehicle, then one precedence
                for (int v = 0; v < numVehicle; v++) {
                    GRBLinExpr expr2 = new GRBLinExpr();
                    expr2.addTerm(1, assignTest[t1][v]);
                    expr2.addTerm(1, assignTest[t2][v]);
                    expr2.addConstant(-1);
                    model.addConstr(expr2, GRB.LESS_EQUAL, expr, null);
                }

                // start time and end time
                GRBLinExpr lhs = new GRBLinExpr();
                GRBLinExpr rhs = new GRBLinExpr();
                lhs.addTerm(1, startTime[t1]);
                lhs.addConstant(tests[t1].getDur());
                rhs.addTerm(1, startTime[t2]);
                rhs.addTerm(-horizonMax, precedence[t1][t2]);
                rhs.addConstant(horizonMax);
                model.addConstr(lhs, GRB.LESS_EQUAL, rhs, null);

                // reverse direction
                lhs.clear(); rhs.clear();
                lhs.addTerm(1, startTime[t2]);
                lhs.addConstant(tests[t2].getDur());
                rhs.addTerm(1, startTime[t1]);
                rhs.addTerm(-horizonMax, precedence[t2][t1]);
                rhs.addConstant(horizonMax);

                model.addConstr(lhs, GRB.LESS_EQUAL, rhs, null);


                // incompatibility
//                if (!DataInstance.getInstance().getRelation(tests[t1].getTid(),
//                        tests[t2].getTid()))
//                    precedence[t1][t2].set(GRB.DoubleAttr.UB, 0);
////
//                if (!DataInstance.getInstance().getRelation(tests[t2].getTid(),
//                        tests[t1].getTid()))
//                    precedence[t2][t1].set(GRB.DoubleAttr.UB, 0);
            }
        }
        model.update();

        return model;

    }

    @Override
    public void solve() {
        try {
            GRBEnv env = new GRBEnv();
            GRBModel model = buildModel(env);

            model.getEnv().set(GRB.DoubleParam.TimeLimit, IP_TIME_LIMIT);

            model.optimize();

            int status = model.get(GRB.IntAttr.Status);
            if (status==GRB.OPTIMAL
                    || status==GRB.TIME_LIMIT) {
                // parse the solution
                int numVehicleUsed = 0;
                double totalTardiness = 0;
                double optGap = model.get(GRB.DoubleAttr.MIPGap);

                // count the number of vehicles
                for (int v = 0; v < useVehicle.length; v++) {
                    if (useVehicle[v].get(GRB.DoubleAttr.X) > 0.5)
                        numVehicleUsed++;
                }

                // count the tardiness
                for (int t = 0; t < this.tardiness.length; t++) {
                    totalTardiness += tardiness[t].get(GRB.DoubleAttr.X);
                }

                System.out.println("Used vehicles: " + numVehicleUsed);
                System.out.println("Tardiness: " + totalTardiness);
                System.out.println("Obj val: " + (numVehicleUsed*Global.VEHICLE_COST+totalTardiness));
                System.out.println("Opt gap: " + optGap);


            } else {
                System.out.println("Unable to find feasible solution");
            }



            model.dispose();
            env.dispose();
        } catch (GRBException e) {
            e.printStackTrace();
        }




    }

    @Override
    public long getTimeTillNow() {
        return System.currentTimeMillis()-initTime;
    }
}
