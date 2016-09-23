package algorithm.pricer;

import algorithm.Column;
import algorithm.ColumnGeneration;
import utils.Global;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        int minIdx = IntStream.range(0, colList.size())
                .reduce((i,j) ->
                        reducedCost(colList.get(i), testDual, vehicleDual) <
                reducedCost(colList.get(j), testDual, vehicleDual) ? i : j).getAsInt();
        this.reducedCost = reducedCost(colList.get(minIdx), testDual, vehicleDual);

        if (this.reducedCost < -0.001) {
            return Collections.singletonList(colList.get(minIdx));
        } else {
            return new ArrayList<>();
        }
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
