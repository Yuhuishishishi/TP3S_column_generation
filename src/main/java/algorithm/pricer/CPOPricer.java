package algorithm.pricer;

import algorithm.Column;
import data.DataInstance;
import data.TestRequest;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloNumExpr;
import ilog.concert.IloRange;
import ilog.cp.*;
import utils.Global;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
@SuppressWarnings("Duplicates")
public class CPOPricer implements Pricer {

    private double reducedCost;
    private IloCP solver;
    private IloRange negReducedConstContr;
    private IloIntVar[] testAtPosition;
    private IloIntVar[] startTimeAtPosition;
    private IloIntVar selectVehicle;
    private IloIntVar[] durAtPosition;

    public CPOPricer() {
        this.reducedCost = Double.MAX_VALUE;
    }

    private IloCP buildBaseSolver(Map<Integer, Double> testDual, Map<Integer, Double> vehicleDual) {
        // parameters
        final int numTests = DataInstance.getInstance().getTestArr().size();
        final int numVehicles = DataInstance.getInstance().getVehicleReleaseList().size();
        final int numSlots = Global.MAX_HITS;
        final int horizonStart = DataInstance.getInstance().getHorizonStart();
        final int horizonEnd = DataInstance.getInstance().getHorizonEnd();

        final int[] releaseArr = DataInstance.getInstance().getVehicleReleaseList().stream().mapToInt(Integer::intValue)
                .toArray();
        final int[] durArr = Arrays.copyOf(DataInstance.getInstance().getTestArr().stream()
                .mapToInt(TestRequest::getDur).toArray(), numTests + 1); // dummy test
        final int[] prepArr = Arrays.copyOf(DataInstance.getInstance().getTestArr().stream()
                .mapToInt(TestRequest::getPrep).toArray(), numTests + 1); // dummy test
        final int[] tidArr = DataInstance.getInstance().getTidList().stream().mapToInt(Integer::intValue).toArray();
        final int[] testReleaseArr = Arrays.copyOf(DataInstance.getInstance().getTestArr()
                .stream().mapToInt(TestRequest::getRelease).toArray(), numTests + 1);

        final int[] deadlineArr = Arrays.copyOf(DataInstance.getInstance().getTestArr().stream()
                .mapToInt(TestRequest::getDeadline).toArray(), numTests + 1);
        deadlineArr[deadlineArr.length - 1] = horizonEnd;


        IloCP model = new IloCP();

        try {

            // variables
            testAtPosition = new IloIntVar[numSlots];
            startTimeAtPosition = new IloIntVar[numSlots];
            selectVehicle = model.intVar(0, numVehicles - 1);
            // initialization
            for (int p = 0; p < numSlots; p++) {
                testAtPosition[p] = model.intVar(0, numTests, "test_at_position");
                startTimeAtPosition[p] = model.intVar(horizonStart, horizonEnd, "starttime_at_position");
            }

            // aux variables
            durAtPosition = new IloIntVar[numSlots];
            for (int p = 0; p < numSlots; p++) {
                durAtPosition[p] = model.intVar(0, 1000, "dur_at_position");
                model.addEq(durAtPosition[p], model.element(durArr, testAtPosition[p]));
            }

            // constraints
            // start after the selected vehicle is released
            model.add(model.ge(startTimeAtPosition[0],
                    model.element(releaseArr, selectVehicle)));

            // release time of tests, need fix
            for (int p = 0; p < numSlots; p++) {
                model.addGe(model.sum(startTimeAtPosition[p], model.element(prepArr, testAtPosition[p])),
                        model.element(testReleaseArr, testAtPosition[p]));
            }

            // each test appear at once in the column
            for (int t = 0; t < numTests; t++) {
                model.addLe(model.count(testAtPosition, t),
                        1);
            }

            // if a position is left blank, all following positions are blank, too
            for (int p = 0; p < numSlots; p++) {
                for (int q = p + 1; q < numSlots; q++) {
                    model.add(model.ifThen(model.eq(testAtPosition[0], numTests),
                            model.eq(testAtPosition[q], numTests)));
                }
            }

            // start time between two positions
            for (int p = 0; p < numSlots - 1; p++) {
                model.addGe(startTimeAtPosition[p + 1],
                        model.sum(startTimeAtPosition[p], durAtPosition[p]));
            }

            // if a position is left blank, then the start time is == previous end time
            for (int p = 1; p < numSlots; p++) {
                model.add(model.ifThen(model.eq(testAtPosition[p], numTests),
                        model.eq(startTimeAtPosition[p],
                                model.sum(startTimeAtPosition[p - 1], durAtPosition[p - 1]))));
            }

            // compatibility
            for (int i = 0; i < numTests; i++) {
                int tid1 = tidArr[i];

                for (int j = i + 1; j < numTests; j++) {
                    int tid2 = tidArr[j];

                    if (!DataInstance.getInstance().getRelation(tid1, tid2)
                            && !DataInstance.getInstance().getRelation(tid2, tid1))
                        model.addLe(model.sum(model.count(testAtPosition, i), model.count(testAtPosition, j)),
                                1);
                    else if (!DataInstance.getInstance().getRelation(tid1, tid2)) {
                        for (int p = 0; p < numSlots; p++) {
                            for (int q = p + 1; q < numSlots; q++) {
                                model.add(model.ifThen(model.eq(testAtPosition[p], i),
                                        model.neq(testAtPosition[q], j)));
                            }
                        }
                    } else if (!DataInstance.getInstance().getRelation(tid2, tid1)) {
                        for (int p = 0; p < numSlots; p++) {
                            for (int q = p + 1; q < numSlots; q++) {
                                model.add(model.ifThen(model.eq(testAtPosition[p], j),
                                        model.neq(testAtPosition[q], i)));
                            }
                        }
                    }
                }
            }

            negReducedConstContr = model.addLe(model.numExpr(), -0.001);

        } catch (IloException ex) {
            ex.printStackTrace();
        }

        return model;
    }

    @Override
    public void end() {
        this.solver.end();
    }

    @Override
    public List<Column> price(Map<Integer, Double> testDual, Map<Integer, Double> vehicleDual) {

        List<Column> candidates = new ArrayList<>();

        // parameters
        final int numTests = DataInstance.getInstance().getTestArr().size();
        final int numSlots = Global.MAX_HITS;
        final int horizonEnd = DataInstance.getInstance().getHorizonEnd();
        final int[] releaseArr = DataInstance.getInstance().getVehicleReleaseList().stream().mapToInt(Integer::intValue)
                .toArray();
        final int[] tidArr = DataInstance.getInstance().getTidList().stream().mapToInt(Integer::intValue).toArray();
        final int[] deadlineArr = Arrays.copyOf(DataInstance.getInstance().getTestArr().stream()
                .mapToInt(TestRequest::getDeadline).toArray(), numTests + 1);
        deadlineArr[deadlineArr.length - 1] = horizonEnd;
        final double[] testDualArr = Arrays.copyOf(
                DataInstance.getInstance().getTidList().stream()
                        .mapToDouble(e -> testDual.get(e) * -1) //.mapToInt(e -> (int) Math.round(e)) // reverted the sign
                        .toArray(),
                numTests + 1);
        final double[] vehicleDualArr = DataInstance.getInstance().getVehicleReleaseList().stream()
                .mapToDouble(e -> vehicleDual.get(e) * -1)//.mapToInt(e -> (int) Math.round(e)) // reverted the sign
                .toArray();

        if (null == this.solver)
            this.solver = buildBaseSolver(testDual, vehicleDual);

        IloCP model;
        try {

            if (this.negReducedConstContr != null)
                this.solver.remove(this.negReducedConstContr);

            model = this.solver;
            // negative reduced cost
            IloNumExpr reducedCostExpr = model.numExpr();
            reducedCostExpr = model.sum(reducedCostExpr, model.constant(Global.VEHICLE_COST));

            for (int p = 0; p < numSlots; p++) {
                // tardiness
                IloIntVar tardiness = model.intVar(0, horizonEnd);
                model.addEq(tardiness,
                        model.max(0,
                                model.diff(model.sum(startTimeAtPosition[p], durAtPosition[p]),
                                        model.element(deadlineArr, testAtPosition[p]))));
                reducedCostExpr = model.sum(reducedCostExpr, tardiness);
                // test dual contribution
                reducedCostExpr = model.sum(reducedCostExpr, model.element(testDualArr, testAtPosition[p]));
            }
            // vehicle dual contribution
            reducedCostExpr = model.sum(reducedCostExpr, model.element(vehicleDualArr, selectVehicle));

            // negative reduced cost constraints
            model.addLe(reducedCostExpr, -0.001);

//            model.setOut(null);


            // ================================ searching strategy======================================================
            IloSearchPhase[] searchPhases = new IloSearchPhase[3];

            // first fix the assignment of vehicle
            // variable selector = None. only one variable
            // value selector = smallest dual contribution first, if tie, smaller release first
            IloValueSelector[] vehicleValueSelector = new IloValueSelector[2];
            final int numVehicles = DataInstance.getInstance().getVehicleReleaseList().size();
            vehicleValueSelector[0] = model.selectSmallest(
                    model.explicitValueEval(IntStream.range(0, numVehicles).toArray(),
                            vehicleDualArr));  // smallest vehicle dual contribution first
            vehicleValueSelector[1] = model.selectSmallest(model.value());
            IloSearchPhase vehicleSearch = model.searchPhase(new IloIntVar[]{selectVehicle},
                    model.intVarChooser(model.selectRandomVar()),
                    model.intValueChooser(vehicleValueSelector));
            searchPhases[0] = vehicleSearch;

            // fix the test assignment
            // select the test with the least dual contribution
            IloValueSelector testValueSelector = model.selectSmallest(model.explicitValueEval(
                    IntStream.range(0, numTests).toArray(),
                    testDualArr));
            // select the variable on smaller slots first
            IloVarSelector testVarSelector = model.selectSmallest(model.varIndex(testAtPosition));
            IloSearchPhase testSearch = model.searchPhase(testAtPosition,
                    model.intVarChooser(testVarSelector),
                    model.intValueChooser(testValueSelector));
            searchPhases[1] = testSearch;

            // fix the start time of tests

            // fix all auxiliary variables





            // solve the problem
            if (model.solve()) {
                // parse the solution
                this.reducedCost = model.getValue(reducedCostExpr);
                List<Integer> seq = new ArrayList<>();
                int vehicleIdx = (int) Math.round(model.getValue(selectVehicle));
                int colRelease = releaseArr[vehicleIdx];
                for (int p = 0; p < numSlots; p++) {
                    int tidIdx = (int) Math.round(model.getValue(testAtPosition[p]));
                    if (tidIdx != numTests)
                        seq.add(tidArr[tidIdx]);
                }
                Column newcol = new Column(seq, colRelease);
                candidates.add(newcol);
            }


        } catch (IloException e1) {
            e1.printStackTrace();
        }
        return candidates;
    }

    @Override
    public double getReducedCost() {
        return this.reducedCost;
    }
}
