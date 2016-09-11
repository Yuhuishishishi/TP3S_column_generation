package algorithm.pricer;

import algorithm.Column;

import java.util.*;

import algorithm.ColumnWithTiming;
import data.DataInstance;
import data.TestRequest;
import ilog.concert.*;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloRange;
import ilog.concert.cppimpl.*;
import ilog.concert.cppimpl.IloConstraint;
import ilog.concert.cppimpl.IloIntExpr;
import ilog.cp.*;
import utils.Global;

/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
public class CPPricerFacility implements PricerFacility {
    Pricer firstStagePricer;
    IloCP cpPricer;
    private IloIntVar selectVehicle;
    private IloIntVar[] startTimeAtPosition;
    private IloIntVar[] testAtPosition;
    private ilog.concert.IloConstraint reducedCostConstr;

    private double mostNegReducedCost;

    public CPPricerFacility() {
        firstStagePricer = new EnumPricer();

    }

    @Override
    public List<Column> price(Map<Integer, Double> testDual, Map<Integer, Double> vehicleDual,
                              Map<Integer,Double> dayDual) {
        // 1st stage, ignore facility, relaxation
        List<Column> candidate = firstStagePricer.price(testDual, vehicleDual);
        if (candidate.size() == 0) {
            // no solution to the relaxation
            return candidate;
        } else {
            // proceed to more accurate model
            ColumnWithTiming candidateWithTiming =
                    new ColumnWithTiming(candidate.get(0).getSeq(), candidate.get(0).getRelease());
            if (reducedCost(candidateWithTiming, testDual, vehicleDual, dayDual) < 0.001)
                return Collections.singletonList(candidateWithTiming);

            // constraint programming model
            if (cpPricer==null)
                try {
                    cpPricer = buildModel();

                    // modify the reduced cost obj

                } catch (IloException e) {
                    e.printStackTrace();
                }


        }

        return null;
    }

    private ColumnWithTiming parseCol(IloCP model) throws IloException {

        int[] tidArr = DataInstance.getInstance().getTestArr().stream().mapToInt(TestRequest::getTid)
                .toArray();
        List<Integer> release = DataInstance.getInstance().getVehicleReleaseList();

        final int numTests = tidArr.length;
        final int numVehicles = release.size();
        int[] vehicleRelease = new int[numVehicles];
        for (int i=0; i<numVehicles; i++)
            vehicleRelease[i] = release.get(i);

        List<Integer> seq = new ArrayList<>();
        Map<Integer, Integer> startTimeMap = new HashMap<>();

        for (int p = 0; p < Global.MAX_HITS; p++) {
            int testIdx = (int) Math.round(model.getValue(testAtPosition[p]));
            if (testIdx != numTests) {
                seq.add(tidArr[testIdx]);
                int startDay = (int) Math.round(model.getValue(startTimeAtPosition[p]));
                startTimeMap.put(tidArr[testIdx], startDay);
            } else
                break;
        }

        int colReleaseIdx = (int) Math.round(model.getValue(selectVehicle));
        int colRelease = vehicleRelease[colReleaseIdx];

        // construct the column
        ColumnWithTiming candidate =
                new ColumnWithTiming(seq, colRelease, startTimeMap);
        return candidate;
    }

    private double reducedCost(ColumnWithTiming col,
                               Map<Integer,Double> testDual,
                               Map<Integer,Double> vehicleDual,
                               Map<Integer,Double> dayDual) {
        // reduced cost with facility consideration
        double cost = EnumPricer.reducedCost(col, testDual, vehicleDual);
        cost -= col.daysHasCrash().stream().mapToDouble(dayDual::get).sum();
        return cost;
    }

    private IloCP buildModel() throws IloException {
        // does not include the obj < 0 constraints
        IloCP model = new IloCP();

        // parameters
        final int numTests = DataInstance.getInstance().getTestArr().size();
        final int numVehicles = DataInstance.getInstance().getVehicleReleaseList().size();

        int[] tidArr = DataInstance.getInstance().getTestArr().stream().mapToInt(TestRequest::getTid)
                .toArray();
        int[] realDurArr = DataInstance.getInstance().getTestArr().stream().mapToInt(TestRequest::getDur)
                .toArray();
        int[] durArr = new int[numTests+1];
        for (int i = 0; i < numTests+1; i++) {
            if (i < numTests)
                durArr[i] = realDurArr[i];
            else
                durArr[i] = 0;
        }
        int[] deadlineArr = DataInstance.getInstance().getTestArr().stream().mapToInt(TestRequest::getDeadline)
                .toArray();
        int[] vehicleRelease = new int[numVehicles];
        List<Integer> release = DataInstance.getInstance().getVehicleReleaseList();

        for (int i=0; i<numVehicles; i++)
            vehicleRelease[i] = release.get(i);

        // variables
        testAtPosition = model.intVarArray(Global.MAX_HITS, 0, numTests, "test at position");
        startTimeAtPosition = model.intVarArray(Global.MAX_HITS,
                DataInstance.getInstance().getHorizonStart(),
                DataInstance.getInstance().getHorizonEnd(), "start time at position");
        selectVehicle = model.intVar(0, numVehicles-1);

        // constraints

        // start after the selected vehicle is released
        model.add(model.ge(startTimeAtPosition[0], model.element(vehicleRelease, selectVehicle)));

        // each test appear at once in the column
        for (int t=0; t < numTests; t++) {
            model.add(model.le(model.count(testAtPosition, t),1));
        }

        // if a position is left blank, all following positions are blank, too
        for (int p=0; p < Global.MAX_HITS; p++) {
            for (int q=p+1; q < Global.MAX_HITS; q++) {
                model.add(model.ifThen(model.eq(testAtPosition[p], numTests),
                        model.eq(testAtPosition[q], numTests)));
            }
        }

        // start time between two positions
        for (int p=0; p < Global.MAX_HITS-1; p++) {
            model.add(model.ge(startTimeAtPosition[p+1],
                    model.sum(startTimeAtPosition[p], model.element(durArr, testAtPosition[p]))));
        }

        // start time if positions are left blank, symmetry breaking
        for (int p=1; p<Global.MAX_HITS; p++) {
            model.add(model.ifThen(model.eq(testAtPosition[p], numTests),
                    model.eq(startTimeAtPosition[p],
                            model.sum(startTimeAtPosition[p-1], model.element(durArr, testAtPosition[p-1])))));
        }

        // compatibility
//        for (int i=0; i < numTests; i++) {
//            int tid1 = tidArr[i];
//
//            for (int j = i + 1; j<numTests; j++) {
//                int tid2 = tidArr[j];
//                if (!DataInstance.getInstance().getRelation(tid1,tid2)
//                        && !DataInstance.getInstance().getRelation(tid2,tid1))
//                    model.add(model.le(
//                            model.sum(model.count(testAtPosition, i), model.count(testAtPosition, j)),
//                            1
//                    ));
//                else if (!DataInstance.getInstance().getRelation(tid1, tid2)) {
//                    for (int p=0; p<Global.MAX_HITS-1; p++) {
//                        model.add(model.ifThen(
//                                model.eq(testAtPosition[p], i),
//                                model.eq(model.count(Arrays.copyOfRange(testAtPosition, p+1, Global.MAX_HITS), j), 0)
//                        ));
//                    }
//                }
//                else if (!DataInstance.getInstance().getRelation(tid2, tid1)) {
//                    for (int p=0; p<Global.MAX_HITS-1; p++) {
//                        model.add(model.ifThen(
//                                model.eq(testAtPosition[p], j),
//                                model.eq(model.count(Arrays.copyOfRange(testAtPosition, p+1, Global.MAX_HITS), i), 0)
//                        ));
//                    }
//                }
//            }
//        }

        // empty reduced cost constraints
        reducedCostConstr = model.addLe(model.intExpr(),-.001);


        return model;
    }

    public double getMostNegReducedCost() {
        return mostNegReducedCost;
    }
}
