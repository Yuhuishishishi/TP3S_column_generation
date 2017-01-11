package algorithm.pricer;

import algorithm.Column;
import algorithm.ColumnGeneration;
import data.DataInstance;
import data.TestRequest;
import facility.ColumnWithTiming;
import utils.Global;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
public class SequenceThenTimePricerFacility implements PricerFacility {

    // TODO: need to fix the offset problem

    private String instID;
    private double lastReducedCost;
    private List<List<Integer>> seqPool;
    private int offset;

    public SequenceThenTimePricerFacility(String instID) {
        this.instID = instID;
        this.lastReducedCost = Double.MAX_VALUE;
        initSeqPool();

        offset = DataInstance.getInstance(instID).getHorizonStart();
    }

    public SequenceThenTimePricerFacility() {
        this.instID = DataInstance.getInstance().getInstID();
        this.lastReducedCost = Double.MAX_VALUE;
        initSeqPool();

        offset = DataInstance.getInstance(instID).getHorizonStart();
    }

    @Override
    public List<ColumnWithTiming> price(
            Map<Integer, Double> testDual, Map<Integer, Double> vehicleDual, Map<Integer, Double> dayDual) {

        // lazy initialization of seq pool
        if (null == seqPool)
            initSeqPool();

        return genFullSchedule(testDual, vehicleDual, dayDual);
//            return genAllNeg(testDual, vehicleDual, dayDual);

    }

    private List<ColumnWithTiming> genFullSchedule(Map<Integer, Double> testDual,
                                                   Map<Integer, Double> vehicleDual,
                                                   Map<Integer, Double> dayDual) {

        return genFullSchedule(testDual, vehicleDual, dayDual, false);
    }



    // heuristic to generate a full schedule to cover all tests
    private List<ColumnWithTiming> genFullSchedule(Map<Integer, Double> testDual,
                                                   Map<Integer, Double> vehicleDual,
                                                   Map<Integer, Double> dayDual, boolean neglectNegativeReducedCost) {
        List<ColumnWithTiming> colList = new ArrayList<>();

        Map<Integer, Boolean> testCovered = new HashMap<>();
        DataInstance.getInstance(instID).getTidList()
                .forEach(tid->testCovered.put(tid, false));

        // sort the seq pool
        List<ColumnWithTiming> sortedCol = sortCols(testDual, vehicleDual, dayDual);

        lastReducedCost = CPOPricerFacility.reducedCost(sortedCol.get(0), testDual,
                vehicleDual, dayDual);

        for (ColumnWithTiming mostNeg : sortedCol) {
            // pick the most negative one

            if (CPOPricerFacility.reducedCost(mostNeg, testDual, vehicleDual, dayDual) > -0.001 && !neglectNegativeReducedCost)
                break;

            // if tests covered
            if (mostNeg.getSeq().stream().anyMatch(testCovered::get))
                continue;

            // create column with timing
            colList.add(mostNeg);

            // mark tests as covered
            mostNeg.getSeq().forEach(tid-> testCovered.put(tid, true));

            if (!testCovered.values().contains(false))
                break;
        }

        return colList;
    }


    @Override
    public double getReducedCost() {
        return this.lastReducedCost;
    }

    @Override
    public void end() {

    }

    private void initSeqPool() {
        System.out.println("Initializing the column pool in the pricer ... ");
        List<Column> colPool = ColumnGeneration.enumInitCol(instID, Global.MAX_HITS);
        Set<List<Integer>> seqSet = colPool.stream().map(Column::getSeq).collect(Collectors.toSet());
        this.seqPool = new ArrayList<>(seqSet);
    }

    private double colReducedCost(List<Integer> seq,
                                  Map<Integer, Double> testDual, Map<Integer, Double> vehicleDual, Map<Integer, Double> dayDual
    ) {
        // calculate the best reduced cost
        // decision - vehicle release day selection
        //          - time for different tests
//        double reducedCost = Global.VEHICLE_COST;
//        double[][] valueFunction = optimalTimeFinding(seq, dayDual);
//        // test composition
//        for (Integer aSeq : seq) {
//            reducedCost -= testDual.get(aSeq);
//        }
//        // find the smallest vehicle
//        OptionalDouble minVehicleResourceCost = vehicleDual.keySet().stream()
//                .mapToDouble(release -> -vehicleDual.get(release) + valueFunction[0][release])
//                .min();
//        assert minVehicleResourceCost.isPresent();
//        reducedCost += minVehicleResourceCost.getAsDouble();
//        return reducedCost;
        return CPOPricerFacility.reducedCost(bestTimedColGivenSeq(seq, vehicleDual, dayDual),
                testDual, vehicleDual, dayDual);

    }

    private ColumnWithTiming bestTimedColGivenSeq(List<Integer> seq,
                                                  Map<Integer, Double> vehicleDual,
                                                  Map<Integer, Double> dayDual) {
        double[][] valueFunction = optimalTimeFinding(seq, dayDual);
        int[] result = backTractStartTime(seq, vehicleDual, dayDual, valueFunction);
        int vehicleRelease = result[0];

        final int numHorizon = DataInstance.getInstance(instID).getHorizonEnd()
                -DataInstance.getInstance(instID).getHorizonStart();
        double partialReducedCost = 0;

            partialReducedCost = valueFunction[0][vehicleRelease - offset] - vehicleDual.get(vehicleRelease);



        if (valueFunction[0][vehicleRelease - offset] > 9*numHorizon) // infeasible
            return null;

        Map<Integer, Integer> startTime = new HashMap<>();
        for (int i = 1; i < result.length; i++) {
            startTime.put(seq.get(i-1), result[i] + offset);
        }

        ColumnWithTiming bestCol = new ColumnWithTiming(instID, seq, vehicleRelease, startTime);
        double partialReducedCostTheory = bestCol.getCost() - vehicleDual.get(bestCol.getRelease()) - bestCol.daysHasCrash()
                .stream().mapToDouble(dayDual::get).sum();
        try {
            assert Math.abs(partialReducedCost - partialReducedCostTheory) < 0.01;
        } catch (AssertionError err) {
            System.err.println("reduced cost mismatching");
//            System.out.println("partialReducedCostTheory = " + partialReducedCostTheory);
//            System.out.println("partialReducedCost = " + partialReducedCost);
//            System.out.println("Difference: " + (partialReducedCostTheory - partialReducedCost));
        }

        return bestCol;

    }

    private double[][] optimalTimeFinding(List<Integer> seq,
                                          Map<Integer, Double> dayDual
    ) {
        // find the optimal time setting for a sequence, given the dual value
        final int numTest = seq.size();
        final int numHorizon = DataInstance.getInstance(instID).getHorizonEnd()
                - DataInstance.getInstance(instID).getHorizonStart() + 1;
        List<TestRequest> tests = seq.stream().map(tid -> DataInstance.getInstance(instID).getTestById(tid))
                .collect(Collectors.toList());

        double[][] valueFunction = new double[numTest][numHorizon];
        int durCum = 0;
        for (int i = numTest - 1; i >= 0; i--) {
            TestRequest test = tests.get(i);
            durCum += test.getDur();
            for (int d = numHorizon - 1; d >= 0; d--) {
                if (d + durCum > numHorizon)
                    valueFunction[i][d] = (double) numHorizon*10;
                else if (d < test.getRelease() - offset)
                    valueFunction[i][d] = (double) numHorizon*10;
                else {
                    int tardiness = Math.max(d  + test.getDur() - test.getDeadline() + offset, 0);
                    double resourceCost = 0;
                    int tatStart = d  + test.getPrep();
                    int tatEnd = d  + test.getPrep() + test.getTat();
                    for (int j = tatStart; j < tatEnd; j++) {
                        assert dayDual.containsKey(j + offset);
                        resourceCost += dayDual.get(j + offset);
                    }

                    if (i == numTest - 1)
                        valueFunction[i][d] = Math.min(tardiness - resourceCost, valueFunction[i][d + 1]);
                    else {
                        valueFunction[i][d] = Math.min(tardiness - resourceCost + valueFunction[i + 1][d + test.getDur()],
                                valueFunction[i][d + 1]);
                    }
                }
            }
        }
        return valueFunction;
    }

    private int[] backTractStartTime(List<Integer> seq,
                                     Map<Integer, Double> vehicleDual,
                                     Map<Integer, Double> dayDual,
                                     double[][] valueFunction) {

        // which vehicle to pair
        int[] result = new int[seq.size() + 1];
        Optional<Integer> bestRelease = vehicleDual.keySet().stream()
                .reduce((r1, r2) -> -vehicleDual.get(r1) + valueFunction[0][r1-offset] < -vehicleDual.get(r2) + valueFunction[0][r2-offset] ?
                        r1 : r2);
        assert bestRelease.isPresent();
        result[0] = bestRelease.get();

        int searchStart = result[0] - offset;
//        for (int i = 0; i < seq.size(); i++) {
//            double target = valueFunction[i][searchStart];
//            TestRequest test = tests.get(i);
//            for (int d = searchStart; d < numHorizon - test.getDur(); d++) {
//                int tardiness = Math.max(d + test.getDur() - test.getDeadline(), 0);
//                double resourceCost = 0;
//                int tatStart = d + test.getPrep();
//                int tatEnd = d + test.getPrep() + test.getTat();
//                for (int j = tatStart; j < tatEnd; j++) {
//                    assert dayDual.containsKey(j);
//                    resourceCost += dayDual.get(j);
//                }
//                if ((i != seq.size() - 1 && tardiness - resourceCost + valueFunction[i + 1][d + test.getDur()] == target)
//                        || (i == seq.size() - 1 && tardiness - resourceCost == target)) {
//                    result[i + 1] = d;
//                    searchStart = d + test.getDur();
//                    break;
//                }
//            }
//        }
        int[] timeResult = bestTimeGivenSeqAndVehicle(seq, dayDual, valueFunction, searchStart);
        System.arraycopy(timeResult, 0, result, 1, timeResult.length);

        return result;
    }

    private int[] bestTimeGivenSeqAndVehicle(final List<Integer> seq,
                                             final Map<Integer, Double> dayDual,
                                             final double[][] valueFunction,
                                             int searchStart) {
        int[] result = new int[seq.size()];
        List<TestRequest> tests = seq.stream().map(tid -> DataInstance.getInstance(instID).getTestById(tid))
                .collect(Collectors.toList());
        final int numHorizon = DataInstance.getInstance(instID).getHorizonEnd()
                - DataInstance.getInstance(instID).getHorizonStart() + 1;


        for (int i = 0; i < seq.size(); i++) {
            double target = valueFunction[i][searchStart];
            TestRequest test = tests.get(i);
            for (int d = searchStart; d < numHorizon - test.getDur(); d++) {
                int tardiness = Math.max(d  + test.getDur() - test.getDeadline() + offset, 0);
                double resourceCost = 0;
                int tatStart = d  + test.getPrep();
                int tatEnd = d  + test.getPrep() + test.getTat();
                for (int j = tatStart; j < tatEnd; j++) {
                    assert dayDual.containsKey(j + offset);
                    resourceCost += dayDual.get(j + offset);
                }
                if ((i != seq.size() - 1 && tardiness - resourceCost + valueFunction[i + 1][d + test.getDur()] == target)
                        || (i == seq.size() - 1 && tardiness - resourceCost == target)) {
                    result[i] = d;
                    searchStart = d + test.getDur();
                    break;
                }
            }
        }
        return result;
    }

    public List<ColumnWithTiming> createMultipleVehicleVersion(ColumnWithTiming col,
                                                               Map<Integer, Double> dayDual) {
        List<Integer> seq = col.getSeq();
        double[][] valueFunc = optimalTimeFinding(seq, dayDual);

        return DataInstance.getInstance(instID).getVehicleReleaseList().stream()
                .map(release->{
                    int[] timeResult = bestTimeGivenSeqAndVehicle(seq, dayDual, valueFunc, release-offset);
                    Map<Integer, Integer> startTime = new HashMap<>();
                    assert timeResult.length==seq.size();
                    for (int i = 0; i < timeResult.length; i++) {
                        startTime.put(seq.get(i), timeResult[i]);
                    }
                    return new ColumnWithTiming(col.getInstID(), seq, release, startTime);
                }).collect(Collectors.toList());
    }

    public List<ColumnWithTiming> primalHeuristic(Map<Integer, Double> testDual,
                                                  Map<Integer, Double> vehicleDual,
                                                  Map<Integer, Double> dayDual) {
        // solve for a feasible integer solution using heuristics
        Map<Integer, Boolean> testCovered = new HashMap<>();
        Map<Integer, Integer> vehicleCapacity = new HashMap<>();
        Map<Integer, Integer> resourceRemaining = new HashMap<>();

        DataInstance inst = DataInstance.getInstance(instID);
        testDual.keySet().forEach(tid->testCovered.put(tid, false));
        vehicleDual.keySet().forEach(release->vehicleCapacity.put(release, inst.numVehiclesByRelease(release)));
        dayDual.keySet().forEach(day->resourceRemaining.put(day, Global.FACILITY_CAP));



        return null;

    }

    private List<ColumnWithTiming> sortCols(Map<Integer,Double> testDual,
                                            Map<Integer, Double> vehicleDual,
                                            Map<Integer, Double> dayDual) {
        return seqPool.stream().parallel()
                .map(seq -> bestTimedColGivenSeq(seq, vehicleDual, dayDual)).sequential()
                .sorted((c1, c2) -> {
                    if (CPOPricerFacility.reducedCost(c1, testDual, vehicleDual, dayDual) <
                            CPOPricerFacility.reducedCost(c2, testDual, vehicleDual, dayDual))
                        return -1;
                    else if (CPOPricerFacility.reducedCost(c1, testDual, vehicleDual, dayDual) >
                            CPOPricerFacility.reducedCost(c2, testDual, vehicleDual, dayDual))
                        return 1;
                    else
                        return 0;
                }).collect(Collectors.toList());
    }
}
