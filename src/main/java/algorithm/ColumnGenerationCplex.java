package algorithm;



import algorithm.pricer.CPOPricer;
import algorithm.pricer.Pricer;
import data.DataInstance;
import ilog.concert.*;
import ilog.cplex.IloCplex;
import utils.Global;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
public class ColumnGenerationCplex implements Algorithm{


    private final Map<Column, IloNumVar> varMap;
    private final Map<Integer, IloRange> testCoverConstrs;
    private final Map<Integer, IloRange> vehicleCapConstrs;
    private IloObjective obj;

    public ColumnGenerationCplex() {
        testCoverConstrs = new HashMap<>();
        vehicleCapConstrs = new HashMap<>();
        varMap = new HashMap<>();
        obj = null;
    }



    @Override
    public void solve() {
        List<Column> colList = ColumnGeneration.enumInitCol(2);

        try {
            IloCplex model = buildModel(colList, true);
            model.setOut(null);

            final int maxIter = 10000;
            int iterTimes = 0;
            Pricer pricer = new CPOPricer();

            while (iterTimes++ < maxIter) {
                if (!model.solve())
                    System.err.println("Master problem infeasible");
                Map<Integer, Double> testDual = new HashMap<>();
                Map<Integer, Double> vehicleDual = new HashMap<>();
                for (int tid : testCoverConstrs.keySet()) {
                    testDual.put(tid,
                            model.getDual(testCoverConstrs.get(tid)));
                }
                for (int release : vehicleCapConstrs.keySet()) {
                    vehicleDual.put(release,
                            model.getDual(vehicleCapConstrs.get(release)));
                }

                List<Column> candidates = pricer.price(testDual, vehicleDual);
                System.out.printf("Iteration %d, Master obj: %.3f, pricing obj: %.3f\n",
                        iterTimes, model.getObjValue(), pricer.getReducedCost());
                if (candidates.size()==0)
                    break;

                for (Column col : candidates) {
                    addOneCol(model, col, false);
                    colList.add(col);
                }
            }

            pricer.end();
            model.end();
            // rebuild the integer version
            IloCplex ipModel = buildModel(colList, false);
            if (ipModel.solve())
                printStats(ipModel, colList);

            ipModel.end();

        } catch (IloException e) {
            e.printStackTrace();
        }



    }

    @Override
    public long getTimeTillNow() {
        return 0;
    }

    public void solveFull() {
        List<Column> allCols = ColumnGeneration.enumInitCol(Global.MAX_HITS);
        try {
            IloCplex model = buildModel(allCols, false);
            if (model.solve())
                printStats(model, allCols);
        } catch (IloException e) {
            e.printStackTrace();
        }


    }

    private void printStats(IloCplex model, List<Column> colList) throws IloException {

            List<Column> usedCols = parseSol(model);
            double tardiness =
                    model.getObjValue() - usedCols.size()*Global.VEHICLE_COST;
            System.out.println("Total tardiness: " + tardiness);
            System.out.println("Used vehicles: " + usedCols.size());
            System.out.println("Obj val: " + model.getObjValue());
            System.out.println("max activities on day " + ColumnGeneration.countMaxActivityPerDay(usedCols));


    }

    private IloCplex buildModel(List<Column> colList, boolean isRelax) throws IloException {
        IloCplex model = new IloCplex();

        // ranges
        for (int tid : DataInstance.getInstance().getTidList()) {
            testCoverConstrs.put(tid,
                    model.addRange(1, Double.MAX_VALUE));
        }

        for (int release : DataInstance.getInstance().getVehicleReleaseList()) {
            vehicleCapConstrs.put(release,
                    model.addRange(-Double.MAX_VALUE,
                            DataInstance.getInstance().numVehiclesByRelease(release)));
        }

        // objective
        obj = model.addMinimize();

        // add variables
        for (Column col : colList) {
            addOneCol(model, col, !isRelax);
        }

        return model;

    }

    private void addOneCol(IloCplex model, Column col, boolean isInt) throws IloException {
        IloColumn objCol = model.column(obj, Global.VEHICLE_COST+col.getCost());
        List<IloColumn> testColList = col.getSeq().stream().map(testCoverConstrs::get)
                .map(rng -> {
                    try {
                        return model.column(rng, 1.0);
                    } catch (IloException e) {
                        e.printStackTrace();
                        return null;
                    }
                }).collect(Collectors.toList());
        IloColumn vehicleCol = model.column(vehicleCapConstrs.get(col.getRelease()),
                1.0);

        IloColumn testCol = testColList.stream().reduce(IloColumn::and).get();
        IloColumn finalCol = objCol.and(testCol.and(vehicleCol));

        IloNumVar var;
        if (isInt)
            var = model.boolVar(finalCol);
        else
            var = model.numVar(finalCol, 0, Double.MAX_VALUE);
        varMap.put(col, var);
    }


    private List<Column> parseSol(IloCplex model) {
        return varMap.keySet().stream().filter(col -> {
            try {
                return model.getValue(varMap.get(col)) > 0.5;
            } catch (IloException e) {
                e.printStackTrace();
                return false;
            }
        }).collect(Collectors.toList());

    }
}
