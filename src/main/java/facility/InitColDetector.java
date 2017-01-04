package facility;

import algorithm.Column;
import data.DataInstance;
import data.TestRequest;
import gurobi.*;
import ilog.concert.*;
import ilog.cp.IloCP;
import utils.Global;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by yuhuishi on 10/28/2016.
 * University of Michigan
 * Academic use only
 */
public class InitColDetector implements WarmupAlgorithm{
    private final String instID;
    private List<Column> allColList;

    public InitColDetector(List<Column> allColList) {
        instID = DataInstance.getInstance().getInstID();
        this.allColList = new ArrayList<>();
        this.allColList.addAll(allColList);
    }

    public InitColDetector(String instID, List<Column> allColList) {
        this.instID = instID;
        this.allColList = new ArrayList<>();
        this.allColList.addAll(allColList);
    }

    @Override
    public List<ColumnWithTiming> getInitSol() {

        try {
            GRBEnv env = new GRBEnv("initial col detector");
            GRBModel model;
            model = new GRBModel(env);


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
            for (Column col : allColList) {
                GRBVar var = addOneCol(model, col, testCoverConstrs, vehicleCapConstrs, GRB.BINARY);
                varMap.put(col, var);
            }


            // solve the model
            model.optimize();
            if (model.get(GRB.IntAttr.Status) == GRB.OPTIMAL) {
                // get used cols
                List<Column> usedCol = new ArrayList<>();
                for (Column col : allColList) {
                    GRBVar var = varMap.get(col);
                    if (var.get(GRB.DoubleAttr.X) > 0.5)
                        usedCol.add(col);
                }

                // post process
                return postProcess(usedCol);

            }
        } catch (GRBException | IloException e) {
            e.printStackTrace();
        }

        return new ArrayList<>();

    }

    // constraint programming to smooth facility usage
    private List<ColumnWithTiming> postProcess(List<Column> usedCol) throws IloException {
        IloCP model = new IloCP();

        Map<Integer, IloIntervalVar> prep, tat, analysis, dur;
        prep = new HashMap<>();
        tat = new HashMap<>();
        analysis = new HashMap<>();
        dur = new HashMap<>();

        DataInstance.getInstance(instID).getTestArr().forEach(testRequest -> {
            try {
                int tid = testRequest.getTid();
                prep.put(tid, model.intervalVar(testRequest.getPrep()));
                tat.put(tid, model.intervalVar(testRequest.getTat()));
                analysis.put(tid, model.intervalVar(testRequest.getAnalysis()));
                dur.put(tid, model.intervalVar());
            } catch (IloException e) {
                e.printStackTrace();
            }
        });


        // global resource constraints
        IloCumulFunctionExpr resource = model.cumulFunctionExpr();
        List<IloNumExpr> tardinessList = new ArrayList<>();

        for (Column col : usedCol) {
            List<Integer> seq = col.getSeq();
            for (int i = 0; i < seq.size(); i++) {
                int tid = seq.get(i);
                TestRequest test = DataInstance.getInstance(instID).getTestById(tid);

                // span constraints, relations between different stages
                IloIntervalVar durVar = dur.get(tid);
                IloIntervalVar prepVar = prep.get(tid);
                IloIntervalVar tatVar = tat.get(tid);
                IloIntervalVar analysisVar = analysis.get(tid);

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
                    model.add(model.endBeforeStart(dur.get(prevTid),
                            durVar));
                }

                // resource pulse
                resource.add(model.pulse(tatVar, 1));

                // tardiness
                tardinessList.add(model.max(0,
                        model.diff(test.getDeadline(), model.endOf(durVar))));
            }
        }

        model.add(model.le(resource, Global.FACILITY_CAP));

        // objective
        IloNumExpr obj = model.numExpr();
        for (IloNumExpr tardiness : tardinessList)
            obj = model.sum(obj, tardiness);
//        model.addMinimize(obj);

        // solve the model
        if (model.solve()) {
            // parse the solution
            List<ColumnWithTiming> result = new ArrayList<>();
            for (Column col : usedCol) {
                // get the start time
                Map<Integer, Integer> startTime = new HashMap<>();
                col.getSeq().forEach(tid -> {
                    IloIntervalVar durVar = dur.get(tid);
                    startTime.put(tid,
                            model.getStart(durVar));
                });

                // convert to column with timing
                ColumnWithTiming colWithTime = new ColumnWithTiming(instID, col.getSeq(), col.getRelease(),
                        startTime);
                result.add(colWithTime);
            }
            model.end();
            return result;

        } else {
            // this should not happen
            System.err.println("Post processing is infeasible");
            model.end();
            return usedCol.stream().map(column -> new ColumnWithTiming(column.getSeq(), column.getRelease()))
                    .collect(Collectors.toList());
        }



    }

    private GRBVar addOneCol(GRBModel model, Column col,
                             Map<Integer, GRBConstr> testCoverConstrs,
                             Map<Integer, GRBConstr> vehicleCapConstrs, char vtype) throws GRBException {
        GRBColumn grbColumn = new GRBColumn();
        GRBConstr vConstr = vehicleCapConstrs.get(col.getRelease());
        grbColumn.addTerm(1, vConstr);

        col.getSeq().forEach(tid -> grbColumn.addTerm(1, testCoverConstrs.get(tid)));
        GRBVar v;
        if (vtype == GRB.BINARY)
            v = model.addVar(0, 1, col.getCost() + Global.VEHICLE_COST,
                    GRB.BINARY, grbColumn, "use col " + col.getSeq());
        else
            v = model.addVar(0, GRB.INFINITY, col.getCost() + Global.VEHICLE_COST,
                    GRB.CONTINUOUS, grbColumn, "use col " + col.getSeq());

        return v;
    }



}
