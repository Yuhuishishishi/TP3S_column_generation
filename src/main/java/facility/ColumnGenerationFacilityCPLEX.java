package facility;

import algorithm.Algorithm;
import algorithm.Column;
import algorithm.pricer.CPOPricerFacility;
import algorithm.pricer.PricerFacility;
import data.DataInstance;
import ilog.concert.*;
import ilog.cplex.IloCplex;
import utils.Global;

import java.util.*;
import java.util.stream.Collectors;

import static algorithm.ColumnGeneration.enumInitCol;

/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
@SuppressWarnings("ALL")
public class ColumnGenerationFacilityCPLEX implements Algorithm {

    private Map<ColumnWithTiming, IloNumVar> varMap;
    private Map<Integer, IloRange> resourceCapConstrs;
    private Map<Integer, IloRange> testCoverConstrs;
    private Map<Integer, IloRange> vehicleCapConstrs;
    private IloObjective obj;

    public ColumnGenerationFacilityCPLEX() {
        varMap = new HashMap<>();
        resourceCapConstrs = new HashMap<>();
        testCoverConstrs = new HashMap<>();
        vehicleCapConstrs = new HashMap<>();
    }


    @Override
    public void solve() {
        // enumerate initial set of columns
        List<Column> normalColList = enumInitCol(Global.MAX_HITS);
        // transfer to subclass
        List<ColumnWithTiming> colList = normalColList.stream()
                .map(col -> new ColumnWithTiming(col.getSeq(), col.getRelease()))
                .collect(Collectors.toList());

        // create multiple versions of columns
//        List<ColumnWithTiming> additonalColumns = new ArrayList<>();
//        colList.forEach(c->{
//            List<ColumnWithTiming> moreCols = CPOPricerFacility.createMultipleVersion(c);
//            additonalColumns.addAll(moreCols);
//        });
//        colList.addAll(additonalColumns);
//        System.out.println(additonalColumns.size() + " additional cols generated.");

        Set<ColumnWithTiming> uniqColSet = new HashSet<>(colList);
//        colList.clear();
//        colList.addAll(uniqColSet);
        assert colList.size()==uniqColSet.size();
        System.out.println("unique col size: " + uniqColSet.size());

        try {
            IloCplex model = buildModel(colList, true);
            // suppress the log
            model.setOut(null);


            // ================================= Column Generation Loop ================================================

            final int maxIter = 10000;
            int iterTimes = 0;

            PricerFacility pricer = new CPOPricerFacility();
            while (iterTimes++ < maxIter) {
                if (!model.solve()) {
                    System.err.println("Master problem infeasible");
                    break;
                }

                // get dual information
                Map<Integer, Double> testDual = new HashMap<>();
                Map<Integer, Double> vehicleDual = new HashMap<>();
                Map<Integer, Double> dayDual = new HashMap<>();
                for (int tid : testCoverConstrs.keySet()) {
                    testDual.put(tid,
                            model.getDual(testCoverConstrs.get(tid)));
                }
                for (int release : vehicleCapConstrs.keySet()) {
                    vehicleDual.put(release,
                            model.getDual(vehicleCapConstrs.get(release)));
                }
                for (int d : resourceCapConstrs.keySet()) {
                    dayDual.put(d,
                            model.getDual(resourceCapConstrs.get(d)));
                }

                List<ColumnWithTiming> candidates = pricer.price(testDual, vehicleDual, dayDual);
                System.out.printf("Iteration: %d, Master obj: %.3f, pricing obj: %.3f, # cols: %d, ", iterTimes,
                        model.getObjValue(),
                        pricer.getReducedCost(),
                        candidates.size());
                if (candidates.size()==0)
                    break;
                // add the column to master problem
                int realColNum = 0;
                for (ColumnWithTiming col : candidates) {
                    if (uniqColSet.add(col)) {
                        addOneCol(model, col, false);
                        colList.add(col);
                        realColNum++;
                    }
                }
                System.out.print("# col added: " + realColNum + "\n");
            }

            pricer.end();
            model.end();

            // solve the integer version
            IloCplex ipModel = buildModel(colList, false);

            if (ipModel.solve()) {
                List<ColumnWithTiming> usedCols = parseSol(ipModel);
                System.out.println("max tardiness: " + usedCols.stream().mapToDouble(ColumnWithTiming::getCost).sum());
                double tardiness = ipModel.getObjValue() - usedCols.size()*Global.VEHICLE_COST;
                System.out.println("Used vehicles: " + usedCols.size());
                System.out.println("Tardiness: " + tardiness);
                System.out.println("Obj val: " + ipModel.getObjValue());
            }
        } catch (IloException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getTimeTillNow() {
        return 0;
    }


    private List<ColumnWithTiming> parseSol(IloCplex model) throws IloException {
        List<ColumnWithTiming> result = new ArrayList<>();
        for (Map.Entry<ColumnWithTiming, IloNumVar> entry : this.varMap.entrySet()) {
            if (model.getValue(entry.getValue()) > 0.5)
                result.add(entry.getKey());
        }
        return result;
    }

    private IloCplex buildModel(List<ColumnWithTiming> colList, boolean isRelax) throws IloException {
        IloCplex model = new IloCplex();

        final int horizonStart = DataInstance.getInstance().getHorizonStart();
        final int horizonEnd = DataInstance.getInstance().getHorizonEnd();

        // build constraints first
        obj = model.addMinimize();

        // test cover constraints
        for (int tid : DataInstance.getInstance().getTidList()) {
            testCoverConstrs.put(tid,
                    model.addRange(1, Double.MAX_VALUE));
        }

        // vehicle capacity constraints
        for (int release : DataInstance.getInstance().getVehicleReleaseList()) {
            vehicleCapConstrs.put(release,
                    model.addRange(-Double.MAX_VALUE,
                            DataInstance.getInstance().numVehiclesByRelease(release)));
        }

        // day resource capacity constraints
        for (int d = horizonStart; d < horizonEnd; d++) {
            resourceCapConstrs.put(d,
                    model.addRange(-Double.MAX_VALUE, Global.FACILITY_CAP));
        }

        // add variables

        for (ColumnWithTiming col : colList) {
            addOneCol(model, col, !isRelax);
        }

        return model;
    }

    private void addOneCol(IloCplex model, ColumnWithTiming col, boolean isInt) throws IloException {
        IloColumn objCol = model.column(obj, Global.VEHICLE_COST+col.getCost());
        IloColumn testCol = col.getSeq().stream().map(testCoverConstrs::get)
                .map(rng -> {
                    try {
                        return model.column(rng, 1.0);
                    } catch (IloException e) {
                        e.printStackTrace();
                        return null;
                    }
                }).reduce(IloColumn::and).get();

        IloColumn dayCol;
        try {
            dayCol = col.daysHasCrash().stream().map(resourceCapConstrs::get)
                    .map(rng -> {
                        try {
                            return model.column(rng, 1.0);
                        } catch (IloException e) {
                            e.printStackTrace();
                            return null;
                        }
                    }).reduce(IloColumn::and).get();
        } catch (NoSuchElementException ex) {
            dayCol= null;
        }
        IloColumn vehicleCol = model.column(vehicleCapConstrs.get(col.getRelease()), 1.0);
        IloColumn finalCol = objCol.and(testCol.and(vehicleCol));
        if (null!=dayCol)
            finalCol = finalCol.and(dayCol);

        IloNumVar var;
        if (isInt)
            var = model.boolVar(finalCol);
        else
            var = model.numVar(finalCol, 0, Double.MAX_VALUE);
        varMap.put(col, var);
    }


}
