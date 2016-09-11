package algorithm.pricer;

import algorithm.Column;
import data.DataInstance;
import data.TestRequest;
import ilog.concert.*;
import ilog.cp.IloCP;
import utils.Global;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
public class CPPricer implements Pricer {
    private IloCP solver;
    private IloIntVar[] testAtPosition;
    private IloIntVar[] startTimeAtPosition;
    private IloIntVar selectVehicle;
    private IloConstraint reducedCostConstr;
    private IloNumExpr reducedCostExpr;
    private double reducedCost;

    @Override
    public List<Column> price(Map<Integer, Double> testDual, Map<Integer, Double> vehicleDual) {
        List<Column> candidates = new ArrayList<>();
        try {

            if (null == this.solver) {
                this.solver = buildSolver(testDual, vehicleDual);
            }

            // update the reduced const constraints
//            this.reducedCostConstr = buildNegReducedCostConstr(this.solver, testDual, vehicleDual);


            if (this.solver.solve()) {
                // parse the solution
                this.reducedCost = this.solver.getValue(this.reducedCostExpr);
                Column col = parseSolution(this.solver);
                candidates.add(col);
            }

            this.solver.end();

        } catch (IloException ex) {
            ex.printStackTrace();
        }
        return candidates;
    }

    @Override
    public double getReducedCost() {
        return reducedCost;
    }

    private Column parseSolution(IloCP model) {
        List<Integer> seq = new ArrayList<>();
        final int[] tidArr = DataInstance.getInstance().getTidList().stream().mapToInt(Integer::intValue).toArray();
        final int[] releaseArr = DataInstance.getInstance().getVehicleReleaseList().stream().mapToInt(Integer::intValue)
                .toArray();

        final int numTests = tidArr.length;
        // which vehicle
        int releaseIdx = (int) Math.round(model.getValue(this.selectVehicle));
        int release = releaseArr[releaseIdx];
        // which tests
        for (int p = 0; p < Global.MAX_HITS; p++) {
            int tidIdx = (int) Math.round(model.getValue(testAtPosition[p]));
//            System.out.printf("test at position %d : %.3f\n", p,model.getValue(testAtPosition[p]));

            if (tidIdx != numTests)
                seq.add(tidArr[tidIdx]);
//            System.out.printf("start time at position %d: %.3f\n",p,model.getValue(startTimeAtPosition[p]));
        }
        return new Column(seq, release);
    }

    private IloConstraint buildNegReducedCostConstr(IloCP model,
                                                 Map<Integer, Double> testDual,
                                                 Map<Integer, Double> vehicleDual) throws IloException {

        final int numTests = DataInstance.getInstance().getTestArr().size();
        final int[] deadlineArr = Arrays.copyOf(DataInstance.getInstance().getTestArr().stream()
                .mapToInt(TestRequest::getDeadline).toArray(), numTests+1);
        deadlineArr[deadlineArr.length-1] = DataInstance.getInstance().getHorizonEnd(); // dummy test latest deadline
        final int[] durArr = Arrays.copyOf(DataInstance.getInstance().getTestArr().stream()
                .mapToInt(TestRequest::getDur).toArray(), numTests+1); // dummy test
        final double[] testDualArr = Arrays.copyOf(
                DataInstance.getInstance().getTidList().stream()
                        .mapToDouble(testDual::get)
                        .toArray(),
                numTests+1);
        final double[] vehicleDualArr = DataInstance.getInstance().getVehicleReleaseList().stream()
                .mapToDouble(vehicleDual::get)
                .toArray();

//        if (reducedCostConstr != null)
//            model.remove(reducedCostConstr);

        // build the initial constraint
        IloNumExpr reducedCostExpr = model.numExpr();
        reducedCostExpr = model.sum(reducedCostExpr, model.constant(Global.VEHICLE_COST)); // constants
        for (int p = 0; p < Global.MAX_HITS; p++) {
            reducedCostExpr = model.sum(reducedCostExpr,
                    model.max(model.diff(
                            model.sum(startTimeAtPosition[p], model.element(durArr, testAtPosition[p])),
                            model.element(deadlineArr, testAtPosition[p])), model.constant(0)));
        } // test tardiness
        for (int p = 0; p < Global.MAX_HITS; p++) {
            reducedCostExpr = model.diff(reducedCostExpr,
                    model.element(testDualArr, testAtPosition[p]));
        } // test duals
        reducedCostExpr = model.diff(reducedCostExpr,
                model.element(vehicleDualArr, selectVehicle)); // vehicle duals
        this.reducedCostExpr = reducedCostExpr;

        return model.addLe(reducedCostExpr, -0.001);

    }

    private IloCP buildSolver(Map<Integer, Double> testDual, Map<Integer,Double> vehicleDual) throws IloException {
        IloCP model = new IloCP();

        // parameters
        final int numTests = DataInstance.getInstance().getTestArr().size();
        final int numVehicles = DataInstance.getInstance().getVehicleReleaseList().size();
        final int numSlots = Global.MAX_HITS;
        final int horizonStart = DataInstance.getInstance().getHorizonStart();
        final int horizonEnd = DataInstance.getInstance().getHorizonEnd();

        final int[] releaseArr = DataInstance.getInstance().getVehicleReleaseList().stream().mapToInt(Integer::intValue)
                .toArray();
        final int[] durArr = Arrays.copyOf(DataInstance.getInstance().getTestArr().stream()
                .mapToInt(TestRequest::getDur).toArray(), numTests+1); // dummy test
        final int[] tidArr = DataInstance.getInstance().getTidList().stream().mapToInt(Integer::intValue).toArray();
        final int[] testReleaseArr = Arrays.copyOf(DataInstance.getInstance().getTestArr()
                .stream().mapToInt(TestRequest::getRelease).toArray(), numTests+1);

        // variables
        testAtPosition = model.intVarArray(numSlots, 0, numTests, "test at position");
        startTimeAtPosition = model.intVarArray(numSlots, horizonStart, horizonEnd, "start time at position");
        selectVehicle = model.intVar(0, numVehicles-1, "select vehicle");

        // constraints
        // start after the selected vehicle is released
        model.add(model.ge(startTimeAtPosition[0], model.element(releaseArr, selectVehicle)));

        // release time of tests
        for (int p = 0; p < Global.MAX_HITS; p++) {
            model.add(model.ge(startTimeAtPosition[p],
                    model.element(testReleaseArr, testAtPosition[p])));
        }

        // each test appear at once in the column
        for (int t=0; t < numTests; t++) {
            model.add(model.le(model.count(testAtPosition, t),1));
        }

        // if a position is left blank, all following positions are blank, too
        for (int p=0; p < Global.MAX_HITS; p++) {
            for (int q=p+1; q < Global.MAX_HITS; q++) {
                model.add(model.ifThen(model.eq(testAtPosition[p], numTests),
                        model.eq(testAtPosition[q], numTests)));
            }
        }

        // start time between two positions, assume immediate start
        for (int p=0; p < Global.MAX_HITS-1; p++) {
            model.add(model.eq(startTimeAtPosition[p+1],
                    model.sum(startTimeAtPosition[p], model.element(durArr, testAtPosition[p]))));
        }

        // start time if positions are left blank, symmetry breaking
        for (int p=1; p<Global.MAX_HITS; p++) {
            model.add(model.ifThen(model.eq(testAtPosition[p], numTests),
                    model.eq(startTimeAtPosition[p],
                            model.sum(startTimeAtPosition[p-1], model.element(durArr, testAtPosition[p-1])))));
        }

        // compatibility
        for (int i=0; i < numTests; i++) {
            int tid1 = tidArr[i];

            for (int j = i + 1; j<numTests; j++) {
                int tid2 = tidArr[j];
                if (!DataInstance.getInstance().getRelation(tid1,tid2)
                        && !DataInstance.getInstance().getRelation(tid2,tid1))
                    model.add(model.le(
                            model.sum(model.count(testAtPosition, i), model.count(testAtPosition, j)),
                            1
                    ));
                else if (!DataInstance.getInstance().getRelation(tid1, tid2)) {
                    for (int p=0; p<Global.MAX_HITS-1; p++) {
                        model.add(model.ifThen(
                                model.eq(testAtPosition[p], i),
                                model.eq(model.count(Arrays.copyOfRange(testAtPosition, p+1, Global.MAX_HITS), j), 0)
                        ));
                    }
                }
                else if (!DataInstance.getInstance().getRelation(tid2, tid1)) {
                    for (int p=0; p<Global.MAX_HITS-1; p++) {
                        model.add(model.ifThen(
                                model.eq(testAtPosition[p], j),
                                model.eq(model.count(Arrays.copyOfRange(testAtPosition, p+1, Global.MAX_HITS), i), 0)
                        ));
                    }
                }
            }
        }
//        model.setOut(null);
        buildNegReducedCostConstr(model, testDual, vehicleDual);
        return model;
    }
}
