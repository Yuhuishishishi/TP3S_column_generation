package algorithm.pricer;

import algorithm.Column;
import data.DataInstance;
import data.TestRequest;
import org.jacop.constraints.Element;
import org.jacop.constraints.XlteqC;
import org.jacop.constraints.XlteqY;
import org.jacop.core.BoundDomain;
import org.jacop.core.IntDomain;
import org.jacop.core.IntVar;
import org.jacop.core.Store;
import utils.Global;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
        return null;
    }

    @Override
    public double getReducedCost() {
        return 0;
    }

    private Store buildModel() {
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
        final int[] tidArr = DataInstance.getInstance().getTidList().stream().mapToInt(Integer::intValue).toArray();
        final int[] testReleaseArr = Arrays.copyOf(DataInstance.getInstance().getTestArr()
                .stream().mapToInt(TestRequest::getRelease).toArray(), numTests+1);

        // variables
        testAtPosition = new IntVar[numSlots];
        startTimeAtPosition = new IntVar[numSlots];
        selectVehicle = new IntVar(model, "select vehicle", 0, numVehicles-1);
        // initialization
        for (int p = 0; p < testAtPosition.length; p++) {
            testAtPosition[p] = new IntVar(model, "test at position" + p, 0, numTests);
            startTimeAtPosition[p] = new IntVar(model, "start time at position" + p,
                    horizonStart, horizonEnd);
        }

        // auxiliary variables
        IntVar vehicleReleaseVar = new IntVar(model);
        model.impose(new Element(selectVehicle, releaseArr, vehicleReleaseVar, 1));

        IntVar[] durAtPosition = new IntVar[numSlots];
        IntVar[] testReleaseAtPosition = new IntVar[numSlots];
        for (int p = 0; p < numSlots; p++) {
            durAtPosition[p] = new IntVar(model);
            model.impose(new Element(testAtPosition[p], durArr, durAtPosition[p], 1));

            testReleaseAtPosition[p] = new IntVar(model);
            model.impose(new Element(testAtPosition[p], testReleaseArr, testReleaseAtPosition[p], 1));
        }


        // constraints
        // start after the selected vehicle is released
        model.impose(new XlteqY(testAtPosition[0], vehicleReleaseVar));





        return model;
    }
}
