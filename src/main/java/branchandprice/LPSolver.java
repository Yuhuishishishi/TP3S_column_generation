package branchandprice;

/**
 * Created by yuhuishi on 12/18/2016.
 * University of Michigan
 * Academic use only
 */

import algorithm.Column;
import algorithm.pricer.Pricer;
import data.DataInstance;
import gurobi.*;
import utils.Global;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LPSolver {
    // solve the LP relaxation with extra branching constraint


    private gurobi.GRBModel solver;
    private List<Column> currentColPool;
    private List<BranchConstraint> constraintList;

    private Map<Integer, GRBConstr> testCoverConstrs;
    private Map<Integer, GRBConstr> vehicleCapConstrs;

    private Map<Column, GRBVar> vars;

    public LPSolver(List<Column> currentColPool, List<BranchConstraint> constraintList, GRBEnv env) {
        this.currentColPool = currentColPool;
        this.constraintList = constraintList;
        this.vars = new HashMap<>();

        try {
            this.solver = new GRBModel(env);
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }

    public Map<Column, Double> solve() throws GRBException {

        buildBaseProblem();
        solver.getEnv().set(GRB.IntParam.OutputFlag, 0);

        int iterTimes = 0;
        Pricer pricer = EnumPricerWithBranchConstraints.getPricer(constraintList);
        while (iterTimes++ < BranchAndPrice.MAX_ITERATIONS) {

            solver.optimize();
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
                    solver.get(GRB.DoubleAttr.ObjVal),
                    pricer.getReducedCost());
            if (candidates.size() == 0)
                break;
            // add the column to the master problem
            for (Column col : candidates) {
                addOneCol(col);
                currentColPool.add(col);
            }
            System.out.println("candidates.size = " + candidates.size());
            solver.update();
        }


        // parse the solution
        Map<Column, Double> result = new HashMap<>();
        for (Map.Entry<Column, GRBVar> entry : vars.entrySet()) {
            result.put(entry.getKey(),
                    entry.getValue().get(GRB.DoubleAttr.X));
        }

        return result;
    }

    private void buildBaseProblem() throws GRBException {
        testCoverConstrs = new HashMap<>();
        vehicleCapConstrs = new HashMap<>();

        // test cover constraints
        for (int tid : DataInstance.getInstance().getTidList()) {
            GRBConstr constr = solver.addConstr(
                    new GRBLinExpr(), GRB.GREATER_EQUAL, 1.0, "cover test " + tid);
            testCoverConstrs.put(tid, constr);
        }

        // vehicle capacity constraints
        for (int release : DataInstance.getInstance().getVehicleReleaseList()) {
            GRBConstr constr = solver.addConstr(
                    new GRBLinExpr(), GRB.LESS_EQUAL, DataInstance.getInstance().numVehiclesByRelease(release),
                    "vehicle capacity " + release);
            vehicleCapConstrs.put(release, constr);
        }

        solver.update();

        // add variables
        for (Column col : currentColPool) {
            addOneCol(col);
        }

        solver.update();
    }

    private boolean addOneCol(Column col) throws GRBException {
        // check if the column satisfy the branching constraints, if yes, add it to the problem
        for (BranchConstraint cons : constraintList) {
            if (cons.isColFixToZero(col))
                return false; // skip the column
        }

        GRBColumn grbColumn = new GRBColumn();
        grbColumn.addTerm(1, vehicleCapConstrs.get(col.getRelease()));

        for (int tid : col.getSeq()) {
            grbColumn.addTerm(1, testCoverConstrs.get(tid));
        }

        GRBVar v = solver.addVar(0, GRB.INFINITY, col.getCost() + Global.VEHICLE_COST, GRB.CONTINUOUS,
                grbColumn, "use col " + col.getSeq());

        vars.put(col, v);

        return true;
    }

    // solve integer version of the problem
     public List<Column> solveIntegerProblem() throws GRBException {
         // convert continuous variables to binary variables
         for (GRBVar var : vars.values()) {
             var.set(GRB.CharAttr.VType, GRB.BINARY);
         }

         this.solver.update();
         this.solver.getEnv().set(GRB.DoubleParam.TimeLimit, BranchAndPrice.INTEGER_PROBLEM_TIME_LIMIT); // time limit ;
         solver.getEnv().set(GRB.DoubleParam.Cutoff, BranchAndPrice.getBranchTree().getUpperbound());
         solver.getEnv().set(GRB.IntParam.OutputFlag, 1);

         this.solver.optimize();

         int status = this.solver.get(GRB.IntAttr.Status);
         assert status==GRB.OPTIMAL || status==GRB.TIME_LIMIT;

         if (solver.get(GRB.IntAttr.Status)==GRB.CUTOFF)
             return new ArrayList<>();

         // parse the solution
         List<Column> result = new ArrayList<>();
         for (Map.Entry<Column, GRBVar> entry : vars.entrySet()) {
             if (entry.getValue().get(GRB.DoubleAttr.X) > 0.5) {
                 result.add(entry.getKey());
             }
         }

         return result;
     }

    public void end() {
        solver.dispose();
    }


}
