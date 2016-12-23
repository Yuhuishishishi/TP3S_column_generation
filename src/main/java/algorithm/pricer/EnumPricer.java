package algorithm.pricer;

import algorithm.Column;
import algorithm.ColumnGeneration;
import data.DataInstance;
import utils.Global;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
public class EnumPricer implements Pricer {
    private final List<Column> colList;
    private double reducedCost;

    public EnumPricer() {
        colList = ColumnGeneration.enumInitCol(Global.MAX_HITS);
    }

    @Override
    public List<Column> price(Map<Integer, Double> testDual,
                              Map<Integer, Double> vehicleDual) {
//        int minIdx = IntStream.range(0, colList.size())
//                .reduce((i,j) ->
//                        reducedCost(colList.get(i), testDual, vehicleDual) <
//                reducedCost(colList.get(j), testDual, vehicleDual) ? i : j).getAsInt();
//        this.reducedCost = reducedCost(colList.get(minIdx), testDual, vehicleDual);

        List<Column> result = new ArrayList<>();
        List<Column> sortedColList = colList.stream()
                .sorted((c1,c2)->{
                    if (reducedCost(c1, testDual, vehicleDual) < reducedCost(c2, testDual, vehicleDual))
                        return -1;
                    else if (reducedCost(c1, testDual, vehicleDual) > reducedCost(c2, testDual, vehicleDual))
                        return 1;
                    else
                        return 0;
                }).collect(Collectors.toList());

        Map<Integer, Boolean> testCovered = new HashMap<>();
        Map<Integer, Integer> vehicleCapacity = new HashMap<>();
        DataInstance.getInstance().getTidList().forEach(tid->testCovered.put(tid, false));
        DataInstance.getInstance().getVehicleReleaseList().forEach(release->vehicleCapacity.put(release,
                DataInstance.getInstance().numVehiclesByRelease(release)));
        this.reducedCost = reducedCost(sortedColList.get(0), testDual, vehicleDual);

        for (Column col : sortedColList) {
            if (reducedCost(col, testDual, vehicleDual) > -0.001)
                break;

            if (col.getSeq().stream().anyMatch(testCovered::get))
                continue;

            // if vehicle capacity is low
            if (vehicleCapacity.get(col.getRelease()) <= 0)
                continue;

            result.add(col);
            col.getSeq().forEach(tid->testCovered.put(tid, true));
            int cap = vehicleCapacity.get(col.getRelease());
            vehicleCapacity.put(col.getRelease(), --cap);

            if (testCovered.values().stream().allMatch(covered-> covered))
                break;
        }

        return result;
    }

    @Override
    public double getReducedCost() {
        return this.reducedCost;
    }

    @Override
    public void end() {

    }

    public static double reducedCost(Column col, Map<Integer, Double> testDual,
                               Map<Integer, Double> vehicleDual) {
        return Global.VEHICLE_COST + col.getCost()
                - col.getSeq().stream().mapToDouble(testDual::get).sum()
                - vehicleDual.get(col.getRelease());
    }
}
