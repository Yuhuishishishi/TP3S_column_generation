package branchandprice;

import algorithm.Column;
import algorithm.ColumnGeneration;
import algorithm.pricer.EnumPricer;
import algorithm.pricer.Pricer;
import data.DataInstance;
import utils.Global;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by yuhuishi on 12/20/2016.
 * University of Michigan
 * Academic use only
 */
public class EnumPricerWithBranchConstraints implements Pricer {

    private static EnumPricerWithBranchConstraints pricer;

    private final List<Column> colPool;
    private List<BranchConstraint> constraintList;
    private List<Column> colsSatisfyBranchConstraints;
    private double reducedCost;

    private EnumPricerWithBranchConstraints() {
        colPool = ColumnGeneration.enumInitCol(Global.MAX_HITS);
        constraintList = new ArrayList<>();
    }

    public static EnumPricerWithBranchConstraints getPricer(List<BranchConstraint> branchConstraints) {
        if (null == pricer)
            pricer = new EnumPricerWithBranchConstraints();

        pricer.constraintList.clear();
        pricer.constraintList.addAll(branchConstraints);

        pricer.colsSatisfyBranchConstraints = pricer.colPool.stream().filter(pricer::isColSatisfyAllBranchConstraint)
            .collect(Collectors.toList());
        assert pricer.colsSatisfyBranchConstraints.size()>0;
        return pricer;
    }

    private boolean isColSatisfyAllBranchConstraint(Column col) {
        for (BranchConstraint constraint : constraintList) {
            if (constraint.isColFixToZero(col))
                return false; // column not satisfy the branch constraint
        }
        return true;
    }


    @Override
    public List<Column> price(Map<Integer, Double> testDual, Map<Integer, Double> vehicleDual) {
        // recursively return columns
        List<Column> sortedCol = colsSatisfyBranchConstraints.stream().sorted((c1, c2) -> {
            if (EnumPricer.reducedCost(c1, testDual, vehicleDual) < EnumPricer.reducedCost(c2, testDual, vehicleDual))
                return -1;
            else if (EnumPricer.reducedCost(c1, testDual, vehicleDual) > EnumPricer.reducedCost(c2, testDual, vehicleDual))
                return 1;
            else
                return 0;
        }).collect(Collectors.toList());

        // heuristics
        Map<Integer, Boolean> isTestCovered = new HashMap<>();
        Map<Integer, Integer> isVehicleSetUsedUp = new HashMap<>();
        // initialization
        DataInstance.getInstance().getTidList().forEach(tid->isTestCovered.put(tid, false));
        DataInstance.getInstance().getVehicleReleaseList()
                .forEach(vrelease->isVehicleSetUsedUp.put(vrelease,
                        DataInstance.getInstance().numVehiclesByRelease(vrelease)));

        // most negative reduced cost
        reducedCost = EnumPricer.reducedCost(sortedCol.get(0), testDual, vehicleDual);

        List<Column>  result = new ArrayList<>();
        // recursive call
        for (Column col : sortedCol) {
            if (EnumPricer.reducedCost(col, testDual, vehicleDual) > -0.001)
                break;

            // if tests are covered, continue
            // if vehicle is used up, continue
            if (col.getSeq().stream().anyMatch(isTestCovered::get)
                    || isVehicleSetUsedUp.get(col.getRelease())==0)
                continue;

            // mark all test as covered
            col.getSeq().forEach(tid->isTestCovered.put(tid, true));
            // deduct vehicle count
            int vehicleCount = isVehicleSetUsedUp.get(col.getRelease());
            isVehicleSetUsedUp.put(col.getRelease(), --vehicleCount);

            result.add(col);
        }

        return result;
    }



    @Override
    public double getReducedCost() {
        return reducedCost;
    }

    @Override
    public void end() {
        // do nothing
    }
}
