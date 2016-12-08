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

    private double lastReducedCost;
    private List<List<Integer>> seqPool;

    @Override
    public List<ColumnWithTiming> price(
            Map<Integer, Double> testDual, Map<Integer, Double> vehicleDual, Map<Integer, Double> dayDual) {

        // lazy initialization of seq pool
        if (null == seqPool)
            initSeqPool();

        // find the minimum one
        Optional<List<Integer>> minSeq = this.seqPool.parallelStream()
                .reduce((s1, s2) -> colReducedCost(s1, testDual, vehicleDual, dayDual) < colReducedCost(s2, testDual, vehicleDual, dayDual) ?
                        s1 : s2);
        assert minSeq.isPresent();
        this.lastReducedCost = colReducedCost(minSeq.get(), testDual, vehicleDual, dayDual);

        // negative
        if (lastReducedCost < -0.001) {
            // backtrack to figure out the timing
            double[][] valueFunction = optimalTimeFinding(minSeq.get(), dayDual);
            int[] result = backTractStartTime(minSeq.get(), vehicleDual, dayDual, valueFunction);
            int vehicleRelease = result[0];

            Map<Integer, Integer> startTime = new HashMap<>();
            for (int i = 1; i < result.length; i++) {
                startTime.put(minSeq.get().get(i-1), result[i]);
            }

            ColumnWithTiming col = new ColumnWithTiming(minSeq.get(), vehicleRelease, startTime);

            return new ArrayList<>(Collections.singletonList(col));
        }


        return new ArrayList<>();
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
        List<Column> colPool = ColumnGeneration.enumInitCol(Global.MAX_HITS);
        Set<List<Integer>> seqSet = colPool.stream().map(Column::getSeq).collect(Collectors.toSet());
        this.seqPool = new ArrayList<>(seqSet);
    }

    private double colReducedCost(List<Integer> seq,
                                  Map<Integer, Double> testDual, Map<Integer, Double> vehicleDual, Map<Integer, Double> dayDual
    ) {
        // calculate the best reduced cost
        // decision - vehicle release day selection
        //          - time for different tests
        double reducedCost = Global.VEHICLE_COST;
        double[][] valueFunction = optimalTimeFinding(seq, dayDual);
        // test composition
        for (Integer aSeq : seq) {
            reducedCost -= testDual.get(aSeq);
        }
        // find the smallest vehicle
        OptionalDouble minVehicleResourceCost = vehicleDual.keySet().stream()
                .mapToDouble(release -> -vehicleDual.get(release) + valueFunction[0][release])
                .min();
        assert minVehicleResourceCost.isPresent();
        reducedCost += minVehicleResourceCost.getAsDouble();
        return reducedCost;

    }

    private double[][] optimalTimeFinding(List<Integer> seq,
                                          Map<Integer, Double> dayDual
    ) {
        // find the optimal time setting for a sequence, given the dual value
        final int numTest = seq.size();
        final int numHorizon = DataInstance.getInstance().getHorizonEnd()
                - DataInstance.getInstance().getHorizonStart() + 1;
        List<TestRequest> tests = seq.stream().map(tid -> DataInstance.getInstance().getTestById(tid))
                .collect(Collectors.toList());

        double[][] valueFunction = new double[numTest][numHorizon];
        int durCum = 0;
        for (int i = numTest - 1; i >= 0; i--) {
            TestRequest test = tests.get(i);
            durCum += test.getDur();
            for (int d = numHorizon - 1; d >= 0; d--) {
                if (d + durCum > numHorizon)
                    valueFunction[i][d] = (double) numHorizon*10;
                else {
                    int tardiness = Math.max(d + test.getDur() - test.getDeadline(), 0);
                    double resourceCost = 0;
                    int tatStart = d + test.getPrep();
                    int tatEnd = d + test.getPrep() + test.getTat();
                    for (int j = tatStart; j < tatEnd; j++) {
                        assert dayDual.containsKey(j);
                        resourceCost += dayDual.get(j);
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
        final int numHorizon = DataInstance.getInstance().getHorizonEnd()
                - DataInstance.getInstance().getHorizonStart() + 1;
        List<TestRequest> tests = seq.stream().map(tid -> DataInstance.getInstance().getTestById(tid))
                .collect(Collectors.toList());

        // which vehicle to pair
        int[] result = new int[seq.size() + 1];
        Optional<Integer> bestRelease = vehicleDual.keySet().stream()
                .reduce((r1, r2) -> -vehicleDual.get(r1) + valueFunction[0][r1] < -vehicleDual.get(r2) + valueFunction[0][r2] ?
                        r1 : r2);
        assert bestRelease.isPresent();
        result[0] = bestRelease.get();

        int searchStart = result[0];
        for (int i = 0; i < seq.size(); i++) {
            double target = valueFunction[i][searchStart];
            TestRequest test = tests.get(i);
            for (int d = searchStart; d < numHorizon; d++) {
                int tardiness = Math.max(d + test.getDur() - test.getDeadline(), 0);
                double resourceCost = 0;
                int tatStart = d + test.getPrep();
                int tatEnd = d + test.getPrep() + test.getTat();
                for (int j = tatStart; j < tatEnd; j++) {
                    assert dayDual.containsKey(j);
                    resourceCost += dayDual.get(j);
                }
                if ((i != seq.size() - 1 && tardiness - resourceCost + valueFunction[i + 1][d + test.getDur()] == target)
                        || (i == seq.size() - 1 && tardiness - resourceCost == target)) {
                    result[i + 1] = d;
                    searchStart = d + test.getDur();
                    break;
                }

            }
        }

        return result;
    }
}
