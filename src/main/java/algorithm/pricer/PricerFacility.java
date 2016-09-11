package algorithm.pricer;

import algorithm.Column;

import java.util.List;
import java.util.Map;

/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
public interface PricerFacility {
    List<Column> price(Map<Integer, Double> testDual,
                       Map<Integer, Double> vehicleDual,
                       Map<Integer, Double> dayDual);

}
