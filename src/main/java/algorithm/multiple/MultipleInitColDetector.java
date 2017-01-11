package algorithm.multiple;

import algorithm.Column;
import algorithm.ColumnGeneration;
import data.DataInstance;
import data.TestRequest;
import facility.ColumnWithTiming;
import facility.WarmupAlgorithm;
import gurobi.*;
import ilog.concert.IloCumulFunctionExpr;
import ilog.concert.IloException;
import ilog.concert.IloIntervalVar;
import ilog.concert.IloNumExpr;
import ilog.cp.IloCP;
import ilog.cp.IloSearchPhase;
import utils.Global;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by yuhuishi on 1/3/2017.
 * University of Michigan
 * Academic use only
 */
public class MultipleInitColDetector implements WarmupAlgorithm {

    private Map<String, List<Column>> solveSetPartitioning() throws GRBException {
        Map<String, List<Column>> result = new HashMap<>();


        // solve two individual set-partitioning formulation
        GRBEnv env = new GRBEnv();

        for (String instID : DataInstance.getInstIds()) {
            // enumerate all columns
            List<Column> columnList = ColumnGeneration.enumInitCol(instID, 3);

            GRBModel model = new GRBModel(env);

            Map<Integer, GRBConstr> testCoverConstrs = new HashMap<>();
            Map<Integer, GRBConstr> vehicleCapConstrs = new HashMap<>();
            Map<Column, GRBVar> varMap = new HashMap<>();

            // test cover constraints
            for (int tid : DataInstance.getInstance(instID).getTidList()) {
                testCoverConstrs.put(tid, model.addConstr(
                        new GRBLinExpr(), GRB.GREATER_EQUAL, 1.0, "cover test " + tid));
            }

            // vehicle capacity constraints
            for (int release : DataInstance.getInstance(instID).getVehicleReleaseList()) {
                vehicleCapConstrs.put(release, model.addConstr(
                        new GRBLinExpr(), GRB.LESS_EQUAL, DataInstance.getInstance(instID).numVehiclesByRelease(release),
                        "vehicle capacity " + release
                ));
            }

            model.update();

            // add variables
            for (Column col : columnList) {
                GRBVar var = addOneCol(model, col, testCoverConstrs, vehicleCapConstrs);
                varMap.put(col, var);
            }

//            model.getEnv().set(GRB.IntParam.OutputFlag, 0);
            model.getEnv().set(GRB.DoubleParam.MIPGap, 0.01);
            model.getEnv().set(GRB.DoubleParam.TimeLimit, 300);

            // solve the model
            model.optimize();
            List<Column> usedCol = new ArrayList<>();
            int status = model.get(GRB.IntAttr.Status);
            if (status == GRB.OPTIMAL
                    || status==GRB.TIME_LIMIT) {
                // get used cols
                for (Column col : columnList) {
                    GRBVar var = varMap.get(col);
                    if (var.get(GRB.DoubleAttr.X) > 0.5)
                        usedCol.add(col);
                }
            }

            result.put(instID, usedCol);

            model.dispose();
        }

        env.dispose();
        return result;

    }

    private Map<String, List<ColumnWithTiming>> solveCP(Map<String, List<Column>> usedCols) throws IloException {
        IloCP model = new IloCP();

        Map<String, Map<Integer, IloIntervalVar>> prep, tat, analysis, dur;
        prep = new HashMap<>();
        tat = new HashMap<>();
        analysis = new HashMap<>();
        dur = new HashMap<>();
        DataInstance.getInstIds().forEach(instID->{
            prep.put(instID, new HashMap<>());
            tat.put(instID, new HashMap<>());
            analysis.put(instID, new HashMap<>());
            dur.put(instID, new HashMap<>());

            // create variables
            DataInstance.getInstance(instID).getTestArr().forEach(testRequest -> {
                try {
                    int tid = testRequest.getTid();
                    prep.get(instID).put(tid, model.intervalVar(testRequest.getPrep()));
                    tat.get(instID).put(tid, model.intervalVar(testRequest.getTat()));
                    analysis.get(instID).put(tid, model.intervalVar(testRequest.getAnalysis()));
                    dur.get(instID).put(tid, model.intervalVar());
                } catch (IloException e) {
                    e.printStackTrace();
                }
            });
        });

        // global resource constraints
        IloCumulFunctionExpr resource = model.cumulFunctionExpr();
        List<IloNumExpr> tardinessList = new ArrayList<>();

        for (String instID : DataInstance.getInstIds()) {
            List<Column> colList = usedCols.get(instID);

            for (Column col : colList) {
                List<Integer> seq = col.getSeq();
                for (int i = 0; i < seq.size(); i++) {
                    int tid = seq.get(i);
                    TestRequest test = DataInstance.getInstance(instID).getTestById(tid);

                    // span constraints, relations between different stages
                    IloIntervalVar durVar = dur.get(instID).get(tid);
                    IloIntervalVar prepVar = prep.get(instID).get(tid);
                    IloIntervalVar tatVar = tat.get(instID).get(tid);
                    IloIntervalVar analysisVar = analysis.get(instID).get(tid);

                    // span constraints
                    model.add(model.span(durVar, new IloIntervalVar[]{prepVar, tatVar, analysisVar}));

                    // prep -> tat -> analysis
                    model.add(model.endAtStart(prepVar, tatVar));
                    model.add(model.endAtStart(tatVar, analysisVar));

                    // time window constraints
                    tatVar.setStartMin(test.getRelease());

                    if (i == 0) {
                        int vehicleRelease = col.getRelease();
                        durVar.setStartMin(vehicleRelease);
                    } else {
                        // start after the previous one has completed
                        int prevTid = col.getSeq().get(i - 1);
                        model.add(model.endBeforeStart(dur.get(instID).get(prevTid),
                                durVar));
                    }

                    // resource pulse
                    resource.add(model.pulse(tatVar, 1));

                    // tardiness
                    tardinessList.add(model.max(0,
                            model.diff(test.getDeadline(), model.endOf(durVar))));
                }
            }
        }

        model.add(model.le(resource, Global.FACILITY_CAP));

        // objective
        IloNumExpr obj = model.numExpr();
        for (IloNumExpr tardiness : tardinessList)
            obj = model.sum(obj, tardiness);
//        model.addMinimize(obj);

        // search for tat first

        List<IloIntervalVar> tatVars = new ArrayList<>();
        tat.values().forEach(map->tatVars.addAll(map.values()));
        IloSearchPhase selectTat = model.searchPhase(tatVars.toArray(new IloIntervalVar[tatVars.size()]));
        model.setSearchPhases(selectTat);

        Map<String, List<ColumnWithTiming>> result = new HashMap<>();
        if (model.solve()) {
            for (String instID : DataInstance.getInstIds()) {
                List<Column> colList = usedCols.get(instID);
                List<ColumnWithTiming> colsToAdd = new ArrayList<>();
                for (Column col : colList) {
                    // get the start time
                    Map<Integer, Integer> startTime = new HashMap<>();
                    col.getSeq().forEach(tid -> {
                        IloIntervalVar durVar = dur.get(instID).get(tid);
                        startTime.put(tid,
                                model.getStart(durVar));
                    });

                    // convert to column with timing
                    ColumnWithTiming colWithTime = new ColumnWithTiming(instID, col.getSeq(), col.getRelease(),
                            startTime);
                    colsToAdd.add(colWithTime);

                }
                result.put(instID, colsToAdd);
            }
        } else {
            return null;
        }

        model.end();
        return result;
    }


    @Override
    public List<ColumnWithTiming> getInitSol() {
        // solve the integer solution by full enumeration algorithm
        try {
            Map<String, List<Column>> seqInfo = solveSetPartitioning();
            Map<String, List<ColumnWithTiming>> additionalCols = solveCP(seqInfo);
            List<ColumnWithTiming> result = new ArrayList<>();
            assert additionalCols != null;
            additionalCols.values().forEach(result::addAll);
            return result;
        } catch (GRBException | IloException e) {
            e.printStackTrace();
        }

        // post processing using CP

        return new ArrayList<>();
    }

    private GRBVar addOneCol(GRBModel model, Column col,
                             Map<Integer, GRBConstr> testCoverConstrs,
                             Map<Integer, GRBConstr> vehicleCapConstrs) throws GRBException {
        GRBColumn grbColumn = new GRBColumn();
        GRBConstr vConstr = vehicleCapConstrs.get(col.getRelease());
        grbColumn.addTerm(1, vConstr);

        col.getSeq().forEach(tid -> grbColumn.addTerm(1, testCoverConstrs.get(tid)));
        GRBVar v;
            v = model.addVar(0, GRB.INFINITY, col.getCost() + Global.VEHICLE_COST,
                    GRB.BINARY, grbColumn, "use col " + col.getSeq());

        return v;
    }
}
