package algorithm.pricer;

import facility.ColumnWithTiming;

import java.util.List;
import java.util.Map;

/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
public interface PricerFacility {
    List<ColumnWithTiming> price(Map<Integer, Double> testDual,
                                 Map<Integer, Double> vehicleDual,
                                 Map<Integer, Double> dayDual);
    double getReducedCost();
    void end();

}
