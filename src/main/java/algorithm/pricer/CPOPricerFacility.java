package algorithm.pricer;


import algorithm.Column;
import data.DataInstance;
import data.TestRequest;
import facility.ColumnWithTiming;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloNumExpr;
import ilog.concert.IloRange;
import ilog.cp.IloCP;
import ilog.cp.IloSearchPhase;
import utils.Global;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
@SuppressWarnings("Duplicates")
public class CPOPricerFacility implements PricerFacility {

    private double reducedCost;
    private final Pricer firstStagePricer;
    private IloCP solver;
    private IloIntVar[] testAtPosition;
    private IloIntVar[] startTimeAtPosition;
    private IloIntVar selectVehicle;
    private IloRange negReducedCostConstr;

    public CPOPricerFacility() {
        this.reducedCost = Double.MAX_VALUE;
        this.firstStagePricer = new EnumPricer();
        this.solver = null;
    }

    private List<ColumnWithTiming> firstStagePrice(Map<Integer, Double> testDual,
                                                   Map<Integer, Double> vehicleDual,
                                                   Map<Integer, Double> dayDual) {
        // 1st stage pricer
        List<Column> firstStageCandidates = this.firstStagePricer.price(testDual, vehicleDual);
        System.out.println("Pricing lb: " + this.firstStagePricer.getReducedCost());
        if (firstStageCandidates.size() == 0) {
            System.out.println("Pricing relaxation problem is infeasible.");
            return null; // no solution to the relaxation, terminate
        }
        else {
            Column candidate = firstStageCandidates.get(0);
            ColumnWithTiming candidateWithTiming = new ColumnWithTiming(candidate.getSeq(), candidate.getRelease());

            List<ColumnWithTiming> otherVersions = createMultipleVersion(candidateWithTiming);
            // check if any of them negative reduced cost
            List<ColumnWithTiming> usefulCols = otherVersions.stream()
                    .filter(c -> reducedCost(c, testDual, vehicleDual, dayDual) < -0.001)
                    .collect(Collectors.toList());
            if (usefulCols.size()>0) {
                // update the reduced cost
                this.reducedCost = usefulCols.stream().mapToDouble(c->EnumPricer.reducedCost(c,testDual,vehicleDual))
                        .min().getAsDouble();
                System.out.println("Pricing heuristic find " + usefulCols.size() + " cols");
                return usefulCols;
            }
        }
        // build the timing decisions
//        Column candidate = firstStageCandidates.get(0);
//
//        int[] tidArr = candidate.getSeq().stream().mapToInt(Integer::intValue).toArray();
//        int[] prepArr = candidate.getSeq().stream()
//                .mapToInt(tid -> DataInstance.getInstance().getTestById(tid).getPrep())
//                .toArray();
//        int[] durArr = candidate.getSeq().stream()
//                .mapToInt(tid -> DataInstance.getInstance().getTestById(tid).getDur())
//                .toArray();
//        int[] deadlineArr = candidate.getSeq().stream()
//                .mapToInt(tid -> DataInstance.getInstance().getTestById(tid).getDeadline())
//                .toArray();
//        int[] testReleaseArr = candidate.getSeq().stream()
//                .mapToInt(tid -> DataInstance.getInstance().getTestById(tid).getRelease())
//                .toArray();
//        Map<Integer, Double> negDayDual = new HashMap<>();
//        dayDual.keySet().forEach(e -> negDayDual.put(e, -dayDual.get(e)));
//
//        final int numTests = candidate.getSeq().size();
//        final int horizonStart = DataInstance.getInstance().getHorizonStart();
//        final int horizonEnd = DataInstance.getInstance().getHorizonEnd();
//        final int numDays = horizonEnd - horizonStart + 1;
//        // precompute the day dual contribution matrix
//        double[] dayContrib = new double[numTests * numDays]; // dummy test
//        for (int t = 0; t < numTests; t++) {
//            int tid = tidArr[t];
//            TestRequest test = DataInstance.getInstance().getTestById(tid);
//            for (int d = 0; d < numDays; d++) {
//                int start = d + horizonStart;
//                int tatStart = start + test.getPrep();
//                int tatEnd = tatStart + test.getTat();
//                // add all duals from tatStart -> tatEnd
//                final double[] totalDual = {0};
//                IntStream.range(tatStart, tatEnd).forEach(e ->
//                        totalDual[0] += negDayDual.getOrDefault(e, 0.0)
//                );
//                dayContrib[t * numDays + d] = totalDual[0];
//            }
//        }
//
//
//        IloCP timeProb = new IloCP();
//
//        try {
//            IloIntVar[] startTimeAtPosition = timeProb.intVarArray(numTests,
//                    DataInstance.getInstance().getHorizonStart(),
//                    DataInstance.getInstance().getHorizonEnd());
//
//            // constraints
//            // start after the selected vehicle is released
//            timeProb.addGe(startTimeAtPosition[0], candidate.getRelease());
//
//            // release time of tests
//            for (int p = 0; p < numTests; p++) {
//                timeProb.addGe(timeProb.sum(startTimeAtPosition[p], prepArr[p]), testReleaseArr[p]);
//            }
//
//            // start time between two positions
//            for (int p = 0; p < numTests - 1; p++) {
//                timeProb.addGe(startTimeAtPosition[p + 1],
//                        timeProb.sum(startTimeAtPosition[p], durArr[p]));
//            }
//
//            // minimize reduced cost
//            IloNumExpr reducedCostExpr = timeProb.numExpr();
//            // constants
//            reducedCostExpr = timeProb.sum(Global.VEHICLE_COST
//                            - vehicleDual.get(candidate.getRelease())
//                            - candidate.getSeq().stream().mapToDouble(testDual::get).reduce((i, j) -> (i + j)).getAsDouble(),
//                    reducedCostExpr);
//            // tardiness
//            IloNumExpr totalTardiness = timeProb.numExpr();
//            IloNumExpr totalDayContrib = timeProb.numExpr();
//            for (int p = 0; p < numTests; p++) {
//                // tardiness
//                IloIntVar tardiness = timeProb.intVar(0, horizonEnd);
//                timeProb.addEq(tardiness,
//                        timeProb.max(0,
//                                timeProb.diff(timeProb.sum(startTimeAtPosition[p], durArr[p]),
//                                        deadlineArr[p])));
////                reducedCostExpr = timeProb.sum(reducedCostExpr, tardiness);
//                totalTardiness = timeProb.sum(totalTardiness, tardiness);
//
//                // day contribution
//                totalDayContrib = timeProb.sum(totalDayContrib,
//                        timeProb.element(dayContrib, timeProb.sum(p * numDays,
//                                timeProb.diff(startTimeAtPosition[p], horizonStart))));
//            }
//            reducedCostExpr = timeProb.sum(reducedCostExpr, totalTardiness);
//            reducedCostExpr = timeProb.sum(reducedCostExpr, totalDayContrib);
//
//            timeProb.addMinimize(reducedCostExpr);
//            timeProb.setOut(null);
//
//            if (timeProb.solve()) {
//                // parse the solution
//                Map<Integer, Integer> startTimeMap = new HashMap<>();
//                for (int p = 0; p < numTests; p++) {
//                    int time = (int) Math.round(timeProb.getValue(startTimeAtPosition[p]));
//                    startTimeMap.put(
//                            candidate.getSeq().get(p),
//                            time
//                    );
//                }
//                ColumnWithTiming result = new ColumnWithTiming(candidate.getSeq(), candidate.getRelease(),
//                        startTimeMap);
//                System.out.println("1st stage reduced cost = " + reducedCost(result, testDual, vehicleDual, dayDual));
//
//                if (timeProb.getObjValue() < -0.001) {
//                    this.reducedCost = timeProb.getObjValue();
//                    return Collections.singletonList(result);
//                } else {
//                    this.reducedCost = Double.MAX_VALUE;
//                }
//            }
//
//        } catch (IloException e) {
//            e.printStackTrace();
//        } finally {
//            timeProb.end();
//        }

        return new ArrayList<>();
    }

    @Override
    public List<ColumnWithTiming> price(Map<Integer, Double> testDual, Map<Integer, Double> vehicleDual, Map<Integer, Double> dayDual) {

//        List<ColumnWithTiming> firstStageCandidate = firstStagePrice(testDual, vehicleDual, dayDual);
        List<ColumnWithTiming> candidates = new ArrayList<>();

//        if (null == firstStageCandidate)
//            return candidates;
//        else {
//            if (firstStageCandidate.size()>0)
//                return firstStageCandidate;
//
//
//        }
//        else if (firstStageCandidate.size()>0)
//            return firstStageCandidate;

        // parameters
        final int numTests = DataInstance.getInstance().getTestArr().size();
        final int numSlots = Global.MAX_HITS;
        final int horizonStart = DataInstance.getInstance().getHorizonStart();
        final int horizonEnd = DataInstance.getInstance().getHorizonEnd();

        final int[] releaseArr = DataInstance.getInstance().getVehicleReleaseList().stream().mapToInt(Integer::intValue)
                .toArray();
        final int[] tidArr = DataInstance.getInstance().getTidList().stream().mapToInt(Integer::intValue).toArray();
        final int[] durArr = Arrays.copyOf(DataInstance.getInstance().getTestArr().stream()
                .mapToInt(TestRequest::getDur).toArray(), numTests + 1); // dummy test
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


        if (null == this.solver)
            this.solver = buildCPmodel();

        IloCP model = this.solver;

        // negative reduced cost
        try {
            if (null != this.negReducedCostConstr)
                model.remove(this.negReducedCostConstr);

            IloNumExpr reducedCostExpr = model.numExpr();

            reducedCostExpr = model.sum(reducedCostExpr, model.constant(Global.VEHICLE_COST));

            for (int p = 0; p < numSlots; p++) {
                // tardiness
                IloIntVar tardiness = model.intVar(0, horizonEnd);
                model.addEq(tardiness,
                        model.max(0,
                                model.diff(model.sum(startTimeAtPosition[p], model.element(durArr, testAtPosition[p])),
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

            negReducedCostConstr = model.addLe(reducedCostExpr, -0.001);

            model.setOut(null);

            // search strategy
            // find test assignment first
            IloSearchPhase vehicleSelect = model.searchPhase(new IloIntVar[]{selectVehicle});
            IloSearchPhase testPhase = model.searchPhase(testAtPosition,
                    model.intVarChooser(model.selectSmallest(model.varIndex(testAtPosition))),
                    model.intValueChooser(model.selectRandomValue()));
            IloSearchPhase timePhase = model.searchPhase(startTimeAtPosition,
                    model.intVarChooser(model.selectSmallest(model.varIndex(startTimeAtPosition))),
                    model.intValueChooser(model.selectSmallest(model.valueLocalImpact())));
            model.setSearchPhases(new IloSearchPhase[]{testPhase, vehicleSelect, timePhase});

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
                    }
                }
                ColumnWithTiming newCol = new ColumnWithTiming(seq, colRelease,
                        startTimeMap);
                List<ColumnWithTiming> newColOtherVersions = createMultipleVersion(newCol);
//                candidates.add(newCol);
                candidates.addAll(newColOtherVersions);
            } else {
                this.reducedCost = Double.MAX_VALUE;
            }

        } catch (IloException e) {
            e.printStackTrace();
        }

        return candidates;
    }

    public static List<ColumnWithTiming> createMultipleVersion(ColumnWithTiming rawCol) {
        List<ColumnWithTiming> result = new ArrayList<>();
        final int seqLength = rawCol.getSeq().size();
        int[] possibleStartUB = new int[seqLength];
        int[] possibleStartLB = new int[seqLength];
        int[] durArr = rawCol.getSeq().stream()
                .mapToInt(tid->DataInstance.getInstance().getTestById(tid).getDur()).toArray();

        for (int i = 0; i < seqLength; i++) {
            TestRequest test = DataInstance.getInstance().getTestById(rawCol.getSeq().get(i));
            possibleStartLB[i] = rawCol.getStartTimeByTid(rawCol.getSeq().get(i));
            int ub = Math.max(test.getDeadline() - test.getDur(),
                    possibleStartLB[i]);
            possibleStartUB[i] = ub;
        }

        List<List<Integer>> startTimeComb = new ArrayList<>();
        List<List<Integer>> prev, current;
        prev = new ArrayList<>();
        current = new ArrayList<>();
        for (int i = 0; i < seqLength; i++) {
            current.clear();
            for (int e = possibleStartLB[i]; e <= possibleStartUB[i]; e++) {
                if (i==0)
                    current.add(Collections.singletonList(e));
                else {
                    for (List<Integer> time : prev) {
                        List<Integer> newTime = new ArrayList<>(time);
                        newTime.add(e);
                        current.add(newTime);
                    }
                }

            }
            prev.clear();
            prev.addAll(current);
        }
        startTimeComb.addAll(current);

        List<List<Integer>> validTimeComb = startTimeComb.stream()
                .filter(l -> isValidTime(l, durArr)).collect(Collectors.toList());

        for (List<Integer> s : validTimeComb) {
            Map<Integer,Integer> startTimeMap = new HashMap<>();
            for (int i = 0; i < seqLength; i++) {
                startTimeMap.put(rawCol.getSeq().get(i),
                        s.get(i));
            }
            result.add(new ColumnWithTiming(rawCol.getSeq(), rawCol.getRelease(),
                    startTimeMap));
        }

        return result;
    }

    private static boolean isValidTime(List<Integer> startTime, int[] dur) {
        for (int i = 0; i < startTime.size()-1; i++) {
            if (startTime.get(i) + dur[i] > startTime.get(i+1))
                return false;
        }
        return true;
    }

    private IloCP buildCPmodel() {

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

        IloCP model = null;

        try {

            // variables
            model = new IloCP();
            testAtPosition = new IloIntVar[numSlots];
            startTimeAtPosition = new IloIntVar[numSlots];
            selectVehicle = model.intVar(0, numVehicles - 1);
            // initialization
            for (int p = 0; p < numSlots; p++) {
                testAtPosition[p] = model.intVar(0, numTests, "test_at_position");
                startTimeAtPosition[p] = model.intVar(horizonStart, horizonEnd, "starttime_at_position");
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
                        model.sum(startTimeAtPosition[p], model.element(durArr, testAtPosition[p])));
            }

            // if a position is left blank, then the start time is == previous end time
            for (int p = 1; p < numSlots; p++) {
                model.add(model.ifThen(model.eq(testAtPosition[p], numTests),
                        model.eq(startTimeAtPosition[p],
                                model.sum(startTimeAtPosition[p - 1], model.element(durArr, testAtPosition[p-1])))));
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
        } catch (IloException ex) {
            ex.printStackTrace();
        }
        return model;


    }

    public static double reducedCost(ColumnWithTiming col, Map<Integer, Double> testDual,
                                      Map<Integer, Double> vehicleDual,
                                      Map<Integer, Double> dayDual) {
        if (null==col)
            return Double.MAX_VALUE;


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

    @Override
    public void end() {
        this.solver.end();
    }
}
