package algorithm.pricer;


import data.DataInstance;
import data.TestRequest;
import facility.ColumnWithTiming;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloNumExpr;
import ilog.cp.IloCP;
import utils.Global;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
public class CPOPricerFacility implements PricerFacility {

    private double reducedCost;

    public CPOPricerFacility() {
        this.reducedCost = Double.MAX_VALUE;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public List<ColumnWithTiming> price(Map<Integer, Double> testDual, Map<Integer, Double> vehicleDual, Map<Integer, Double> dayDual) {
        List<ColumnWithTiming> candidates = new ArrayList<>();

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

//        final int[] prepArr = Arrays.copyOf(DataInstance.getInstance().getTestArr().stream()
//                .mapToInt(TestRequest::getPrep).toArray(), numTests+1); // dummy test
        final int[] tidArr = DataInstance.getInstance().getTidList().stream().mapToInt(Integer::intValue).toArray();
        final int[] testReleaseArr = Arrays.copyOf(DataInstance.getInstance().getTestArr()
                .stream().mapToInt(TestRequest::getRelease).toArray(), numTests + 1);

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
        Map<Integer, Double> negDayDual = new HashMap<>();
        dayDual.keySet().forEach(e -> negDayDual.put(e, -dayDual.get(e)));

        try {

            // variables
            IloCP model = new IloCP();
            IloIntVar[] testAtPosition = new IloIntVar[numSlots];
            IloIntVar[] startTimeAtPosition = new IloIntVar[numSlots];
            IloIntVar selectVehicle = model.intVar(0, numVehicles - 1);
            // initialization
            for (int p = 0; p < numSlots; p++) {
                testAtPosition[p] = model.intVar(0, numTests, "test_at_position");
                startTimeAtPosition[p] = model.intVar(horizonStart, horizonEnd, "starttime_at_position");
            }

            // aux variables
            IloIntVar[] durAtPosition = new IloIntVar[numSlots];
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

            // day dual contribution
            final int numDays = horizonEnd - horizonStart + 1;
            // precompute the day dual contribution matrix
            double[] dayContrib = new double[(numTests + 1) * numDays]; // dummy test
            for (int t = 0; t < numTests; t++) {
                int tid = tidArr[t];
                TestRequest test = DataInstance.getInstance().getTestById(tid);
                for (int d = 0; d < numDays; d++) {
                    int start = d + horizonStart;
                    int tatStart = start + test.getPrep();
                    int tatEnd = tatStart + test.getTat();
                    // add all duals from tatStart -> tatEnd
                    final double[] totalDual = {0};
                    IntStream.range(tatStart, tatEnd).forEach(e ->
                            totalDual[0] += negDayDual.getOrDefault(e, 0.0)
                    );
                    dayContrib[t * numDays + d] = totalDual[0];
                }
            }
            for (int p = 0; p < numSlots; p++) {
                reducedCostExpr = model.sum(reducedCostExpr,
                        model.element(dayContrib,
                                model.sum(model.diff(startTimeAtPosition[p], horizonStart),
                                        model.prod(testAtPosition[p], numDays))));
            }

            model.addLe(reducedCostExpr, -0.001);

            model.setOut(null);

            // solve the problem
            if (model.solve()) {
                // parse the solution
                this.reducedCost = model.getValue(reducedCostExpr);
                List<Integer> seq = new ArrayList<>();
                Map<Integer, Integer> startTimeMap = new HashMap<>();
                int vehicleIdx = (int) Math.round(model.getValue(selectVehicle));
                int colRelease = releaseArr[vehicleIdx];
                for (int p = 0; p < numSlots; p++) {
                    int tidIdx = (int) Math.round(model.getValue(testAtPosition[p]));
                    if (tidIdx != numTests) {
                        seq.add(tidArr[tidIdx]);
                        startTimeMap.put(tidArr[tidIdx],
                                (int) Math.round(model.getValue(startTimeAtPosition[p])));

//                        System.out.println("test at position p " + tidArr[tidIdx]);
//                        System.out.println("star time at position p " + Math.round(model.getValue(startTimeAtPosition[p])));
//                        System.out.println("dur " + durArr[tidIdx] + " prep " + prepArr[tidIdx] + " tat " +
//                                DataInstance.getInstance().getTestById(tidArr[tidIdx]).getTat());
//                        System.out.println("deadline " + deadlineArr[tidIdx]);
//                        System.out.println("tardiness" +
//                                model.getValue(model.max(0,
//                                        model.diff(model.sum(startTimeAtPosition[p], durAtPosition[p]),
//                                                model.element(deadlineArr, testAtPosition[p])))));
//
//                        System.out.println("day dual contrib: " + model.getValue(
//                                model.element(dayContrib,
//                                        model.sum(model.diff(startTimeAtPosition[p], horizonStart),
//                                                model.prod(testAtPosition[p], numDays))))
//                        );
//                        int tatStart = (int) Math.round(model.getValue(startTimeAtPosition[p])) + prepArr[tidIdx];
//                        int tatEnd = tatStart + DataInstance.getInstance().getTestById(tidArr[tidIdx]).getTat();
//                        double realContrib = 0;
//                        for (int i = tatStart; i < tatEnd; i++) {
//                            realContrib += dayDual.get(i);
//                            System.out.println("day " + i + "=" + dayDual.get(i));
//                        }
//                        System.out.println("read contrib " + realContrib);
//                        System.out.println("compute contrib " + dayContrib[tidIdx*numDays + ((int) Math.round(model.getValue(startTimeAtPosition[p]) - horizonStart))]);

                    }
                }
                ColumnWithTiming newCol = new ColumnWithTiming(seq, colRelease,
                        startTimeMap);
                candidates.add(newCol);

//                System.out.println("reducedCost = " + reducedCost(newCol,
//                        testDual,vehicleDual,dayDual));
//                System.out.println("this.reducedCost = " + this.reducedCost);
            } else {
                this.reducedCost = Double.MAX_VALUE;
            }

            model.end();

        } catch (IloException e) {
            e.printStackTrace();
        }

        return candidates;
    }

    private static double reducedCost(ColumnWithTiming col, Map<Integer, Double> testDual,
                                      Map<Integer, Double> vehicleDual,
                                      Map<Integer, Double> dayDual) {
        double reducedCostWithoutDayDual =
                EnumPricer.reducedCost(col, testDual, vehicleDual);
        // facility duals
        List<Integer> daysUsingFacility = col.daysHasCrash();
        final double[] dayContrib = {0};
        daysUsingFacility.forEach(e -> dayContrib[0] += dayDual.get(e));
        return reducedCostWithoutDayDual - dayContrib[0];
    }

    @Override
    public double getReducedCost() {
        return this.reducedCost;
    }
}
