package algorithm.pricer;

import algorithm.Column;
import data.DataInstance;
import data.TestRequest;
import org.jacop.constraints.*;
import org.jacop.core.Domain;
import org.jacop.core.IntVar;
import org.jacop.core.Store;
import org.jacop.floats.constraints.ElementFloat;
import org.jacop.floats.constraints.LinearFloat;
import org.jacop.floats.constraints.PltC;
import org.jacop.floats.constraints.XeqP;
import org.jacop.floats.core.FloatVar;
import org.jacop.floats.search.SmallestDomainFloat;
import org.jacop.floats.search.SplitSelectFloat;
import org.jacop.search.*;
import utils.Global;

import java.util.*;
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

        Column candidate = buildModel(testDual, vehicleDual);
        if (null==candidate)
            return new ArrayList<>();
        else
            return Collections.singletonList(candidate);
    }

    @Override
    public double getReducedCost() {
        return reducedCost;
    }

    private Column buildModel(Map<Integer, Double> testDual, Map<Integer, Double> vehicleDual) {
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
        final int[] prepArr = Arrays.copyOf(DataInstance.getInstance().getTestArr().stream()
                .mapToInt(TestRequest::getPrep).toArray(), numTests+1); // dummy test

//        final int[] prepArr = Arrays.copyOf(DataInstance.getInstance().getTestArr().stream()
//                .mapToInt(TestRequest::getPrep).toArray(), numTests+1); // dummy test
        final int[] tidArr = DataInstance.getInstance().getTidList().stream().mapToInt(Integer::intValue).toArray();
        final int[] testReleaseArr = Arrays.copyOf(DataInstance.getInstance().getTestArr()
                .stream().mapToInt(TestRequest::getRelease).toArray(), numTests+1);

        final int[] deadlineArr = Arrays.copyOf(DataInstance.getInstance().getTestArr().stream()
                .mapToInt(TestRequest::getDeadline).toArray(), numTests+1);
        deadlineArr[deadlineArr.length-1] = horizonEnd;
        final double[] testDualArr = Arrays.copyOf(
                DataInstance.getInstance().getTidList().stream()
                        .mapToDouble(e -> testDual.get(e)*-1) //.mapToInt(e -> (int) Math.round(e)) // reverted the sign
                        .toArray(),
                numTests+1);
        final double[] vehicleDualArr = DataInstance.getInstance().getVehicleReleaseList().stream()
                .mapToDouble(e -> vehicleDual.get(e)*-1)//.mapToInt(e -> (int) Math.round(e)) // reverted the sign
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
        model.impose(new Element(selectVehicle, releaseArr, vehicleReleaseVar, -1));

        IntVar[] durAtPosition = new IntVar[numSlots];
//        IntVar[] prepAtPosition = new IntVar[numSlots];
        IntVar[] testReleaseAtPosition = new IntVar[numSlots];
        IntVar[] prepDurAtPosition = new IntVar[numSlots];
        IntVar[] deadlineAtPosition = new IntVar[numSlots];
        IntVar[] occurenceOfTest = new IntVar[numTests];

        for (int p = 0; p < numSlots; p++) {
            durAtPosition[p] = new IntVar(model, 0, horizonEnd);
            model.impose(new Element(testAtPosition[p], durArr, durAtPosition[p], -1));

//            prepAtPosition[p] = new IntVar(model);
//            model.impose(new Element(testAtPosition[p], prepArr, prepAtPosition[p], 1));

            testReleaseAtPosition[p] = new IntVar(model, horizonStart, horizonEnd);
            model.impose(new Element(testAtPosition[p], testReleaseArr, testReleaseAtPosition[p], -1));

            deadlineAtPosition[p] = new IntVar(model, horizonStart, horizonEnd);
            model.impose(new Element(testAtPosition[p], deadlineArr, deadlineAtPosition[p], -1));

            prepDurAtPosition[p] = new IntVar(model, 0, Arrays.stream(durArr).max().getAsInt());
            model.impose(new Element(testAtPosition[p], prepArr, prepDurAtPosition[p], -1));
        }

        for (int t = 0; t < numTests; t++) {
            occurenceOfTest[t] = new IntVar(model, 0, numSlots);
            model.impose(new Count(testAtPosition, occurenceOfTest[t], t));
        }


        // constraints
        // start after the selected vehicle is released
        model.impose(new XgteqY(startTimeAtPosition[0], vehicleReleaseVar));

        // release time of tests, need fix
        for (int p = 0; p < numSlots; p++) {
            IntVar tatStart = new IntVar(model, horizonStart, horizonEnd);
            model.impose(new XplusYeqZ(startTimeAtPosition[p], prepDurAtPosition[p], tatStart));
//            model.impose(new XgteqY(startTimeAtPosition[p], testReleaseAtPosition[p]));
            model.impose(new XgteqY(tatStart, testReleaseAtPosition[p]));
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

        // start time between two positions
        for (int p = 0; p < numSlots-1; p++) {
            model.impose(new XplusYlteqZ(startTimeAtPosition[p],
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
                                    new XeqC(occurenceOfTest[j], 1))
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
        // convert to float
        FloatVar totalTardinessFloat = new FloatVar(model, 0, horizonEnd*numSlots);
        model.impose(new XeqP(totalTardiness, totalTardinessFloat));

//
        // vehicle contribution
//        IntVar vehicleContrib = new IntVar(model, Arrays.stream(vehicleDualArr).min().getAsInt(),
//                Arrays.stream(vehicleDualArr).max().getAsInt());
//        model.impose(new Element(selectVehicle, vehicleDualArr, vehicleContrib, -1));
        // convert to float
        FloatVar vehicleContrib = new FloatVar(model, Arrays.stream(vehicleDualArr).min().getAsDouble(),
                Arrays.stream(vehicleDualArr).max().getAsDouble());
        model.impose(new ElementFloat(selectVehicle, vehicleDualArr, vehicleContrib, -1));

//
        // test contribution
//        IntVar[] testContribAtPosition = new IntVar[numSlots];
//        for (int p = 0; p < numSlots; p++) {
//            testContribAtPosition[p] = new IntVar(model, Arrays.stream(testDualArr).min().getAsInt(),
//                    Arrays.stream(testDualArr).max().getAsInt());
//            model.impose(new Element(testAtPosition[p], testDualArr, testContribAtPosition[p], -1));
//        }
//        IntVar totalTestContrib = new IntVar(model, Arrays.stream(testDualArr).sum(), 0);
//        model.impose(new SumInt(model, testContribAtPosition, "==", totalTestContrib));
        FloatVar[] testContribAtPosition = new FloatVar[numSlots];
        for (int p = 0; p < numSlots; p++) {
            testContribAtPosition[p] = new FloatVar(model, Arrays.stream(testDualArr).min().getAsDouble(),
                    Arrays.stream(testDualArr).max().getAsDouble());
//            model.impose(new ElementFloat(testAtPosition[p], testDualArr, testContribAtPosition[p], -1));
        }
        model.impose(new ElementFloat(testAtPosition[0], testDualArr, testContribAtPosition[0], -1));
//        FloatVar totalTestContrib = new FloatVar(model, Arrays.stream(testDualArr).sum(), 0);
//        double[] weights = new double[numSlots+1];
//        Arrays.fill(weights,1.0);
//        weights[weights.length-1] = -1.0;
//        FloatVar[] vars = new FloatVar[numSlots+1];
//        for (int p = 0; p < numSlots; p++) {
//            vars[p] = testContribAtPosition[p];
//        }
//        vars[vars.length-1] = totalTestContrib;
//        model.impose(new LinearFloat(model, vars, weights, "==", 0));

        // reduced cost = VEHICLE COST + total tardiness - test contribution - vehicle contribution
//        IntVar rc = new IntVar(model, -1000, 1000);
//        model.impose(new XplusYplusQeqZ(totalTardiness, vehicleContrib, totalTestContrib, rc));
//        model.impose(new XltC(rc, (int)-Global.VEHICLE_COST));
//        FloatVar rc = new FloatVar(model, -1000, 1000);
//        model.impose(new LinearFloat(model, new FloatVar[]{totalTardinessFloat, totalTestContrib, vehicleContrib, rc},
//                new double[]{1,1,1,-1}, "==", 0));
//        model.impose(new PltC(rc, -Global.VEHICLE_COST));

        // searching strategy

        // float variable search
        DepthFirstSearch<FloatVar> floatVarNailing = new DepthFirstSearch<>();
        FloatVar[] floatVars = {totalTardinessFloat, vehicleContrib, testContribAtPosition[0],testContribAtPosition[1],testContribAtPosition[2],testContribAtPosition[3] }; //, totalTestContrib, rc};
        SplitSelectFloat<FloatVar> floatVarSelectChoicePoint = new SplitSelectFloat<>(model, floatVars,
                null);
        floatVarNailing.setSelectChoicePoint(floatVarSelectChoicePoint);
        floatVarNailing.getSolutionListener().recordSolutions(true);

        // start time & aux variable search
        Search<IntVar> timeAssignment = new DepthFirstSearch<>();
        IntVar[] timeVars = new IntVar[1+numSlots*2];
        timeVars[0] = selectVehicle;
        for (int p = 0; p < numSlots; p++) {
            timeVars[1+p] = startTimeAtPosition[p];
            timeVars[1+numSlots+p] = tardinessAtPosition[p];
        }

        SelectChoicePoint<IntVar> timeSearch = new InputOrderSelect<>(model, timeVars,
                new IndomainMin<>()
                );
        timeAssignment.addChildSearch(floatVarNailing);
        timeAssignment.setSelectChoicePoint(timeSearch);
        timeAssignment.getSolutionListener().recordSolutions(true);


        // test assignment search
        Search<IntVar> testAssignment = new DepthFirstSearch<>();
        SelectChoicePoint<IntVar> testSearch = new InputOrderSelect<>(model, testAtPosition,
                new IndomainMin<>());
        testAssignment.addChildSearch(timeAssignment);
        testAssignment.getSolutionListener().recordSolutions(true);



        boolean result = testAssignment.labeling(model, testSearch);
//        boolean result = floatVarNailing.labeling(model, floatVarSelectChoicePoint);

        // parse the solution

        List<Integer> seq = new ArrayList<>();
        int vehicleReleaseSelect;

        if (result) {
            assert testAssignment.getSolution().length==numSlots;
            for (int i = 0; i < testAssignment.getSolution().length; i++) {
                Domain val = testAssignment.getSolution()[i];
                assert val.singleton(); // nailed down the value
                int testIdx = val.valueEnumeration().nextElement();
                if (testIdx != numTests)
                    seq.add(tidArr[testIdx]);
            }

            // vehicle selection
            assert timeAssignment.getSolution().length>0;
            Domain val = timeAssignment.getSolution()[0];
            assert val.singleton();
            int vehicleIdx = val.valueEnumeration().nextElement();

            // reduced cost
            val = timeAssignment.getSolution()[timeAssignment.getSolution().length-1];
            assert val.singleton();
            this.reducedCost = val.valueEnumeration().nextElement() + Global.VEHICLE_COST;

            vehicleReleaseSelect = releaseArr[vehicleIdx];

            // create the column
            Column col = new Column(seq, vehicleReleaseSelect);
            System.out.println("totalTardinessFloat = " + totalTardinessFloat);
            System.out.println("col.getCost() = " + col.getCost());
            System.out.println("totalTardiness = " + totalTardiness);
            System.out.println("this.reducedCost = " + this.reducedCost);
            System.out.println("EnumPricer.reducedCost(col, testDual, vehice) = " + EnumPricer.reducedCost(col, testDual, vehicleDual));
            return col;
        } else {
            // infeasible
            this.reducedCost = Double.MAX_VALUE;
            return null;
        }


    }
}
