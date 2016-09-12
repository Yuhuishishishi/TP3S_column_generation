package algorithm.pricer;

import algorithm.Column;
import data.DataInstance;
import data.TestRequest;
import org.jacop.constraints.*;
import org.jacop.core.BooleanVar;
import org.jacop.core.IntVar;
import org.jacop.core.Store;
import org.jacop.floats.constraints.ElementFloat;
import org.jacop.floats.constraints.LinearFloat;
import org.jacop.floats.constraints.XeqP;
import org.jacop.floats.core.FloatVar;
import org.jacop.search.*;
import utils.Global;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Created by yuhui on 9/11/16.
 */
public class JacopPricer implements Pricer{
    private double reducedCost;
    private IntVar[] testAtPosition;
    private IntVar selectVehicle;
    private IntVar[] startTimeAtPosition;

    public JacopPricer() {
        this.reducedCost = Double.MAX_VALUE;
    }

    @Override
    public List<Column> price(Map<Integer, Double> testDual, Map<Integer, Double> vehicleDual) {

        buildModel(testDual, vehicleDual);


        return null;
    }

    @Override
    public double getReducedCost() {
        return 0;
    }

    private Store buildModel(Map<Integer, Double> testDual, Map<Integer, Double> vehicleDual) {
        Store model = new Store();

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
//        final int[] prepArr = Arrays.copyOf(DataInstance.getInstance().getTestArr().stream()
//                .mapToInt(TestRequest::getPrep).toArray(), numTests+1); // dummy test
        final int[] tidArr = DataInstance.getInstance().getTidList().stream().mapToInt(Integer::intValue).toArray();
        final int[] testReleaseArr = Arrays.copyOf(DataInstance.getInstance().getTestArr()
                .stream().mapToInt(TestRequest::getRelease).toArray(), numTests+1);

        final int[] deadlineArr = Arrays.copyOf(DataInstance.getInstance().getTestArr().stream()
                .mapToInt(TestRequest::getDeadline).toArray(), numTests+1);
        final int[] testDualArr = Arrays.copyOf(
                DataInstance.getInstance().getTidList().stream()
                        .mapToDouble(e -> testDual.get(e)*-1).mapToInt(e -> (int) Math.round(e)) // reverted the sign
                        .toArray(),
                numTests+1);
        final int[] vehicleDualArr = DataInstance.getInstance().getVehicleReleaseList().stream()
                .mapToDouble(e -> vehicleDual.get(e)*-1).mapToInt(e -> (int) Math.round(e)) // reverted the sign
                .toArray();


        // variables
        testAtPosition = new IntVar[numSlots];
        startTimeAtPosition = new IntVar[numSlots];
        selectVehicle = new IntVar(model, "select_vehicle", 0, numVehicles-1);
        // initialization
        for (int p = 0; p < testAtPosition.length; p++) {
            testAtPosition[p] = new IntVar(model, "test_at_position" + p, 0, numTests);
            startTimeAtPosition[p] = new IntVar(model, "start_time_at_position" + p,
                    horizonStart, horizonEnd);
        }

        // auxiliary variables
        IntVar vehicleReleaseVar = new IntVar(model, 0, Arrays.stream(releaseArr).max().getAsInt());
        model.impose(new Element(selectVehicle, releaseArr, vehicleReleaseVar, 1));

        IntVar[] durAtPosition = new IntVar[numSlots];
//        IntVar[] prepAtPosition = new IntVar[numSlots];
        IntVar[] testReleaseAtPosition = new IntVar[numSlots];
        IntVar[] deadlineAtPosition = new IntVar[numSlots];
        IntVar[] occurenceOfTest = new IntVar[numTests];

        for (int p = 0; p < numSlots; p++) {
            durAtPosition[p] = new IntVar(model, 0, horizonEnd);
            model.impose(new Element(testAtPosition[p], durArr, durAtPosition[p], 1));

//            prepAtPosition[p] = new IntVar(model);
//            model.impose(new Element(testAtPosition[p], prepArr, prepAtPosition[p], 1));

            testReleaseAtPosition[p] = new IntVar(model, horizonStart, horizonEnd);
            model.impose(new Element(testAtPosition[p], testReleaseArr, testReleaseAtPosition[p], 1));

            deadlineAtPosition[p] = new IntVar(model, horizonStart, horizonEnd);
            model.impose(new Element(testAtPosition[p], deadlineArr, deadlineAtPosition[p], 1));
        }

        for (int t = 0; t < numTests; t++) {
            PrimitiveConstraint[] isTestUsed = new PrimitiveConstraint[numSlots];
            for (int p = 0; p < numSlots; p++) {
                isTestUsed[p] = new XeqC(testAtPosition[p], t);
            }
            occurenceOfTest[t] = new IntVar(model, 0, numSlots);

            model.impose(new Count(testAtPosition, occurenceOfTest[t], t));
        }


        // constraints
        // start after the selected vehicle is released
        model.impose(new XgteqY(testAtPosition[0], vehicleReleaseVar));

        // release time of tests, need fix
        for (int p = 0; p < numSlots; p++) {
            model.impose(new XgteqY(startTimeAtPosition[p], testReleaseAtPosition[p]));
        }

        // each test appear at once in the column
        IntStream.range(0, numTests).forEach(t->model.impose(new XlteqC(occurenceOfTest[t], 1)));

        // if a position is left blank, all following positions are blank, too
        for (int p = 0; p < numSlots; p++) {
            for (int q = p+1; q < numSlots; q++) {
                model.impose(new IfThen(new XeqC(testAtPosition[p], numTests),
                        new XeqC(testAtPosition[q], numTests)));
            }
        }

        // start time between two positions, assume immediate start
        for (int p = 0; p < numSlots-1; p++) {
            model.impose(new XplusYeqZ(startTimeAtPosition[p],
                    durAtPosition[p],
                    startTimeAtPosition[p+1]));
        }

        // compatibility
        for (int i=0; i < numTests; i++) {
            int tid1 = tidArr[i];

            for (int j = i + 1; j<numTests; j++) {
                int tid2 = tidArr[j];

                if (!DataInstance.getInstance().getRelation(tid1,tid2)
                        && !DataInstance.getInstance().getRelation(tid2,tid1))
                    model.impose(new Not(
                            new And(new XeqC(occurenceOfTest[i], 1),
                                    new XeqC(occurenceOfTest[i], 1))
                    ));
                else if (!DataInstance.getInstance().getRelation(tid1, tid2)) {
                    for (int p = 0; p < numSlots; p++) {
                        for (int q = p+1; q < numSlots; q++) {
                            model.impose(new IfThen(new XeqC(testAtPosition[p], i),
                                    new XneqC(testAtPosition[q], j)));
                        }
                    }
                }
                else if (!DataInstance.getInstance().getRelation(tid2, tid1)) {
                    for (int p = 0; p < numSlots; p++) {
                        for (int q = p+1; q < numSlots; q++) {
                            model.impose(new IfThen(new XeqC(testAtPosition[p], j),
                                    new XneqC(testAtPosition[q], i)));
                        }
                    }
                }
            }
        }

        // negative reduced cost
//         tardiness
        IntVar[] tardinessAtPosition = new IntVar[numSlots];
        for (int p = 0; p < numSlots; p++) {
            tardinessAtPosition[p] = new IntVar(model, 0, horizonEnd);
            IntVar end = new IntVar(model, horizonStart, horizonEnd);
            model.impose(new XplusYeqZ(startTimeAtPosition[p], durAtPosition[p], end));
            IntVar softDeadline = new IntVar(model, horizonStart, horizonEnd);
            model.impose(new XplusYeqZ(deadlineAtPosition[p], tardinessAtPosition[p], softDeadline));
            model.impose(new XlteqY(end, softDeadline));
        }
        IntVar totalTardiness = new IntVar(model, 0, horizonEnd);
        model.impose(new SumInt(model, tardinessAtPosition, "==", totalTardiness));

//
        // vehicle contribution
        IntVar vehicleContrib = new IntVar(model, Arrays.stream(vehicleDualArr).min().getAsInt(),
                Arrays.stream(vehicleDualArr).max().getAsInt());
        model.impose(new Element(selectVehicle, vehicleDualArr, vehicleContrib, 1));
//
        // test contribution
        IntVar[] testContribAtPosition = new IntVar[numSlots];
        for (int p = 0; p < numSlots; p++) {
            testContribAtPosition[p] = new IntVar(model, Arrays.stream(testDualArr).min().getAsInt(),
                    Arrays.stream(testDualArr).max().getAsInt());
            model.impose(new Element(testAtPosition[p], testDualArr, testContribAtPosition[p], 1));
        }
        IntVar totalTestContrib = new IntVar(model, Arrays.stream(testDualArr).sum(), 0);
        model.impose(new SumInt(model, testContribAtPosition, "==", totalTestContrib));

        // reduced cost = VEHICLE COST + total tardiness - test contribution - vehicle contribution
        IntVar rc = new IntVar(model, -1000, 1000);
        model.impose(new XplusYplusQeqZ(totalTardiness, vehicleContrib, totalTestContrib, rc));
        model.impose(new XltC(rc, (int)-Global.VEHICLE_COST));


        Search<IntVar> timeAssignment = new DepthFirstSearch<>();
        IntVar[] timeVars = new IntVar[numSlots+1];
        timeVars[0] = selectVehicle;
        for (int p = 0; p < numSlots; p++) {
            timeVars[1+p] = startTimeAtPosition[p];
        }
        SelectChoicePoint<IntVar> timeSearch = new InputOrderSelect<>(model, timeVars,
                new IndomainMin<>()
                );
        timeAssignment.setSelectChoicePoint(timeSearch);

        Search<IntVar> testAssignment = new DepthFirstSearch<>();
        SelectChoicePoint<IntVar> testSearch = new InputOrderSelect<>(model, testAtPosition,
                new IndomainMin<>());
        testAssignment.addChildSearch(timeAssignment);
        boolean result = testAssignment.labeling(model, testSearch);

        if (result) {
            for (int p = 0; p < numSlots; p++) {
                System.out.println("testAtPosition = " + testAtPosition[p]);
                System.out.println("startTimeAtPosition = " + startTimeAtPosition[p]);
            }
            System.out.println("rc = " + rc);
        }









        return model;
    }
}
